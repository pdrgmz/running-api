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
import com.running.api.util.ElevationAccumulator;
import com.running.api.util.GeoUtils;
import com.running.api.util.RunningMetrics;
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
        final ElevationAccumulator elevation = new ElevationAccumulator(ELEVATION_NOISE_THRESHOLD);
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
                    double delta = GeoUtils.haversineMeters(
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
                elevation.add(point.getAltitudeMeters());
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
        if (!RunningMetrics.isRunningSport(sport)) {
            log.warn("Archivo FIT rechazado. Sport detectado: {} (mensajes procesados: {})",
                    sport, messageCount[0]);
            throw new IllegalArgumentException(
                "La actividad no es una carrera (running). Sport detectado: " + sport
            );
        }

        // Asignar elevation gain y loss calculados
        if (elevation.getGain() > 0) {
            activityDTO.setElevationGain(elevation.getGain());
        }
        if (elevation.getLoss() > 0) {
            activityDTO.setElevationLoss(elevation.getLoss());
        }

        // --- MÉTRICAS FISIOLÓGICAS (TRIMP, VAM, VO2Max) ---
        double hrAvg = activityDTO.getTrackPoints().stream()
                .filter(tp -> tp.getHeartRate() != null && tp.getHeartRate() > 0)
                .mapToInt(FitActivityDTO.TrackPoint::getHeartRate)
                .average()
                .orElse(0);
        int hrMax = activityDTO.getTrackPoints().stream()
                .filter(tp -> tp.getHeartRate() != null && tp.getHeartRate() > 0)
                .mapToInt(FitActivityDTO.TrackPoint::getHeartRate)
                .max()
                .orElse(0);
        double maxSpeedMs = activityDTO.getTrackPoints().stream()
                .filter(tp -> tp.getSpeed() != null && tp.getSpeed() > 0)
                .mapToDouble(FitActivityDTO.TrackPoint::getSpeed)
                .max()
                .orElse(0);
        double durationMin = activityDTO.getTotalTimerTimeSeconds() != null
                ? activityDTO.getTotalTimerTimeSeconds() / 60.0 : 0.0;

        activityDTO.setTrainingLoad(RunningMetrics.trainingLoad(durationMin, hrAvg, hrMax));
        activityDTO.setVamKmH(RunningMetrics.vamKmH(maxSpeedMs,
                activityDTO.getTotalDistanceMeters(), activityDTO.getElevationGain()));
        activityDTO.setVo2MaxEstimated(RunningMetrics.vo2Max(maxSpeedMs));

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
            return RunningMetrics.isRunningSport(sport);
        }
    }

    private LocalDateTime convertToLocalDateTime(DateTime dateTime) {
        return dateTime.getDate()
                .toInstant()
                .atZone(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}
