package com.running.api.service;

import com.running.api.dto.AcwrStatusDto;
import com.running.api.dto.CardiacDriftResponseDto;
import com.running.api.dto.EddingtonStatsDto;
import com.running.api.dto.HrZoneDetailDto;
import com.running.api.dto.HrZonesMapDto;
import com.running.api.dto.HrZonesResponseDto;
import com.running.api.dto.PmcPointDto;
import com.running.api.dto.RacePredictionsDto;
import com.running.api.dto.SplitDto;
import com.running.api.dto.TrainingLoadResponseDto;
import com.running.api.dto.Vo2MaxVamResponseDto;
import com.running.api.exception.ResourceNotFoundException;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.projection.DailyTrimpProjection;
import com.running.api.util.DistanceSeries;
import com.running.api.util.ElevationAccumulator;
import com.running.api.util.RunningMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final int RESTING_HEART_RATE = 60;
    private static final double ELEVATION_NOISE_THRESHOLD = 0.01;
    private static final double MIN_VALID_SPEED = 1.5;
    private static final double MIN_VO2MAX = 20.0;
    private static final double MAX_VO2MAX = 85.0;
    private static final double DEFAULT_VO2MAX = 35.0;
    private static final double MIN_VO2MAX_FLOOR = 15.0;
    private static final double PMC_CTL_DAYS = 42.0;
    private static final double PMC_ATL_DAYS = 7.0;
    private static final int PMC_WARMUP_DAYS = 120;

    /**
     * Ventana crónica del ACWR. Los umbrales 0.8-1.3 proceden de los estudios 7:28;
     * el CTL de 42 días se mantiene únicamente para la curva PMC (fitness/fatiga).
     */
    private static final double ACWR_CHRONIC_DAYS = 28.0;

    /** Días mínimos de historial para que el ACWR sea interpretable. */
    private static final int ACWR_MIN_HISTORY_DAYS = 28;

    /** Carga crónica mínima: por debajo, el ratio amplifica el ruido de datos casi nulos. */
    private static final double ACWR_MIN_CHRONIC_LOAD = 1.0;
    private static final double VO2MAX_RECREATIONAL_THRESHOLD = 42.0;
    private static final int FALLBACK_MAX_HR = 185;

    private final ActivityRepository activityRepository;

    @Value("${app.user.max-hr:185}")
    private Integer defaultMaxHr;

    // 1. Splits por Kilómetro
    public List<SplitDto> calculateSplits(Long userId, String activityId) {
        Activity activity = activityRepository.findByIdAndUserId(activityId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada con id: " + activityId));

        List<ActivityTrackpoint> points = new ArrayList<>(activity.getTrackpoints());
        points.sort(Comparator.comparing(ActivityTrackpoint::getTimestamp,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<SplitDto> splits = new ArrayList<>();

        if (points.size() < 2) return splits;

        // Distancia saneada por punto: evita que un salto corrupto del dispositivo cree un km fantasma.
        // La serie se ordena internamente por timestamp aunque la colección JPA llegue desordenada.
        double[] distances = DistanceSeries.reconstructPerPoint(points, RunningMetrics.MAX_PLAUSIBLE_SPEED_MPS);

        double trackDistance = distances[distances.length - 1] - distances[0];
        if (trackDistance <= 0) return splits;

        // La distancia del resumen de la actividad es la referencia: si la telemetría se desvía
        // (sensores que reportan distancias inconsistentes o series incompletas), se normaliza
        // para que los splits sumen la distancia real de la carrera y no un múltiplo de ella.
        double activityDistance = activity.getDistanceMeters() != null ? activity.getDistanceMeters() : 0.0;
        double targetTotal = activityDistance > 0 ? activityDistance : trackDistance;
        double scale = Math.abs(targetTotal - trackDistance) / targetTotal > 0.02
                ? targetTotal / trackDistance
                : 1.0;

        double base = distances[0];
        for (int i = 0; i < distances.length; i++) {
            distances[i] = (distances[i] - base) * scale;
        }

        double totalDistance = distances[distances.length - 1];
        if (totalDistance <= 0) return splits;

        int km = 1;
        double previousTarget = 0.0;
        LocalDateTime segmentStartTime = points.get(0).getTimestamp();

        for (int i = 1; i < points.size(); i++) {
            double previousDistance = distances[i - 1];
            double currentDistance = distances[i];
            if (currentDistance <= previousDistance) continue;

            // Un mismo tramo puede cruzar varios kilómetros (huecos de GPS, muestras espaciadas).
            while (previousTarget + 1000.0 <= currentDistance) {
                double target = previousTarget + 1000.0;
                double fraction = (target - previousDistance) / (currentDistance - previousDistance);
                LocalDateTime targetTime = interpolateTime(
                        points.get(i - 1).getTimestamp(), points.get(i).getTimestamp(), fraction);

                splits.add(buildSplitDto(km, 1000.0, segmentStartTime, targetTime,
                        averageHeartRate(points, distances, previousTarget, target)));

                km++;
                previousTarget = target;
                segmentStartTime = targetTime;
            }
        }

        // Tramo final incompleto (el último km parcial).
        if (totalDistance - previousTarget > 1.0) {
            LocalDateTime endTime = points.get(points.size() - 1).getTimestamp();
            splits.add(buildSplitDto(km, totalDistance - previousTarget, segmentStartTime, endTime,
                    averageHeartRate(points, distances, previousTarget, totalDistance)));
        }

        return splits;
    }

    private LocalDateTime interpolateTime(LocalDateTime from, LocalDateTime to, double fraction) {
        if (from == null) return to;
        if (to == null) return from;

        long nanos = Duration.between(from, to).toNanos();
        if (nanos <= 0) return from;

        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        return from.plusNanos(Math.round(nanos * clamped));
    }

    private int averageHeartRate(List<ActivityTrackpoint> points, double[] distances,
                                 double fromDistance, double toDistance) {
        int sumHr = 0;
        int countHr = 0;

        for (int i = 0; i < points.size(); i++) {
            double distance = distances[i];
            if (distance < fromDistance || distance > toDistance) continue;

            Integer hr = points.get(i).getHeartRate();
            if (hr != null && hr > 0) {
                sumHr += hr;
                countHr++;
            }
        }

        return countHr > 0 ? (sumHr / countHr) : 0;
    }

    private SplitDto buildSplitDto(int km, double distMeters, LocalDateTime startTime,
                                   LocalDateTime endTime, int avgHeartRate) {
        long timeSeconds = 0;
        if (startTime != null && endTime != null) {
            timeSeconds = Math.max(0L, Math.round(Duration.between(startTime, endTime).toNanos() / 1_000_000_000.0));
        }

        double avgSpeed = timeSeconds > 0 ? distMeters / timeSeconds : 0.0;
        double paceMinPerKm = avgSpeed > 0 ? (1000.0 / (avgSpeed * 60.0)) : 0.0;

        return SplitDto.builder()
                .km(km)
                .distanceMeters(Math.round(distMeters * 100.0) / 100.0)
                .timeSeconds(timeSeconds)
                .paceMinPerKm(String.format("%.2f", paceMinPerKm))
                .avgHeartRate(avgHeartRate)
                .build();
    }

    // 2. Zonas de Frecuencia Cardíaca (Z1 a Z5)
    public HrZonesResponseDto calculateHrZones(Long userId, String activityId) {
        Activity activity = activityRepository.findByIdAndUserId(activityId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada con id: " + activityId));

        // La referencia es la FC máxima del atleta, no el pico de esta sesión: si se usa el
        // máximo de la actividad, todas las muestras se comprimen hacia las zonas altas y un
        // rodaje suave acaba mostrándose como Z4/Z5.
        int maxHr = resolveAthleteMaxHr(userId, activity.getMaxHeartRate());

        int z1 = 0, z2 = 0, z3 = 0, z4 = 0, z5 = 0;

        List<ActivityTrackpoint> points = activity.getTrackpoints();
        if (points != null) {
            for (ActivityTrackpoint pt : points) {
                if (pt.getHeartRate() == null || pt.getHeartRate() == 0) continue;
                double pct = (double) pt.getHeartRate() / maxHr;

                if (pct < 0.60) z1++;
                else if (pct < 0.70) z2++;
                else if (pct < 0.80) z3++;
                else if (pct < 0.90) z4++;
                else z5++;
            }
        }

        int total = z1 + z2 + z3 + z4 + z5;

        HrZonesMapDto map = HrZonesMapDto.builder()
                .z1Recovery(new HrZoneDetailDto(z1, total > 0 ? Math.round((z1 * 100.0 / total) * 10.0) / 10.0 : 0.0))
                .z2Endurance(new HrZoneDetailDto(z2, total > 0 ? Math.round((z2 * 100.0 / total) * 10.0) / 10.0 : 0.0))
                .z3Tempo(new HrZoneDetailDto(z3, total > 0 ? Math.round((z3 * 100.0 / total) * 10.0) / 10.0 : 0.0))
                .z4Threshold(new HrZoneDetailDto(z4, total > 0 ? Math.round((z4 * 100.0 / total) * 10.0) / 10.0 : 0.0))
                .z5Anaerobic(new HrZoneDetailDto(z5, total > 0 ? Math.round((z5 * 100.0 / total) * 10.0) / 10.0 : 0.0))
                .build();

        return HrZonesResponseDto.builder()
                .maxHrUsed(maxHr)
                .zones(map)
                .build();
    }

    /**
     * FC máxima de referencia del atleta.
     *
     * <p>Las zonas y las métricas derivadas del pulso deben calcularse contra la FC máxima
     * del atleta, no contra el pico de cada sesión: usar el máximo de la actividad comprime
     * todas las muestras hacia las zonas altas y hace que un rodaje suave parezca Z4/Z5.
     *
     * <p>Se parte de los picos plausibles de todas las sesiones del atleta, más el pico de la
     * actividad en curso cuando todavía no está persistida. Sobre esa serie se aplica
     * {@link RunningMetrics#robustMaxHeartRate(java.util.Collection)}: un pico aislado (por
     * ejemplo, una sobrelectura óptica de 200 bpm cuando el resto ronda 190) no se convierte
     * en la referencia de todo el historial. Si no hay ningún dato válido se usa la propiedad
     * {@code app.user.max-hr}.
     *
     * @param userId    propietario de las actividades (puede ser {@code null} en cálculos aislados)
     * @param activityMaxHr pico de la actividad que se está analizando (opcional)
     * @return FC máxima de referencia en bpm, siempre dentro de un rango fisiológico plausible
     */
    public int resolveAthleteMaxHr(Long userId, Integer activityMaxHr) {
        List<Integer> sessionMaxima = new ArrayList<>();
        if (userId != null) {
            List<Integer> stored = activityRepository.findMaxHeartRatesByUserId(userId);
            if (stored != null) {
                sessionMaxima.addAll(stored);
            }
        }
        if (activityMaxHr != null) {
            sessionMaxima.add(activityMaxHr);
        }

        int best = RunningMetrics.robustMaxHeartRate(sessionMaxima);
        return best > 0 ? best : configuredMaxHr();
    }

    private int configuredMaxHr() {
        return defaultMaxHr != null && defaultMaxHr > 0 ? defaultMaxHr : FALLBACK_MAX_HR;
    }

    // 3. Cardiac Drift (Desacople Aeróbico) con guardas contra división por cero
    public CardiacDriftResponseDto calculateCardiacDrift(Long userId, String activityId) {
        Activity activity = activityRepository.findByIdAndUserId(activityId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada con id: " + activityId));

        List<ActivityTrackpoint> points = activity.getTrackpoints();
        if (points.size() < 10) {
            return CardiacDriftResponseDto.builder()
                    .status("Insuficientes puntos de telemetría para calcular desacople aeróbico")
                    .build();
        }

        int half = points.size() / 2;
        double ratio1 = calculateHalfRatio(points.subList(0, half));
        double ratio2 = calculateHalfRatio(points.subList(half, points.size()));

        if (ratio1 <= 0.0) {
            return CardiacDriftResponseDto.builder()
                    .status("Datos de pulso/velocidad no válidos en la primera mitad")
                    .build();
        }

        double driftPct = ((ratio2 - ratio1) / ratio1) * 100.0;

        return CardiacDriftResponseDto.builder()
                .firstHalfEfficiencyRatio(Math.round(ratio1 * 1000.0) / 1000.0)
                .secondHalfEfficiencyRatio(Math.round(ratio2 * 1000.0) / 1000.0)
                .cardiacDriftPercentage(String.format("%.2f", driftPct))
                .decouplingStatus(driftPct > 5.0 ? "Desacople significativo (Fatiga/Deshidratación)" : "Esfuerzo aeróbico estable")
                .build();
    }

    private double calculateHalfRatio(List<ActivityTrackpoint> segment) {
        if (segment.isEmpty()) return 0.0;

        ActivityTrackpoint first = segment.get(0);
        ActivityTrackpoint last = segment.get(segment.size() - 1);
        if (first.getTimestamp() == null || last.getTimestamp() == null) return 0.0;

        double[] distances = DistanceSeries.reconstructPerPoint(segment, RunningMetrics.MAX_PLAUSIBLE_SPEED_MPS);
        double totalDist = distances[distances.length - 1] - distances[0];
        long timeSec = Duration.between(first.getTimestamp(), last.getTimestamp()).getSeconds();
        double avgSpeed = timeSec > 0 ? totalDist / timeSec : 0.0;

        int sumHr = 0, countHr = 0;
        for (ActivityTrackpoint pt : segment) {
            if (pt.getHeartRate() != null && pt.getHeartRate() > 0) {
                sumHr += pt.getHeartRate();
                countHr++;
            }
        }
        if (countHr == 0 || avgSpeed <= 0) return 0.0;
        double avgHr = (double) sumHr / countHr;
        return avgSpeed / avgHr;
    }

    // 4. Carga de Entrenamiento (TRIMP / HRSS)
    public TrainingLoadResponseDto calculateTrainingLoad(Long userId, String activityId) {
        Activity activity = activityRepository.findByIdAndUserId(activityId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada con id: " + activityId));
        return computeTrainingLoadDetails(activity);
    }

    public TrainingLoadResponseDto computeTrainingLoadDetails(Activity activity) {
        return computeTrainingLoadDetails(activity,
                resolveAthleteMaxHr(activity.getUserId(), activity.getMaxHeartRate()));
    }

    public TrainingLoadResponseDto computeTrainingLoadDetails(Activity activity, int athleteMaxHr) {
        List<ActivityTrackpoint> trackpoints = activity.getTrackpoints();
        int maxHr = athleteMaxHr;
        int restHr = RESTING_HEART_RATE;

        double edwardsTrimp = 0.0;
        double totalSecondsWithHr = 0.0;

        if (trackpoints != null && !trackpoints.isEmpty()) {
            ActivityTrackpoint prevPt = null;
            for (ActivityTrackpoint pt : trackpoints) {
                if (pt.getHeartRate() != null && pt.getHeartRate() > 0) {
                    double dtSec = 1.0;
                    if (prevPt != null && prevPt.getTimestamp() != null && pt.getTimestamp() != null) {
                        long diff = Duration.between(prevPt.getTimestamp(), pt.getTimestamp()).getSeconds();
                        if (diff > 0 && diff < 30) dtSec = diff;
                    }
                    totalSecondsWithHr += dtSec;

                    double hrFraction = Math.max(0.0, Math.min(1.0, (double) (pt.getHeartRate() - restHr) / (maxHr - restHr)));
                    double zoneMultiplier;
                    if (hrFraction < 0.60) zoneMultiplier = 1.0;
                    else if (hrFraction < 0.70) zoneMultiplier = 2.0;
                    else if (hrFraction < 0.80) zoneMultiplier = 3.0;
                    else if (hrFraction < 0.90) zoneMultiplier = 4.0;
                    else zoneMultiplier = 5.0;

                    edwardsTrimp += (dtSec / 60.0) * zoneMultiplier;
                }
                prevPt = pt;
            }
        }

        double durationMin = (activity.getTotalTimeSeconds() != null ? activity.getTotalTimeSeconds() : totalSecondsWithHr) / 60.0;
        double avgHr = activity.getAvgHeartRate() != null ? activity.getAvgHeartRate() : 0.0;
        double banisterTrimp = 0.0;
        if (avgHr > restHr && durationMin > 0) {
            double hrrAvg = Math.max(0.0, Math.min(1.0, (avgHr - restHr) / (maxHr - restHr)));
            banisterTrimp = durationMin * hrrAvg * 0.64 * Math.exp(1.92 * hrrAvg);
        }

        if (edwardsTrimp == 0.0 && banisterTrimp > 0.0) {
            edwardsTrimp = banisterTrimp;
        }

        double primaryTrainingLoad = edwardsTrimp > 0 ? Math.round(edwardsTrimp * 10.0) / 10.0 : Math.round(banisterTrimp * 10.0) / 10.0;
        double roundedBanister = Math.round(banisterTrimp * 10.0) / 10.0;
        double hrss = primaryTrainingLoad > 0 ? Math.round((primaryTrainingLoad / 240.0) * 100.0 * 10.0) / 10.0 : 0.0;

        String effortLevel;
        if (primaryTrainingLoad < 50) effortLevel = "Suave / Recuperación";
        else if (primaryTrainingLoad < 120) effortLevel = "Moderado";
        else if (primaryTrainingLoad < 200) effortLevel = "Intenso";
        else if (primaryTrainingLoad < 300) effortLevel = "Muy Intenso";
        else effortLevel = "Extremo / Agotador";

        return TrainingLoadResponseDto.builder()
                .activityId(activity.getId() != null ? activity.getId() : "")
                .trainingLoad(primaryTrainingLoad)
                .edwardsTrimp(Math.round(edwardsTrimp * 10.0) / 10.0)
                .banisterTrimp(roundedBanister)
                .hrss(hrss)
                .effortLevel(effortLevel)
                .build();
    }

    // 5. Estimación VO2Max y VAM (Velocidad Aeróbica Máxima)
    public Vo2MaxVamResponseDto calculateVo2MaxAndVam(Long userId, String activityId) {
        Activity activity = activityRepository.findByIdAndUserId(activityId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada con id: " + activityId));
        return computeVo2MaxAndVamDetails(activity);
    }

    public Vo2MaxVamResponseDto computeVo2MaxAndVamDetails(Activity activity) {
        return computeVo2MaxAndVamDetails(activity,
                resolveAthleteMaxHr(activity.getUserId(), activity.getMaxHeartRate()));
    }

    public Vo2MaxVamResponseDto computeVo2MaxAndVamDetails(Activity activity, int athleteMaxHr) {
        List<ActivityTrackpoint> trackpoints = activity.getTrackpoints();
        int maxHr = athleteMaxHr;
        int restHr = RESTING_HEART_RATE;

        Double estimatedVo2Max = null;

        if (trackpoints != null && !trackpoints.isEmpty()) {
            List<Double> vo2Samples = new ArrayList<>();
            for (ActivityTrackpoint pt : trackpoints) {
                Double speed = pt.getSpeed();
                Integer hr = pt.getHeartRate();
                if (speed != null && speed > MIN_VALID_SPEED && speed < RunningMetrics.MAX_PLAUSIBLE_SPEED_MPS
                        && hr != null && hr > (restHr + 30)) {
                    double speedMPerMin = speed * 60.0;
                    double vo2Cost = (speedMPerMin * 0.2) + 3.5;
                    double fractionHrr = Math.max(0.2, Math.min(1.0, (double) (hr - restHr) / (maxHr - restHr)));
                    double vo2MaxEst = vo2Cost / fractionHrr;
                    if (vo2MaxEst >= MIN_VO2MAX && vo2MaxEst <= MAX_VO2MAX) {
                        vo2Samples.add(vo2MaxEst);
                    }
                }
            }
            if (!vo2Samples.isEmpty()) {
                double sum = 0.0;
                for (Double val : vo2Samples) sum += val;
                estimatedVo2Max = sum / vo2Samples.size();
            }
        }

        if (estimatedVo2Max == null) {
            Double avgSpeed = activity.getAvgSpeed();
            Integer avgHr = activity.getAvgHeartRate();
            if (avgSpeed != null && avgSpeed > MIN_VALID_SPEED && avgHr != null && avgHr > restHr) {
                double speedMPerMin = avgSpeed * 60.0;
                double vo2Cost = (speedMPerMin * 0.2) + 3.5;
                double fractionHrr = Math.max(0.2, Math.min(1.0, (double) (avgHr - restHr) / (maxHr - restHr)));
                estimatedVo2Max = vo2Cost / fractionHrr;
            } else if (activity.getDistanceMeters() != null && activity.getTotalTimeSeconds() != null && activity.getTotalTimeSeconds() > 0) {
                double timeMin = activity.getTotalTimeSeconds() / 60.0;
                double v = activity.getDistanceMeters() / timeMin;
                double vo2Cost = -4.60 + (0.182258 * v) + (0.000104 * v * v);
                double pctVo2Max = 0.8 + (0.1894393 * Math.exp(-0.012778 * timeMin)) + (0.2989558 * Math.exp(-0.1932605 * timeMin));
                estimatedVo2Max = vo2Cost / pctVo2Max;
            }
        }

        if (estimatedVo2Max == null || estimatedVo2Max < MIN_VO2MAX_FLOOR) {
            estimatedVo2Max = DEFAULT_VO2MAX;
        }

        double finalVo2Max = Math.round(estimatedVo2Max * 10.0) / 10.0;
        double vamKmH = Math.round((finalVo2Max / 3.5) * 100.0) / 100.0;

        String fitnessCategory;
        if (finalVo2Max < DEFAULT_VO2MAX) fitnessCategory = "Inicial / Principiante";
        else if (finalVo2Max < VO2MAX_RECREATIONAL_THRESHOLD) fitnessCategory = "Recreativo / Medio";
        else if (finalVo2Max < 50.0) fitnessCategory = "Intermedio / Bueno";
        else if (finalVo2Max < 60.0) fitnessCategory = "Avanzado / Excelente";
        else fitnessCategory = "Elite / Atleta de Alto Rendimiento";

        RacePredictionsDto predictions = RacePredictionsDto.builder()
                .k5(formatPredictionTime(5000.0 / (vamKmH * 0.95 / 3.6)))
                .k10(formatPredictionTime(10000.0 / (vamKmH * 0.90 / 3.6)))
                .k21(formatPredictionTime(21097.5 / (vamKmH * 0.85 / 3.6)))
                .k42(formatPredictionTime(42195.0 / (vamKmH * 0.80 / 3.6)))
                .build();

        return Vo2MaxVamResponseDto.builder()
                .activityId(activity.getId() != null ? activity.getId() : "")
                .vo2MaxEstimated(finalVo2Max)
                .vamKmH(vamKmH)
                .fitnessCategory(fitnessCategory)
                .predictedRaceTimes(predictions)
                .build();
    }

    private String formatPredictionTime(double seconds) {
        long sec = (long) seconds;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) {
            return String.format("%02d:%02d:%02d", h, m, s);
        } else {
            return String.format("%02d:%02d", m, s);
        }
    }

    public boolean populateMetrics(Activity activity) {
        return populateMetrics(activity, null);
    }

    public boolean populateMetrics(Activity activity, List<ActivityTrackpoint> points) {
        if (activity == null) return false;
        boolean modified = false;

        if (points == null || points.isEmpty()) {
            points = activity.getTrackpoints();
        }

        if ((activity.getAvgCadence() == null || activity.getMaxCadence() == null) && points != null && !points.isEmpty()) {
            int maxCad = 0;
            long sumCad = 0;
            int countCad = 0;
            for (ActivityTrackpoint pt : points) {
                if (pt.getCadence() != null && pt.getCadence() > 0) {
                    if (pt.getCadence() > maxCad) maxCad = pt.getCadence();
                    sumCad += pt.getCadence();
                    countCad++;
                }
            }
            if (activity.getAvgCadence() == null && countCad > 0) {
                activity.setAvgCadence((int) Math.round((double) sumCad / countCad));
                modified = true;
            }
            if (activity.getMaxCadence() == null && maxCad > 0) {
                activity.setMaxCadence(maxCad);
                modified = true;
            }
        }

        if (activity.getAvgPaceMinPerKm() == null && activity.getAvgSpeed() != null && activity.getAvgSpeed() > 0) {
            double avgPace = 1000.0 / (activity.getAvgSpeed() * 60.0);
            activity.setAvgPaceMinPerKm(Math.round(avgPace * 100.0) / 100.0);
            modified = true;
        }
        if (activity.getMaxPaceMinPerKm() == null && activity.getMaxSpeed() != null && activity.getMaxSpeed() > 0) {
            double maxPace = 1000.0 / (activity.getMaxSpeed() * 60.0);
            activity.setMaxPaceMinPerKm(Math.round(maxPace * 100.0) / 100.0);
            modified = true;
        }

        Integer athleteMaxHr = null;

        if (activity.getTrainingLoad() == null) {
            athleteMaxHr = resolveAthleteMaxHr(activity.getUserId(), activity.getMaxHeartRate());
            TrainingLoadResponseDto tlDetails = computeTrainingLoadDetails(activity, athleteMaxHr);
            Double tl = tlDetails.getTrainingLoad();
            activity.setTrainingLoad(tl);
            modified = true;
        }

        if (activity.getVo2MaxEstimated() == null || activity.getVamKmH() == null) {
            if (athleteMaxHr == null) {
                athleteMaxHr = resolveAthleteMaxHr(activity.getUserId(), activity.getMaxHeartRate());
            }
            Vo2MaxVamResponseDto vo2Details = computeVo2MaxAndVamDetails(activity, athleteMaxHr);
            Double vo2 = vo2Details.getVo2MaxEstimated();
            Double vam = vo2Details.getVamKmH();
            activity.setVo2MaxEstimated(vo2);
            activity.setVamKmH(vam);
            modified = true;
        }

        if ((activity.getElevationGain() == null || (activity.getElevationGain() == 0.0 && (activity.getElevationLoss() == null || activity.getElevationLoss() == 0.0)))
                && points != null && !points.isEmpty()) {
            ElevationAccumulator elevation = new ElevationAccumulator(ELEVATION_NOISE_THRESHOLD);
            for (ActivityTrackpoint pt : points) {
                if (pt.getAltitudeMeters() != null) {
                    elevation.add(pt.getAltitudeMeters());
                }
            }
            if (elevation.getGain() > 0.0 || elevation.getLoss() > 0.0) {
                activity.setElevationGain(Math.round(elevation.getGain() * 10.0) / 10.0);
                activity.setElevationLoss(Math.round(elevation.getLoss() * 10.0) / 10.0);
                modified = true;
            }
        }

        return modified;
    }

    /**
     * Recalcula las métricas derivadas de la FC máxima de referencia del atleta.
     *
     * <p>Se invoca tras importar, cuando la actividad ya tiene {@code userId} y la telemetría
     * está completa. Así el TRIMP guardado se calcula con la misma referencia que usa la API
     * y no con el pico de la sesión, que era el comportamiento previo cuando los parsers
     * llamaban a {@link #populateMetrics(Activity)} antes de asignar el propietario.
     *
     * @param activity actividad ya persistida o a persistir
     */
    public void refreshHeartRateMetrics(Activity activity) {
        if (activity == null) {
            return;
        }
        int athleteMaxHr = resolveAthleteMaxHr(activity.getUserId(), activity.getMaxHeartRate());
        activity.setTrainingLoad(computeTrainingLoadDetails(activity, athleteMaxHr).getTrainingLoad());
        Vo2MaxVamResponseDto vo2 = computeVo2MaxAndVamDetails(activity, athleteMaxHr);
        activity.setVo2MaxEstimated(vo2.getVo2MaxEstimated());
        activity.setVamKmH(vo2.getVamKmH());
    }

    public List<PmcPointDto> calculatePmcHistory(Long userId, LocalDate startDate, LocalDate endDate) {
        // 1. Ampliamos el rango de búsqueda 120 días atrás para el "warm-up" del algoritmo EWMA
        LocalDate warmUpStartDate = startDate.minusDays(PMC_WARMUP_DAYS);

        // 2. Mapeamos la suma de TRIMP agrupada por fecha desde la BD
        Map<LocalDate, Double> dailyTrimpMap = activityRepository
        .findDailyTrimpSum(userId, warmUpStartDate, endDate)
        .stream()
        .collect(Collectors.toMap(
                DailyTrimpProjection::getDate,
                DailyTrimpProjection::getTotalTrimp,
                Double::sum // <--- MERGE FUNCTION: Suma las cargas si hay múltiples filas/carreras el mismo día
        ));

        List<PmcPointDto> pmcSeries = new ArrayList<>();

        // Variables de estado iniciales para EWMA
        double ctl = 0.0;
        double ctl28 = 0.0;
        double atl = 0.0;

        // Factores de suavizado para 42 días (CTL del PMC), 28 días (CTL del ACWR) y 7 días (ATL)
        double alphaCtl = 1.0 - Math.exp(-1.0 / PMC_CTL_DAYS); // ~0.0235
        double alphaCtl28 = 1.0 - Math.exp(-1.0 / ACWR_CHRONIC_DAYS); // ~0.0351
        double alphaAtl = 1.0 - Math.exp(-1.0 / PMC_ATL_DAYS);  // ~0.1331

        // 3. Iteración DÍA A DÍA CALENDARIO contigua (procesa días de descanso con 0.0 TRIMP)
        LocalDate current = warmUpStartDate;
        while (!current.isAfter(endDate)) {
            double dailyTrimp = dailyTrimpMap.getOrDefault(current, 0.0);

            // Fórmulas de acumulación y decaimiento exponencial
            ctl = ctl + alphaCtl * (dailyTrimp - ctl);
            ctl28 = ctl28 + alphaCtl28 * (dailyTrimp - ctl28);
            atl = atl + alphaAtl * (dailyTrimp - atl);

            double tsb = ctl - atl;
            // A:C Ratio instantáneo = ATL(7d) / CTL(28d). Se usa la ventana estándar del
            // ACWR, no el CTL de 42 días que se muestra como fitness en la propia curva.
            double acwr = (ctl28 > 0) ? (atl / ctl28) : 0.0;

            // 4. Solo guardamos en el resultado los días dentro del rango solicitado por el usuario
            if (!current.isBefore(startDate)) {
                pmcSeries.add(new PmcPointDto(
                        current,
                        roundTwoDecimals(dailyTrimp),
                        roundTwoDecimals(ctl),
                        roundTwoDecimals(atl),
                        roundTwoDecimals(tsb),
                        roundTwoDecimals(acwr),
                        roundTwoDecimals(ctl28)
                ));
            }

            current = current.plusDays(1);
        }

        return pmcSeries;
    }

    // Método utilitario para redondear a 2 decimales (ej. 1.31)
    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public EddingtonStatsDto calculateEddingtonNumber(Long userId) {
        // Agrupa distancia total por día redondeada a entero (en km)
        List<Integer> dailyDistancesKm = activityRepository.findDailyDistancesKmDescending(userId);

        int eddingtonNumber = 0;
        for (int i = 0; i < dailyDistancesKm.size(); i++) {
            int targetKm = i + 1;
            if (dailyDistancesKm.get(i) >= targetKm) {
                eddingtonNumber = targetKm;
            } else {
                break;
            }
        }

        int nextE = eddingtonNumber + 1;
        long runsForNext = dailyDistancesKm.stream().filter(km -> km >= nextE).count();
        int remainingRuns = nextE - (int) runsForNext;

        return new EddingtonStatsDto(eddingtonNumber, (int) runsForNext, remainingRuns, nextE);
    }

    public AcwrStatusDto calculateCurrentAcwr(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate earliestDate = activityRepository.findEarliestActivityDate(userId).orElse(null);

        if (earliestDate == null) {
            return undeterminedAcwr(today, 0.0, 0.0, "Sin actividades para calcular el ACWR.");
        }

        EwmaLoads loads = calculateEwmaLoads(userId, earliestDate, today,
                PMC_ATL_DAYS, ACWR_CHRONIC_DAYS);
        double acute = loads.acute();
        double chronic = loads.chronic();

        // El ACWR 7:28 solo es interpretable con al menos una ventana crónica completa.
        boolean enoughHistory = !earliestDate.isAfter(today.minusDays(ACWR_MIN_HISTORY_DAYS - 1L));
        if (!enoughHistory || chronic < ACWR_MIN_CHRONIC_LOAD) {
            return undeterminedAcwr(today, acute, chronic,
                    "Historial insuficiente: el ACWR necesita al menos "
                            + ACWR_MIN_HISTORY_DAYS + " días de datos.");
        }

        double ratio = acute / chronic;

        String zone;
        String recommendation;

        if (ratio < 0.80) {
            zone = "UNDERTRAINING";
            recommendation = "Carga baja (Sub-entrenamiento). Riesgo de pérdida de condición física.";
        } else if (ratio <= 1.30) {
            zone = "SWEET_SPOT";
            recommendation = "Zona Dulce (0.8 - 1.3). Carga óptima que minimiza el riesgo de lesión.";
        } else if (ratio <= 1.50) {
            zone = "HIGH_RISK";
            recommendation = "Atención: Incremento rápido de carga. Monitorear fatiga muscular.";
        } else {
            zone = "DANGER_ZONE";
            recommendation = "Peligro (> 1.5): Pico de carga excesivo. Riesgo de lesión elevado.";
        }

        return new AcwrStatusDto(today,
                roundTwoDecimals(acute),
                roundTwoDecimals(chronic),
                roundTwoDecimals(ratio),
                zone,
                recommendation);
    }

    /**
     * ATL y CTL con suavizado exponencial sobre la serie diaria de TRIMP.
     *
     * <p>Se procesan días calendario contiguos (los descansos cuentan como carga 0) y se
     * arranca {@value #PMC_WARMUP_DAYS} días antes de la primera actividad para que el EWMA
     * llegue estabilizado al periodo consultado.
     *
     * @return cargas aguda y crónica sin redondear
     */
    private EwmaLoads calculateEwmaLoads(Long userId, LocalDate startDate, LocalDate endDate,
                                         double acuteDays, double chronicDays) {
        LocalDate warmUpStartDate = startDate.minusDays(PMC_WARMUP_DAYS);

        Map<LocalDate, Double> dailyTrimpMap = activityRepository
                .findDailyTrimpSum(userId, warmUpStartDate, endDate)
                .stream()
                .collect(Collectors.toMap(
                        DailyTrimpProjection::getDate,
                        DailyTrimpProjection::getTotalTrimp,
                        Double::sum));

        double alphaAcute = 1.0 - Math.exp(-1.0 / acuteDays);
        double alphaChronic = 1.0 - Math.exp(-1.0 / chronicDays);
        double acute = 0.0;
        double chronic = 0.0;

        for (LocalDate current = warmUpStartDate; !current.isAfter(endDate); current = current.plusDays(1)) {
            double dailyTrimp = dailyTrimpMap.getOrDefault(current, 0.0);
            acute += alphaAcute * (dailyTrimp - acute);
            chronic += alphaChronic * (dailyTrimp - chronic);
        }

        return new EwmaLoads(acute, chronic);
    }

    private AcwrStatusDto undeterminedAcwr(LocalDate date, double acute, double chronic,
                                           String recommendation) {
        return new AcwrStatusDto(date,
                roundTwoDecimals(acute),
                roundTwoDecimals(chronic),
                0.0,
                "UNDETERMINED",
                recommendation);
    }

    private record EwmaLoads(double acute, double chronic) {
    }
}
