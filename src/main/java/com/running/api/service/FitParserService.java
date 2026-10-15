package com.running.api.service;

import com.garmin.fit.DateTime;
import com.garmin.fit.Decode;
import com.garmin.fit.Mesg;
import com.garmin.fit.MesgBroadcaster;
import com.garmin.fit.MesgListener;
import com.garmin.fit.RecordMesgListener;
import com.garmin.fit.SessionMesg;
import com.garmin.fit.SessionMesgListener;
import com.garmin.fit.SportMesg;
import com.garmin.fit.SportMesgListener;
import com.running.api.dto.FitActivityDTO;
import com.running.api.mapper.ActivityMapper;
import com.running.api.model.Activity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class FitParserService {

    private final ActivityMapper activityMapper;

    private static final double SEMICIRCLES_TO_DEGREES = 180.0 / Math.pow(2, 31);
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final double ELEVATION_NOISE_THRESHOLD = 0.5; // metros

    /**
     * Parsea un archivo FIT y valida que sea una carrera.
     * Lanza IllegalArgumentException si no es running.
     */
    public Activity parse(InputStream inputStream) throws Exception {
        Decode decode = new Decode();
        MesgBroadcaster broadcaster = new MesgBroadcaster(decode);
        FitActivityDTO activityDTO = new FitActivityDTO();

        // Estado compartido entre listeners
        final String[] detectedSport = { null };
        final Double[] prevLat = { null };
        final Double[] prevLon = { null };
        final double[] accumulatedHaversineDistance = { 0.0 };
        final Double[] prevAltitude = { null };
        final double[] elevationGain = { 0.0 };
        final double[] elevationLoss = { 0.0 };
        final int[] messageCount = { 0 };

        // 1. Sport Listener - detecta el deporte
        broadcaster.addListener((SportMesgListener) mesg -> {
            messageCount[0]++;
            try {
                if (mesg.getSport() != null) {
                    detectedSport[0] = mesg.getSport().name().toLowerCase();
                    log.debug("SportMesg detectado: {}", detectedSport[0]);
                }
            } catch (Exception e) {
                log.debug("Error en SportMesg: {}", e.getMessage());
            }
        });

        // 2. Session Listener - datos de sesión + fallback de sport
        broadcaster.addListener((SessionMesgListener) mesg -> {
            messageCount[0]++;
            try {
                if (detectedSport[0] == null && mesg.getSport() != null) {
                    detectedSport[0] = mesg.getSport().name().toLowerCase();
                    log.debug("SessionMesg sport detectado: {}", detectedSport[0]);
                }
            } catch (Exception e) {
                // ignorar
            }
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

        // 3. Trackpoint Listener - puntos de track
        broadcaster.addListener((RecordMesgListener) mesg -> {
            messageCount[0]++;
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
                point.setSpeed(mesg.getEnhancedSpeed());
            } else if (mesg.getSpeed() != null) {
                point.setSpeed(mesg.getSpeed());
            }

            if (mesg.getEnhancedAltitude() != null) {
                point.setAltitudeMeters(mesg.getEnhancedAltitude().doubleValue());
            } else if (mesg.getAltitude() != null) {
                point.setAltitudeMeters((double) mesg.getAltitude());
            }

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

            if (mesg.getDistance() != null) {
                point.setDistanceMeters(mesg.getDistance().doubleValue());
            } else if (point.getLatitude() != null && point.getLongitude() != null) {
                point.setDistanceMeters(accumulatedHaversineDistance[0]);
            }

            if (mesg.getCadence() != null) {
                point.setCadence((int) mesg.getCadence() * 2);
            }

            activityDTO.getTrackPoints().add(point);
        });

        decode.read(inputStream, broadcaster, broadcaster);

        // Validar que sea una carrera
        String sport = detectedSport[0];
        boolean isRunning = "running".equals(sport) || "trail_running".equals(sport);
        if (!isRunning) {
            log.warn("Archivo FIT rechazado. Sport detectado: {} (mensajes procesados: {})",
                    sport, messageCount[0]);
            throw new IllegalArgumentException(
                "La actividad no es una carrera (running). Sport detectado: " + sport
            );
        }

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

        return activityMapper.toEntity(activityDTO, "");
    }

    /**
     * Detecta si la actividad es una carrera basándose en el campo sport.
     * @return true si es running, false si es otro deporte (cycling, swimming, etc.)
     */
    public static boolean isRunningActivity(InputStream inputStream) {
        try {
            Decode decode = new Decode();
            SportDetectorListener listener = new SportDetectorListener();
            decode.read(inputStream, listener);
            return listener.isRunning();
        } catch (Exception e) {
            return false;
        }
    }

    private static class SportDetectorListener implements MesgListener {
        private String sport;

        @Override
        public void onMesg(Mesg mesg) {
            try {
                if (mesg instanceof SportMesg sportMesg) {
                    if (sportMesg.getSport() != null) {
                        sport = sportMesg.getSport().name().toLowerCase();
                    }
                } else if (mesg instanceof SessionMesg sessionMesg) {
                    if (sport == null && sessionMesg.getSport() != null) {
                        sport = sessionMesg.getSport().name().toLowerCase();
                    }
                }
            } catch (Exception e) {
                // Ignorar
            }
        }

        boolean isRunning() {
            return "running".equals(sport) || "trail_running".equals(sport);
        }
    }

    private LocalDateTime convertToLocalDateTime(DateTime dateTime) {
        return dateTime.getDate()
                .toInstant()
                .atZone(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    private void calculateTrainingLoad(FitActivityDTO dto) {
        if (dto.getTotalTimerTimeSeconds() != null && dto.getTotalTimerTimeSeconds() > 0) {
            double durationMin = dto.getTotalTimerTimeSeconds() / 60.0;

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

    private void calculateVam(FitActivityDTO dto) {
        double maxSpeedMs = dto.getTrackPoints().stream()
                .filter(tp -> tp.getSpeed() != null && tp.getSpeed() > 0)
                .mapToDouble(FitActivityDTO.TrackPoint::getSpeed)
                .max()
                .orElse(0);

        if (maxSpeedMs > 0) {
            double maxSpeedKmh = maxSpeedMs * 3.6;

            double avgGradient = 0;
            if (dto.getTotalDistanceMeters() != null && dto.getTotalDistanceMeters() > 0
                    && dto.getElevationGain() != null && dto.getElevationGain() > 0) {
                avgGradient = dto.getElevationGain() / dto.getTotalDistanceMeters();
            }

            double vam = maxSpeedKmh * (1 + Math.max(avgGradient, 0) * 0.1);
            dto.setVamKmH(Math.round(vam * 10.0) / 10.0);
        }
    }

    private void calculateVo2Max(FitActivityDTO dto) {
        double maxSpeedMs = dto.getTrackPoints().stream()
                .filter(tp -> tp.getSpeed() != null && tp.getSpeed() > 0)
                .mapToDouble(FitActivityDTO.TrackPoint::getSpeed)
                .max()
                .orElse(0);

        if (maxSpeedMs > 1.0) {
            double v = maxSpeedMs * 60.0;
            double vo2 = -4.60 + 0.182258 * v + 0.000104 * v * v;

            if (vo2 > 20 && vo2 < 100) {
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
