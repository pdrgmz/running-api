package com.running.api.mcp.tools;

import com.running.api.model.Activity;
import com.running.api.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ActivityTools {

    private static final int MAX_ACTIVITIES_LIST = 50;

    private final ActivityRepository activityRepository;

    public String getActivityById(String id) {
        if (id == null || id.isBlank()) {
            return "Error: El parámetro 'id' es requerido";
        }
        try {
            return activityRepository.findById(id)
                .map(this::formatActivity)
                .orElse("Actividad no encontrada con ID: " + id);
        } catch (Exception e) {
            return "Error obteniendo actividad con ID " + id + ": " + e.getMessage();
        }
    }

    public String listActivities() {
        try {
            List<Activity> activities = activityRepository.findAll();
            if (activities.isEmpty()) {
                return "No hay actividades registradas.";
            }

            boolean truncated = activities.size() > MAX_ACTIVITIES_LIST;
            List<Activity> limited = truncated
                ? activities.stream().limit(MAX_ACTIVITIES_LIST).toList()
                : activities;

            StringBuilder sb = new StringBuilder();
            sb.append("📋 Actividades registradas: ").append(activities.size()).append("\n");
            if (truncated) {
                sb.append("(Mostrando primeras ").append(MAX_ACTIVITIES_LIST).append(")\n");
            }
            sb.append("\n");

            for (Activity a : limited) {
                sb.append(formatActivitySummary(a));
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error obteniendo actividades: " + e.getMessage();
        }
    }

    public String getRecentActivities() {
        try {
            List<Activity> recent = activityRepository.findByOrderByStartTimeDesc();
            if (recent.isEmpty()) {
                return "No hay actividades registradas.";
            }
            recent = recent.stream().limit(10).toList();
            StringBuilder sb = new StringBuilder("🕐 Últimas 10 actividades:\n\n");
            for (Activity a : recent) {
                sb.append(formatActivitySummary(a));
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error obteniendo actividades recientes: " + e.getMessage();
        }
    }

    private String formatActivity(Activity a) {
        return String.format("""
            🏃 Actividad: %s
            
            ID: %s
            Nombre: %s
            Fecha: %s
            Distancia: %.2f km
            Tiempo: %.0f min
            Velocidad Promedio: %.2f km/h
            FC Máxima: %s bpm
            FC Promedio: %s bpm
            Elevación: +%.0f m / -%.0f m
            Calorías: %d
            
            """,
            a.getName(),
            a.getId(),
            a.getName(),
            a.getStartTime(),
            a.getDistanceMeters() / 1000.0,
            a.getTotalTimeSeconds() / 60.0,
            a.getAvgSpeed() * 3.6,
            a.getMaxHeartRate() != null ? a.getMaxHeartRate() : "N/A",
            a.getAvgHeartRate() != null ? a.getAvgHeartRate() : "N/A",
            a.getElevationGain(),
            a.getElevationLoss(),
            a.getTotalCalories()
        );
    }

    private String formatActivitySummary(Activity a) {
        return String.format("- ID: %s | Fecha: %s | Distancia: %.2f km | Tiempo: %.0f min\n",
            a.getId(), a.getStartTime(),
            a.getDistanceMeters() / 1000.0, a.getTotalTimeSeconds() / 60.0);
    }
}
