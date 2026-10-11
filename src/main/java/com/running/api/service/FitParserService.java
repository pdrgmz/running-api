package com.running.api.service;

import com.running.api.dto.FitActivityDTO;
import com.garmin.fit.Decode;
import com.garmin.fit.MesgBroadcaster;
import com.garmin.fit.RecordMesgListener;
import com.garmin.fit.SessionMesgListener;
import com.garmin.fit.DateTime;
import com.garmin.fit.FitRuntimeException;
import com.running.api.model.Activity;
import com.running.api.mapper.ActivityMapper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FitParserService {

    private final ActivityMapper activityMapper;

    private static final double SEMICIRCLES_TO_DEGREES = 180.0 / Math.pow(2, 31);
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final double ELEVATION_NOISE_THRESHOLD = 0.5; // metros

    public Activity parse(InputStream inputStream) {
        Decode decode = new Decode();
        MesgBroadcaster broadcaster = new MesgBroadcaster(decode);
        FitActivityDTO activityDTO = new FitActivityDTO();

        // Variables de estado para el cálculo paso a paso
        final Double[] prevLat = { null };
        final Double[] prevLon = { null };
        final double[] accumulatedHaversineDistance = { 0.0 };
        final Double[] prevAltitude = { null };
        final double[] elevationGain = { 0.0 };
        final double[] elevationLoss = { 0.0 };

        // 1. Session Listener
        broadcaster.addListener((SessionMesgListener) mesg -> {
            if (mesg.getTimestamp() != null && mesg.getTimestamp().getDate() != null) {
                activityDTO.setTimestamp(convertToLocalDateTime(mesg.getTimestamp()));
            }
            if (mesg.getTotalDistance() != null) {
                activityDTO.setTotalDistanceMeters((double) mesg.getTotalDistance());
            }
            if (mesg.getTotalTimerTime() != null) {
                activityDTO.setTotalTimerTimeSeconds((double) mesg.getTotalTimerTime());
            }
            if (mesg.getTotalCalories() != null) {
                activityDTO.setTotalCalories(mesg.getTotalCalories());
            }
            if (mesg.getMaxCadence() != null) {
                activityDTO.setMaxCadence((int) mesg.getMaxCadence());
            }
        });

        // 2. Trackpoint Listener
        broadcaster.addListener((RecordMesgListener) mesg -> {
            FitActivityDTO.TrackPoint point = new FitActivityDTO.TrackPoint();

            if (mesg.getTimestamp() != null && mesg.getTimestamp().getDate() != null) {
                point.setTimestamp(convertToLocalDateTime(mesg.getTimestamp()));
            }
            if (mesg.getPositionLat() != null) {
                point.setLatitude(mesg.getPositionLat() * SEMICIRCLES_TO_DEGREES);
            }
            if (mesg.getPositionLong() != null) {
                point.setLongitude(mesg.getPositionLong() * SEMICIRCLES_TO_DEGREES);
            }

            // --- CÁLCULO DE DISTANCIA HAVERSINE ---
            if (point.getLatitude() != null && point.getLongitude() != null) {
                if (prevLat[0] != null && prevLon[0] != null) {
                    double delta = calculateHaversineDistance(
                            prevLat[0], prevLon[0],
                            point.getLatitude(), point.getLongitude()
                    );
                    accumulatedHaversineDistance[0] += delta;
                }
                prevLat[0] = point.getLatitude();
                prevLon[0] = point.getLongitude();
            }

            if (mesg.getHeartRate() != null) {
                point.setHeartRate((int) mesg.getHeartRate());
            }
            if (mesg.getEnhancedSpeed() != null) {
                point.setSpeed(mesg.getEnhancedSpeed()); // m/s
            } else if (mesg.getSpeed() != null) {
                point.setSpeed(mesg.getSpeed());
            }

            // --- ALTITUD ---
            if (mesg.getEnhancedAltitude() != null) {
                point.setAltitudeMeters(mesg.getEnhancedAltitude().doubleValue());
            } else if (mesg.getAltitude() != null) {
                point.setAltitudeMeters((double) mesg.getAltitude());
            }

            // --- CÁLCULO DE ELEVATION GAIN Y LOSS ---
            if (point.getAltitudeMeters() != null) {
                if (prevAltitude[0] != null) {
                    double diff = point.getAltitudeMeters() - prevAltitude[0];
                    if (diff > ELEVATION_NOISE_THRESHOLD) {
                        elevationGain[0] += diff;
                    } else if (diff < -ELEVATION_NOISE_THRESHOLD) {
                        elevationLoss[0] += Math.abs(diff);
                    }
                }
                prevAltitude[0] = point.getAltitudeMeters();
            }

            // --- DISTANCIA ACUMULADA ---
            if (mesg.getDistance() != null) {
                point.setDistanceMeters(mesg.getDistance().doubleValue()); // metros desde el FIT
            } else if (point.getLatitude() != null && point.getLongitude() != null) {
                // Fallback con Haversine si el archivo FIT no trae el campo distance
                point.setDistanceMeters(accumulatedHaversineDistance[0]);
            }

            // --- CADENCIA ---
            if (mesg.getCadence() != null) {
                point.setCadence((int) mesg.getCadence() * 2);
            }

            activityDTO.getTrackPoints().add(point);
        });

        decode.read(inputStream, broadcaster, broadcaster);

        // Asignar elevation gain y loss calculados
        if (elevationGain[0] > 0) {
            activityDTO.setElevationGain(elevationGain[0]);
        }
        if (elevationLoss[0] > 0) {
            activityDTO.setElevationLoss(elevationLoss[0]);
        }

        // --- CÁLCULO DE TRAINING LOAD (TRIMP simplificado) ---
        calculateTrainingLoad(activityDTO);

        // --- CÁLCULO DE VAM (Velocidad Aeróbica Máxima) ---
        calculateVam(activityDTO);

        // --- CÁLCULO DE VO2MAX ESTIMADO ---
        calculateVo2Max(activityDTO);

        Activity activity = activityMapper.toEntity(activityDTO, "");
        
        return activity;
    }

    private LocalDateTime convertToLocalDateTime(DateTime dateTime) {
        return dateTime.getDate()
                .toInstant()
                .atZone(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    /**
     * Training Load - TRIMP simplificado de Banister:
     * TL = duración(min) * (HRavg / HRmax)
     * Rango típico: 50-400 para una sesión de carrera
     */
    private void calculateTrainingLoad(FitActivityDTO dto) {
        if (dto.getTotalTimerTimeSeconds() != null && dto.getTotalTimerTimeSeconds() > 0) {
            double durationMin = dto.getTotalTimerTimeSeconds() / 60.0;

            // Calcular HR promedio desde trackpoints
            double hrAvg = dto.getTrackPoints().stream()
                    .filter(tp -> tp.getHeartRate() != null && tp.getHeartRate() > 0)
                    .mapToInt(FitActivityDTO.TrackPoint::getHeartRate)
                    .average()
                    .orElse(0);

            int hrMax = dto.getTrackPoints().stream()
                    .filter(tp -> tp.getHeartRate() != null && tp.getHeartRate() > 0)
                    .mapToInt(FitActivityDTO.TrackPoint::getHeartRate)
                    .max()
                    .orElse(0);

            if (hrAvg > 0 && hrMax > 0 && hrMax > hrAvg) {
                double tl = durationMin * (hrAvg / hrMax);
                dto.setTrainingLoad(Math.round(tl * 10.0) / 10.0);
            }
        }
    }

    /**
     * VAM (Velocidad Aeróbica Máxima) - estimación desde velocidad máxima y pendiente.
     * VAM = velocidad(km/h) * pendientedecimal
     * Si no hay pendiente, usa velocidad * factor de estimación.
     */
    private void calculateVam(FitActivityDTO dto) {
        double maxSpeedMs = dto.getTrackPoints().stream()
                .filter(tp -> tp.getSpeed() != null && tp.getSpeed() > 0)
                .mapToDouble(FitActivityDTO.TrackPoint::getSpeed)
                .max()
                .orElse(0);

        if (maxSpeedMs > 0) {
            double maxSpeedKmh = maxSpeedMs * 3.6;

            // Pendiente media desde elevation gain y distancia
            double avgGradient = 0;
            if (dto.getTotalDistanceMeters() != null && dto.getTotalDistanceMeters() > 0
                    && dto.getElevationGain() != null && dto.getElevationGain() > 0) {
                avgGradient = dto.getElevationGain() / dto.getTotalDistanceMeters();
            }

            // VAM = velocidad * (1 + pendiente) — estimación aeróbica
            double vam = maxSpeedKmh * (1 + Math.max(avgGradient, 0) * 0.1);
            dto.setVamKmH(Math.round(vam * 10.0) / 10.0);
        }
    }

    /**
     * VO2Max estimado usando la fórmula de Daniels-Gilbert:
     * VO2 = -4.60 + 0.182258*v + 0.000104*v^2
     * donde v = velocidad en m/min
     * Resultado en mL/kg/min
     */
    private void calculateVo2Max(FitActivityDTO dto) {
        double maxSpeedMs = dto.getTrackPoints().stream()
                .filter(tp -> tp.getSpeed() != null && tp.getSpeed() > 0)
                .mapToDouble(FitActivityDTO.TrackPoint::getSpeed)
                .max()
                .orElse(0);

        if (maxSpeedMs > 1.0) { // Mínimo 1 m/s para estimación válida
            double v = maxSpeedMs * 60.0; // m/min
            double vo2 = -4.60 + 0.182258 * v + 0.000104 * v * v;

            if (vo2 > 20 && vo2 < 100) { // Rango fisiológico válido
                dto.setVo2MaxEstimated(Math.round(vo2 * 10.0) / 10.0);
            }
        }
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

}