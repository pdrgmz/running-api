package com.running.api.service;

import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FitParserService {

    private static final double NO_DATA_ALTITUDE = -999.0;
    private static final double ELEVATION_NOISE_THRESHOLD = 0.01;
    private static final double MAX_VALID_SPEED = 20.0;
    private static final double HAVERSINE_MAX_DELTA = 100.0;
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final long FIT_EPOCH_OFFSET = 631065600L;

    private final AnalyticsService analyticsService;

    public Activity parse(InputStream inputStream) throws Exception {
        byte[] data = inputStream.readAllBytes();
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        int headerSize = buffer.get() & 0xFF;
        int protocolVersion = buffer.get() & 0xFF;
        int profileVersion = buffer.getShort() & 0xFFFF;
        int dataSize = buffer.getInt();
        byte[] dataType = new byte[4];
        buffer.get(dataType);

        int headerCrc = 0;
        if (headerSize >= 14) {
            headerCrc = buffer.getShort() & 0xFFFF;
        }

        buffer.position(headerSize);

        List<FitRecord> records = new ArrayList<>();
        int localTimestamp = 0;
        long globalStartTime = 0;

        int dataEnd = headerSize + dataSize;

        while (buffer.hasRemaining() && buffer.position() < dataEnd) {
            byte recordHeader = buffer.get();
            boolean isDefinition = (recordHeader & 0x40) != 0;
            int localMessageType = recordHeader & 0x0F;

            if (isDefinition) {
                int architecture = buffer.get() & 0xFF;
                int globalMessageNumber = buffer.getShort() & 0xFFFF;
                int numFields = buffer.get() & 0xFF;

                List<FieldDefinition> fields = new ArrayList<>();
                for (int i = 0; i < numFields; i++) {
                    int fieldDefNum = buffer.get() & 0xFF;
                    int size = buffer.get() & 0xFF;
                    int baseType = buffer.get() & 0xFF;
                    fields.add(new FieldDefinition(fieldDefNum, size, baseType));
                }

                records.add(new FitRecord(true, globalMessageNumber, localMessageType, fields, null));
            } else {
                FitRecord defRecord = findDefinition(records, localMessageType);
                if (defRecord != null) {
                    // Verificar que hay suficientes bytes para todos los campos
                    int totalBytesNeeded = 0;
                    for (FieldDefinition field : defRecord.fields) {
                        totalBytesNeeded += field.size;
                    }
                    if (buffer.remaining() < totalBytesNeeded) {
                        break; // No hay suficientes bytes, salir del bucle
                    }

                    Object[] values = new Object[defRecord.fields.size()];
                    for (int i = 0; i < defRecord.fields.size(); i++) {
                        FieldDefinition field = defRecord.fields.get(i);
                        values[i] = readField(buffer, field);
                    }
                    records.add(new FitRecord(false, defRecord.globalMessageNumber, localMessageType, null, values));
                }
            }
        }

        List<ActivityTrackpoint> trackpoints = new ArrayList<>();
        double totalDistance = 0.0;
        double totalTime = 0.0;
        int maxHr = 0;
        long sumHr = 0;
        int countHr = 0;
        int maxCadence = 0;
        long sumCadence = 0;
        int countCadence = 0;
        double calculatedMaxSpeed = 0.0;
        double prevAlt = NO_DATA_ALTITUDE;
        double elevationGain = 0.0;
        double elevationLoss = 0.0;
        double runningTotalDistance = 0.0;
        ActivityTrackpoint prevPoint = null;
        LocalDateTime startTime = null;
        Integer firstTimestamp = null;

        for (FitRecord record : records) {
            if (record.isDefinition || record.values == null) continue;

            switch (record.globalMessageNumber) {
                case 20 -> {
                    if (record.values.length >= 6) {
                        Integer lat = toInt(record.values[0]);
                        Integer lon = toInt(record.values[1]);
                        Integer altitude = toInt(record.values[2]);
                        Integer heartRate = toInt(record.values[3]);
                        Integer cadence = toInt(record.values[4]);
                        Integer speed = toInt(record.values[5]);
                        Integer timestamp = toInt(record.values[6]);

                        if (timestamp != null) {
                            localTimestamp = timestamp;
                            if (startTime == null) {
                                startTime = LocalDateTime.ofInstant(
                                    Instant.ofEpochSecond(timestamp + FIT_EPOCH_OFFSET), ZoneId.systemDefault());
                                firstTimestamp = timestamp;
                            }
                        }

                        double latDeg = lat != null ? (lat * (180.0 / 2147483648.0)) : 0;
                        double lonDeg = lon != null ? (lon * (180.0 / 2147483648.0)) : 0;
                        double altMeters = altitude != null ? (altitude / 5.0 - 500.0) : NO_DATA_ALTITUDE;
                        double speedMs = speed != null ? (speed / 1000.0) : 0.0;

                        if (altMeters != NO_DATA_ALTITUDE) {
                            if (prevAlt != NO_DATA_ALTITUDE) {
                                double diff = altMeters - prevAlt;
                                if (Math.abs(diff) > ELEVATION_NOISE_THRESHOLD) {
                                    if (diff > 0) elevationGain += diff;
                                    else elevationLoss += Math.abs(diff);
                                    prevAlt = altMeters;
                                }
                            } else {
                                prevAlt = altMeters;
                            }
                        }

                        int hr = heartRate != null ? heartRate : 0;
                        int cad = cadence != null ? cadence : 0;

                        if (hr > 0) {
                            if (hr > maxHr) maxHr = hr;
                            sumHr += hr;
                            countHr++;
                        }

                        if (cad > 0) {
                            sumCadence += cad;
                            countCadence++;
                        }

                        if (speedMs > calculatedMaxSpeed && speedMs < MAX_VALID_SPEED) {
                            calculatedMaxSpeed = speedMs;
                        }

                        if (lat != null && lon != null && prevPoint != null) {
                            double delta = calculateHaversineDistance(
                                prevPoint.getLatitude(), prevPoint.getLongitude(), latDeg, lonDeg);
                            if (delta > 0 && delta < HAVERSINE_MAX_DELTA) {
                                runningTotalDistance += delta;
                            }
                        }

                        LocalDateTime pointTime = startTime != null && firstTimestamp != null
                            ? startTime.plusSeconds(localTimestamp - firstTimestamp)
                            : LocalDateTime.now();

                        ActivityTrackpoint point = ActivityTrackpoint.builder()
                            .timestamp(pointTime)
                            .latitude(lat != null ? latDeg : null)
                            .longitude(lon != null ? lonDeg : null)
                            .altitudeMeters(altMeters != NO_DATA_ALTITUDE ? altMeters : null)
                            .distanceMeters(runningTotalDistance)
                            .heartRate(hr > 0 ? hr : null)
                            .cadence(cad > 0 ? cad : null)
                            .speed(speedMs > 0 ? speedMs : null)
                            .build();

                        trackpoints.add(point);
                        prevPoint = point;
                    }
                }
                case 253 -> {
                    if (record.values.length >= 1 && record.values[0] != null) {
                        globalStartTime = ((Number) record.values[0]).longValue();
                    }
                }
            }
        }

        // Calcular totalTime desde el primer y último trackpoint
        if (totalTime == 0.0 && trackpoints.size() >= 2) {
            LocalDateTime firstTs = trackpoints.get(0).getTimestamp();
            LocalDateTime lastTs = trackpoints.get(trackpoints.size() - 1).getTimestamp();
            if (firstTs != null && lastTs != null) {
                totalTime = java.time.Duration.between(firstTs, lastTs).getSeconds();
            }
        }

        String rawId = startTime != null
            ? startTime.toString().replace(":", "-").replace(".", "-")
            : "fit_" + System.currentTimeMillis();

        Activity activity = Activity.builder()
            .id(rawId)
            .name("Carrera " + rawId.substring(0, Math.min(rawId.length(), 10)))
            .startTime(startTime != null ? startTime : LocalDateTime.now())
            .distanceMeters(totalDistance > 0 ? totalDistance : runningTotalDistance)
            .totalTimeSeconds(totalTime)
            .maxHeartRate(maxHr > 0 ? maxHr : null)
            .avgHeartRate(countHr > 0 ? (int) (sumHr / countHr) : null)
            .avgCadence(countCadence > 0 ? (int) Math.round((double) sumCadence / countCadence) : null)
            .maxSpeed(calculatedMaxSpeed > 0 ? calculatedMaxSpeed : null)
            .elevationGain(elevationGain)
            .elevationLoss(elevationLoss)
            .build();

        activity.setAvgSpeed(activity.getTotalTimeSeconds() > 0 ?
            activity.getDistanceMeters() / activity.getTotalTimeSeconds() : 0.0);

        for (ActivityTrackpoint tp : trackpoints) {
            activity.addTrackpoint(tp);
        }

        if (analyticsService != null) {
            analyticsService.populateMetrics(activity);
        }

        return activity;
    }

    private FitRecord findDefinition(List<FitRecord> records, int localMessageType) {
        for (int i = records.size() - 1; i >= 0; i--) {
            FitRecord r = records.get(i);
            if (r.isDefinition && r.localMessageType == localMessageType) {
                return r;
            }
        }
        return null;
    }

    private Object readField(ByteBuffer buffer, FieldDefinition field) {
        int bytesNeeded = field.size;
        
        // Verificar que hay suficientes bytes disponibles
        if (buffer.remaining() < bytesNeeded) {
            return null;
        }
        
        int baseType = field.baseType & 0x1F;
        switch (baseType) {
            case 0x00: return buffer.get() & 0xFF;                    // enum (1 byte)
            case 0x01: return buffer.get();                           // sint8 (1 byte)
            case 0x02: return buffer.get() & 0xFF;                    // uint8 (1 byte)
            case 0x03: return buffer.getShort();                       // sint16 (2 bytes)
            case 0x04: return buffer.getShort() & 0xFFFF;              // uint16 (2 bytes)
            case 0x05: return buffer.getInt();                         // sint32 (4 bytes)
            case 0x06: return buffer.getInt() & 0xFFFFFFFFL;           // uint32 (4 bytes)
            case 0x07: {                                               // string (variable)
                byte[] str = new byte[field.size];
                buffer.get(str);
                return new String(str).trim();
            }
            case 0x08: return buffer.getFloat();                       // float32 (4 bytes)
            case 0x09: return buffer.getDouble();                      // float64 (8 bytes)
            case 0x0A: return buffer.get() & 0xFF;                    // uint8z (1 byte)
            case 0x0B: return buffer.getShort() & 0xFFFF;              // uint16z (2 bytes)
            case 0x0C: return buffer.getInt() & 0xFFFFFFFFL;           // uint32z (4 bytes)
            case 0x0D: {                                               // byte (array)
                byte[] bytes = new byte[field.size];
                buffer.get(bytes);
                return bytes;
            }
            case 0x0E: return buffer.getLong();                        // sint64 (8 bytes)
            case 0x0F: return buffer.getLong();                        // uint64 (8 bytes)
            case 0x13: return buffer.getLong();                        // uint64z (8 bytes)
            case 0x14: return buffer.getLong();                        // sint64z (8 bytes)
            default: {
                byte[] bytes = new byte[field.size];
                buffer.get(bytes);
                return bytes;
            }
        }
    }

    private Integer toInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            long v = ((Number) value).longValue();
            // FIT sentinel values indicating "no data"
            if (v == 0xFFL || v == 0xFFFFL || v == 0xFFFFFFFFL) return null;
            return (int) v;
        }
        return null;
    }

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private record FieldDefinition(int fieldNumber, int size, int baseType) {}
    private record FitRecord(boolean isDefinition, int globalMessageNumber, int localMessageType,
                              List<FieldDefinition> fields, Object[] values) {}
}
