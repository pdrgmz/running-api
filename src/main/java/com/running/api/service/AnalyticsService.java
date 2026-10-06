package com.running.api.service;

import com.running.api.exception.ResourceNotFoundException;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ActivityRepository activityRepository;

    @Value("${app.user.max-hr:185}")
    private Integer defaultMaxHr;

    // 1. Splits por Kilómetro
    public List<Map<String, Object>> calculateSplits(String activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada con id: " + activityId));

        List<ActivityTrackpoint> points = activity.getTrackpoints();
        List<Map<String, Object>> splits = new ArrayList<>();

        if (points.isEmpty()) return splits;

        int currentKm = 1;
        double targetMeters = 1000.0;
        int startIndex = 0;

        for (int i = 0; i < points.size(); i++) {            
            ActivityTrackpoint pt = points.get(i);

            if (pt.getDistanceMeters() != null && pt.getDistanceMeters() >= targetMeters) {
                splits.add(buildSplitMap(currentKm, points.subList(startIndex, i + 1)));
                currentKm++;
                targetMeters += 1000.0;
                startIndex = i;
            }
        }

        if (startIndex < points.size() - 1) {
            splits.add(buildSplitMap(currentKm, points.subList(startIndex, points.size())));
        }

        return splits;
    }

    private Map<String, Object> buildSplitMap(int km, List<ActivityTrackpoint> segment) {
        
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

        return Map.of(
                "km", km,
                "distanceMeters", Math.round(distMeters * 100.0) / 100.0,
                "timeSeconds", timeSeconds,
                "paceMinPerKm", String.format("%.2f", paceMinPerKm),
                "avgHeartRate", countHr > 0 ? (sumHr / countHr) : 0
        );
    }

    // 2. Zonas de Frecuencia Cardíaca (Z1 a Z5)
    public Map<String, Object> calculateHrZones(String activityId) {
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

        return Map.of(
                "maxHrUsed", maxHr,
                "zones", Map.of(
                        "Z1_Recovery", Map.of("seconds", z1, "percentage", total > 0 ? Math.round((z1 * 100.0 / total) * 10.0) / 10.0 : 0),
                        "Z2_Endurance", Map.of("seconds", z2, "percentage", total > 0 ? Math.round((z2 * 100.0 / total) * 10.0) / 10.0 : 0),
                        "Z3_Tempo", Map.of("seconds", z3, "percentage", total > 0 ? Math.round((z3 * 100.0 / total) * 10.0) / 10.0 : 0),
                        "Z4_Threshold", Map.of("seconds", z4, "percentage", total > 0 ? Math.round((z4 * 100.0 / total) * 10.0) / 10.0 : 0),
                        "Z5_Anaerobic", Map.of("seconds", z5, "percentage", total > 0 ? Math.round((z5 * 100.0 / total) * 10.0) / 10.0 : 0)
                )
        );
    }

    // 3. Cardiac Drift (Desacople Aeróbico) con guardas contra división por cero
    public Map<String, Object> calculateCardiacDrift(String activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada con id: " + activityId));

        List<ActivityTrackpoint> points = activity.getTrackpoints();
        if (points.size() < 10) {
            return Map.of("status", "Insuficientes puntos de telemetría para calcular desacople aeróbico");
        }

        int half = points.size() / 2;
        double ratio1 = calculateHalfRatio(points.subList(0, half));
        double ratio2 = calculateHalfRatio(points.subList(half, points.size()));

        if (ratio1 <= 0.0) {
            return Map.of("status", "Datos de pulso/velocidad no válidos en la primera mitad");
        }

        double driftPct = ((ratio2 - ratio1) / ratio1) * 100.0;

        return Map.of(
                "firstHalfEfficiencyRatio", Math.round(ratio1 * 1000.0) / 1000.0,
                "secondHalfEfficiencyRatio", Math.round(ratio2 * 1000.0) / 1000.0,
                "cardiacDriftPercentage", String.format("%.2f", driftPct),
                "decouplingStatus", driftPct > 5.0 ? "Desacople significativo (Fatiga/Deshidratación)" : "Esfuerzo aeróbico estable"
        );
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
}
