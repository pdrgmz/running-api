package com.running.api.mcp.tools;

import com.running.api.dto.RecordResponseDto;
import com.running.api.dto.SummaryStatsDto;
import com.running.api.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class StatsTools {

    private static final Set<String> VALID_PERIODS = Set.of(
        "THIS_MONTH", "THIS_YEAR", "LAST_MONTH", "LAST_YEAR",
        "THIS_WEEK", "LAST_WEEK", "ALL_TIME"
    );

    private final StatsService statsService;

    public String getSummaryStats() {
        try {
            SummaryStatsDto stats = statsService.getSummary(null, null);
            if (stats == null) {
                return "No hay estadísticas disponibles";
            }
            return String.format("""
                📊 Resumen Global de Estadísticas
                
                Total Actividades: %d
                Distancia Total: %.2f km
                Tiempo Total: %s
                Elevación Total: %.1f m
                Calorías Totales: %d
                FC Máxima Global: %d bpm
                Distancia Promedio por Carrera: %.2f km
                """,
                stats.getTotalActivities(),
                stats.getTotalDistanceKm(),
                stats.getTotalTimeFormatted(),
                stats.getTotalElevationGain(),
                stats.getTotalCalories(),
                stats.getMaxHeartRateGlobal(),
                stats.getAvgDistanceKm()
            );
        } catch (Exception e) {
            return "Error obteniendo estadísticas globales: " + e.getMessage();
        }
    }

    public String getPersonalRecords() {
        try {
            List<RecordResponseDto> records = statsService.getPersonalRecords();
            if (records == null || records.isEmpty()) {
                return "No hay marcas personales registradas.";
            }
            StringBuilder sb = new StringBuilder("🏆 Marcas Personales (PB)\n\n");
            for (RecordResponseDto r : records) {
                sb.append(String.format("- %s: %s (Ritmo: %s min/km) | Fecha: %s | ID: %s\n",
                    r.getDistanceCategory(),
                    r.getBestTimeFormatted(),
                    r.getAvgPaceMinPerKm(),
                    r.getActivityDate(),
                    r.getActivityId()
                ));
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error obteniendo marcas personales: " + e.getMessage();
        }
    }

    public String getSummaryByPeriod(String period) {
        if (period == null || period.isBlank()) {
            return "Error: El parámetro 'period' es requerido";
        }

        String normalizedPeriod = period.trim().toUpperCase();

        // Si es un año (4 dígitos), usar directamente
        if (normalizedPeriod.matches("\\d{4}")) {
            return getSummaryByYear(normalizedPeriod);
        }

        // Validar período predefinido
        if (!VALID_PERIODS.contains(normalizedPeriod)) {
            return String.format("Error: Período no válido: '%s'. Períodos válidos: %s o un año (ej: 2024)",
                period, String.join(", ", VALID_PERIODS));
        }

        try {
            SummaryStatsDto stats = statsService.getSummary(normalizedPeriod, null);
            if (stats == null) {
                return "No hay estadísticas disponibles para el período: " + period;
            }
            return String.format("""
                📊 Estadísticas del período: %s
                
                Total Actividades: %d
                Distancia Total: %.2f km
                Tiempo Total: %s
                Elevación Total: %.1f m
                """,
                normalizedPeriod,
                stats.getTotalActivities(),
                stats.getTotalDistanceKm(),
                stats.getTotalTimeFormatted(),
                stats.getTotalElevationGain()
            );
        } catch (Exception e) {
            return "Error obteniendo estadísticas del período " + period + ": " + e.getMessage();
        }
    }

    private String getSummaryByYear(String year) {
        try {
            int yearInt = Integer.parseInt(year);
            if (yearInt < 2000 || yearInt > 2100) {
                return "Error: Año fuera de rango válido (2000-2100): " + year;
            }

            SummaryStatsDto stats = statsService.getSummary(null, yearInt);
            if (stats == null) {
                return "No hay estadísticas disponibles para el año: " + year;
            }
            return String.format("""
                📊 Estadísticas del año: %s
                
                Total Actividades: %d
                Distancia Total: %.2f km
                Tiempo Total: %s
                Elevación Total: %.1f m
                """,
                year,
                stats.getTotalActivities(),
                stats.getTotalDistanceKm(),
                stats.getTotalTimeFormatted(),
                stats.getTotalElevationGain()
            );
        } catch (NumberFormatException e) {
            return "Error: Formato de año no válido: " + year;
        } catch (Exception e) {
            return "Error obteniendo estadísticas del año " + year + ": " + e.getMessage();
        }
    }
}
