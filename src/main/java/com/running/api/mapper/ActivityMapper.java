package com.running.api.mapper;

import com.running.api.dto.FitActivityDTO;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;

@Component
public class ActivityMapper {

    public Activity toEntity(FitActivityDTO fitDTO, String backupFilePath) {
        if (fitDTO == null) {
            return null;
        }

        LocalDateTime startTime = fitDTO.getTimestamp();

        // Generar un ID único basado en el inicio de la actividad (o un UUID como fallback)
        String activityId = (startTime != null) 
                ? startTime.toString() 
                : UUID.randomUUID().toString();

        Activity activity = Activity.builder()
                .id(activityId)
                .name("Carrera del " + (startTime != null ? startTime.toLocalDate() : "Día"))
                .startTime(startTime)
                .distanceMeters(fitDTO.getTotalDistanceMeters())
                .totalTimeSeconds(fitDTO.getTotalTimerTimeSeconds())
                .elevationGain(fitDTO.getElevationGain())
                .elevationLoss(fitDTO.getElevationLoss())
                .totalCalories(fitDTO.getTotalCalories())
                .maxCadence(fitDTO.getMaxCadence())
                .trainingLoad(fitDTO.getTrainingLoad())
                .vamKmH(fitDTO.getVamKmH())
                .vo2MaxEstimated(fitDTO.getVo2MaxEstimated())
                .sourceFileType("fit")
                .backupFilePath(backupFilePath)
                .trackpoints(new ArrayList<>())
                .build();

        List<FitActivityDTO.TrackPoint> dtosTrackpoints = fitDTO.getTrackPoints();

        if (dtosTrackpoints != null && !dtosTrackpoints.isEmpty()) {

            // 1. Convertir los trackpoints y asociarlos a la entidad principal
            for (FitActivityDTO.TrackPoint tpDTO : dtosTrackpoints) {
                ActivityTrackpoint trackpoint = ActivityTrackpoint.builder()
                        .timestamp(tpDTO.getTimestamp())
                        .latitude(tpDTO.getLatitude())
                        .longitude(tpDTO.getLongitude())
                        .speed(tpDTO.getSpeed() != null ? tpDTO.getSpeed().doubleValue() : null)
                        .heartRate(tpDTO.getHeartRate())
                        .altitudeMeters(tpDTO.getAltitudeMeters())
                        .distanceMeters(tpDTO.getDistanceMeters())
                        .cadence(tpDTO.getCadence())
                        .build();

                activity.addTrackpoint(trackpoint);
            }

            // 2. Cálculo o fallback de métricas agregadas
            populateDerivedMetrics(activity, dtosTrackpoints);
        }

        return activity;
    }

    private void populateDerivedMetrics(Activity activity, List<FitActivityDTO.TrackPoint> trackpoints) {

        // --- VELOCIDAD Y PACE / RITMO ---
        OptionalDouble maxSpeedOpt = trackpoints.stream()
                .filter(tp -> tp.getSpeed() != null && tp.getSpeed() > 0)
                .mapToDouble(FitActivityDTO.TrackPoint::getSpeed)
                .max();

        if (maxSpeedOpt.isPresent()) {
            double maxSpeedMs = maxSpeedOpt.getAsDouble();
            activity.setMaxSpeed(maxSpeedMs);
            // Ritmo máximo (más rápido) en min/km -> (1000m / velocidad m/s) / 60
            activity.setMaxPaceMinPerKm(speedMsToPaceMinPerKm(maxSpeedMs));
        }

        // Velocidad Promedio (calculada desde distancia total y tiempo si no venía)
        if (activity.getAvgSpeed() == null && activity.getDistanceMeters() != null 
                && activity.getTotalTimeSeconds() != null && activity.getTotalTimeSeconds() > 0) {
            activity.setAvgSpeed(activity.getDistanceMeters() / activity.getTotalTimeSeconds());
        }

        if (activity.getAvgSpeed() != null && activity.getAvgSpeed() > 0) {
            activity.setAvgPaceMinPerKm(speedMsToPaceMinPerKm(activity.getAvgSpeed()));
        }

        // --- FRECUENCIA CARDÍACA ---
        OptionalDouble avgHrOpt = trackpoints.stream()
                .filter(tp -> tp.getHeartRate() != null && tp.getHeartRate() > 0)
                .mapToInt(FitActivityDTO.TrackPoint::getHeartRate)
                .average();

        if (activity.getAvgHeartRate() == null && avgHrOpt.isPresent()) {
            activity.setAvgHeartRate((int) Math.round(avgHrOpt.getAsDouble()));
        }

        OptionalInt maxHrOpt = trackpoints.stream()
                .filter(tp -> tp.getHeartRate() != null && tp.getHeartRate() > 0)
                .mapToInt(FitActivityDTO.TrackPoint::getHeartRate)
                .max();

        if (activity.getMaxHeartRate() == null && maxHrOpt.isPresent()) {
            activity.setMaxHeartRate(maxHrOpt.getAsInt());
        }

        // --- CADENCIA ---
        OptionalDouble avgCadOpt = trackpoints.stream()
                .filter(tp -> tp.getCadence() != null && tp.getCadence() > 0)
                .mapToInt(FitActivityDTO.TrackPoint::getCadence)
                .average();

        if (activity.getAvgCadence() == null && avgCadOpt.isPresent()) {
            activity.setAvgCadence((int) Math.round(avgCadOpt.getAsDouble()));
        }

        if (activity.getMaxCadence() == null) {
            // Si no vino del session, calcular desde trackpoints
            OptionalInt maxCadOpt = trackpoints.stream()
                    .filter(tp -> tp.getCadence() != null && tp.getCadence() > 0)
                    .mapToInt(FitActivityDTO.TrackPoint::getCadence)
                    .max();

            if (maxCadOpt.isPresent()) {
                activity.setMaxCadence(maxCadOpt.getAsInt());
            }
        }
    }

    /**
     * Convierte velocidad de m/s a ritmo en min/km (formato decimal: p. ej., 4.5 = 4:30 min/km).
     */
    private Double speedMsToPaceMinPerKm(double speedMs) {
        if (speedMs <= 0) return null;
        double paceSecondsPerKm = 1000.0 / speedMs;
        double minutes = paceSecondsPerKm / 60.0;
        return Math.round(minutes * 100.0) / 100.0; // Redondeado a 2 decimales
    }
}