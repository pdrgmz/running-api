package com.running.api.service;

import com.running.api.dto.*;
import com.running.api.exception.ResourceNotFoundException;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import java.util.*;

import com.running.api.dto.DaytimeStatsDto;
import com.running.api.dto.EddingtonStatsDto;
import com.running.api.dto.PmcPointDto;
import com.running.api.dto.StreakStatsDto;
import com.running.api.dto.WeekdayStatsDto;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.projection.DailyTrimpProjection;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ActivityRepository activityRepository;

    @Value("${app.user.max-hr:185}")
    private Integer defaultMaxHr;

    // 1. Splits por Kilómetro
    public List<SplitDto> calculateSplits(String activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada con id: " + activityId));

        List<ActivityTrackpoint> points = activity.getTrackpoints();
        List<SplitDto> splits = new ArrayList<>();

        if (points.isEmpty()) return splits;

        int currentKm = 1;
        double targetMeters = 1000.0;
        int startIndex = 0;

        for (int i = 0; i < points.size(); i++) {            
            ActivityTrackpoint pt = points.get(i);

            if (pt.getDistanceMeters() != null && pt.getDistanceMeters() >= targetMeters) {
                splits.add(buildSplitDto(currentKm, points.subList(startIndex, i + 1)));
                currentKm++;
                targetMeters += 1000.0;
                startIndex = i;
            }
        }

        if (startIndex < points.size() - 1) {
            splits.add(buildSplitDto(currentKm, points.subList(startIndex, points.size())));
        }

        return splits;
    }

    private SplitDto buildSplitDto(int km, List<ActivityTrackpoint> segment) {
        double startDist = segment.get(0).getDistanceMeters();
        double endDist = segment.get(segment.size() - 1).getDistanceMeters();
        double distMeters = endDist - startDist;

        long timeSeconds = java.time.Duration.between(
                segment.get(0).getTimestamp(),
                segment.get(segment.size() - 1).getTimestamp()
        ).getSeconds();

        double avgSpeed = timeSeconds > 0 ? distMeters / timeSeconds : 0.0;
        double paceMinPerKm = avgSpeed > 0 ? (1000.0 / (avgSpeed * 60.0)) : 0.0;

        int sumHr = 0, countHr = 0;
        for (ActivityTrackpoint pt : segment) {
            if (pt.getHeartRate() != null && pt.getHeartRate() > 0) {
                sumHr += pt.getHeartRate();
                countHr++;
            }
        }

        return SplitDto.builder()
                .km(km)
                .distanceMeters(Math.round(distMeters * 100.0) / 100.0)
                .timeSeconds(timeSeconds)
                .paceMinPerKm(String.format("%.2f", paceMinPerKm))
                .avgHeartRate(countHr > 0 ? (sumHr / countHr) : 0)
                .build();
    }

    // 2. Zonas de Frecuencia Cardíaca (Z1 a Z5)
    public HrZonesResponseDto calculateHrZones(String activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada con id: " + activityId));

        int maxHr = activity.getMaxHeartRate() != null ? activity.getMaxHeartRate() : defaultMaxHr;

        int z1 = 0, z2 = 0, z3 = 0, z4 = 0, z5 = 0;

        for (ActivityTrackpoint pt : activity.getTrackpoints()) {
            if (pt.getHeartRate() == null || pt.getHeartRate() == 0) continue;
            double pct = (double) pt.getHeartRate() / maxHr;

            if (pct < 0.60) z1++;
            else if (pct < 0.70) z2++;
            else if (pct < 0.80) z3++;
            else if (pct < 0.90) z4++;
            else z5++;
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

    // 3. Cardiac Drift (Desacople Aeróbico) con guardas contra división por cero
    public CardiacDriftResponseDto calculateCardiacDrift(String activityId) {
        Activity activity = activityRepository.findById(activityId)
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
        double totalDist = segment.get(segment.size() - 1).getDistanceMeters() - segment.get(0).getDistanceMeters();
        long timeSec = java.time.Duration.between(segment.get(0).getTimestamp(), segment.get(segment.size() - 1).getTimestamp()).getSeconds();
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
    public TrainingLoadResponseDto calculateTrainingLoad(String activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada con id: " + activityId));
        return computeTrainingLoadDetails(activity);
    }

    public TrainingLoadResponseDto computeTrainingLoadDetails(Activity activity) {
        List<ActivityTrackpoint> trackpoints = activity.getTrackpoints();
        int maxHr = activity.getMaxHeartRate() != null ? activity.getMaxHeartRate() : defaultMaxHr;
        int restHr = 60;

        double edwardsTrimp = 0.0;
        double totalSecondsWithHr = 0.0;

        if (trackpoints != null && !trackpoints.isEmpty()) {
            ActivityTrackpoint prevPt = null;
            for (ActivityTrackpoint pt : trackpoints) {
                if (pt.getHeartRate() != null && pt.getHeartRate() > 0) {
                    double dtSec = 1.0;
                    if (prevPt != null && prevPt.getTimestamp() != null && pt.getTimestamp() != null) {
                        long diff = java.time.Duration.between(prevPt.getTimestamp(), pt.getTimestamp()).getSeconds();
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
    public Vo2MaxVamResponseDto calculateVo2MaxAndVam(String activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada con id: " + activityId));
        return computeVo2MaxAndVamDetails(activity);
    }

    public Vo2MaxVamResponseDto computeVo2MaxAndVamDetails(Activity activity) {
        List<ActivityTrackpoint> trackpoints = activity.getTrackpoints();
        int maxHr = activity.getMaxHeartRate() != null ? activity.getMaxHeartRate() : defaultMaxHr;
        int restHr = 60;

        Double estimatedVo2Max = null;

        if (trackpoints != null && !trackpoints.isEmpty()) {
            List<Double> vo2Samples = new ArrayList<>();
            for (ActivityTrackpoint pt : trackpoints) {
                Double speed = pt.getSpeed();
                Integer hr = pt.getHeartRate();
                if (speed != null && speed > 1.5 && speed < 15.0 && hr != null && hr > (restHr + 30)) {
                    double speedMPerMin = speed * 60.0;
                    double vo2Cost = (speedMPerMin * 0.2) + 3.5;
                    double fractionHrr = Math.max(0.2, Math.min(1.0, (double) (hr - restHr) / (maxHr - restHr)));
                    double vo2MaxEst = vo2Cost / fractionHrr;
                    if (vo2MaxEst >= 20.0 && vo2MaxEst <= 85.0) {
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
            if (avgSpeed != null && avgSpeed > 1.5 && avgHr != null && avgHr > restHr) {
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

        if (estimatedVo2Max == null || estimatedVo2Max < 15.0) {
            estimatedVo2Max = 35.0;
        }

        double finalVo2Max = Math.round(estimatedVo2Max * 10.0) / 10.0;
        double vamKmH = Math.round((finalVo2Max / 3.5) * 100.0) / 100.0;

        String fitnessCategory;
        if (finalVo2Max < 35.0) fitnessCategory = "Inicial / Principiante";
        else if (finalVo2Max < 42.0) fitnessCategory = "Recreativo / Medio";
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

        if (activity.getTrainingLoad() == null) {
            TrainingLoadResponseDto tlDetails = computeTrainingLoadDetails(activity);
            Double tl = tlDetails.getTrainingLoad();
            activity.setTrainingLoad(tl);
            modified = true;
        }

        if (activity.getVo2MaxEstimated() == null || activity.getVamKmH() == null) {
            Vo2MaxVamResponseDto vo2Details = computeVo2MaxAndVamDetails(activity);
            Double vo2 = vo2Details.getVo2MaxEstimated();
            Double vam = vo2Details.getVamKmH();
            activity.setVo2MaxEstimated(vo2);
            activity.setVamKmH(vam);
            modified = true;
        }

        if ((activity.getElevationGain() == null || (activity.getElevationGain() == 0.0 && (activity.getElevationLoss() == null || activity.getElevationLoss() == 0.0)))
                && points != null && !points.isEmpty()) {
            double calcGain = 0.0;
            double calcLoss = 0.0;
            double prevAlt = -999.0;
            for (ActivityTrackpoint pt : points) {
                if (pt.getAltitudeMeters() != null && pt.getAltitudeMeters() != -999.0) {
                    if (prevAlt != -999.0) {
                        double diff = pt.getAltitudeMeters() - prevAlt;
                        if (Math.abs(diff) > 0.01) {
                            if (diff > 0) calcGain += diff;
                            else calcLoss += Math.abs(diff);
                            prevAlt = pt.getAltitudeMeters();
                        }
                    } else {
                        prevAlt = pt.getAltitudeMeters();
                    }
                }
            }
            if (calcGain > 0.0 || calcLoss > 0.0) {
                activity.setElevationGain(Math.round(calcGain * 10.0) / 10.0);
                activity.setElevationLoss(Math.round(calcLoss * 10.0) / 10.0);
                modified = true;
            }
        }

        return modified;
    }

    public List<PmcPointDto> calculatePmcHistory(LocalDate startDate, LocalDate endDate) {
        // 1. Obtener la suma diaria de TRIMP acumulada por fecha
        Map<LocalDate, Double> dailyTrimpMap = activityRepository.findDailyTrimpSum(startDate.minusDays(42), endDate)
            .stream()
            .collect(Collectors.toMap(DailyTrimpProjection::getDate, DailyTrimpProjection::getTotalTrimp));

        List<PmcPointDto> pmcSeries = new ArrayList<>();
        double ctl = 0.0;
        double atl = 0.0;

        double alphaCtl = 1.0 - Math.exp(-1.0 / 42.0);
        double alphaAtl = 1.0 - Math.exp(-1.0 / 7.0);

        LocalDate current = startDate.minusDays(42); // Warm-up para estabilizar la curva
        while (!current.isAfter(endDate)) {
            double dailyTrimp = dailyTrimpMap.getOrDefault(current, 0.0);

            ctl = ctl + alphaCtl * (dailyTrimp - ctl);
            atl = atl + alphaAtl * (dailyTrimp - atl);
            double tsb = ctl - atl;
            double acwr = ctl > 0 ? (atl / ctl) : 0.0;

            if (!current.isBefore(startDate)) {
                pmcSeries.add(new PmcPointDto(current, dailyTrimp, ctl, atl, tsb, acwr));
            }

            current = current.plusDays(1);
        }

        return pmcSeries;
    }

    public EddingtonStatsDto calculateEddingtonNumber() {
        // Agrupa distancia total por día redondeada a entero (en km)
        List<Integer> dailyDistancesKm = activityRepository.findDailyDistancesKmDescending();

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

    public AcwrStatusDto calculateCurrentAcwr() {

        LocalDate today = LocalDate.now();
        // Obtenemos los últimos puntos PMC para evaluar el día de hoy
        List<PmcPointDto> pmcHistory = calculatePmcHistory(today.minusDays(1), today);

        if (pmcHistory.isEmpty()) {
            return new AcwrStatusDto(today, 0.0, 0.0, 0.0, "UNDETERMINED", "Sin datos suficientes para calcular la carga.");
        }

        PmcPointDto latest = pmcHistory.get(pmcHistory.size() - 1);
        double acwr = latest.acwr();

        String zone;
        String recommendation;

        if (acwr < 0.80) {
            zone = "UNDERTRAINING";
            recommendation = "Carga baja (Sub-entrenamiento). Riesgo de pérdida de condición física si se mantiene prolongadamente.";
        } else if (acwr <= 1.30) {
            zone = "SWEET_SPOT";
            recommendation = "Zona Dulce (0.8 - 1.3). Carga óptima que minimiza el riesgo de lesión y maximiza la adaptación.";
        } else if (acwr <= 1.50) {
            zone = "HIGH_RISK";
            recommendation = "Atención: Incremento rápido de carga. Monitorear fatiga muscular.";
        } else {
            zone = "DANGER_ZONE";
            recommendation = "Peligro (> 1.5): Pico de carga excesivo. Riesgo de lesión significativamente elevado. Se recomienda descargar.";
}

    return new AcwrStatusDto(latest.date(), latest.atl(), latest.ctl(), acwr, zone, recommendation);
}

}
