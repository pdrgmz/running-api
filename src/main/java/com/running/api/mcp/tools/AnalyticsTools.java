package com.running.api.mcp.tools;

import com.running.api.dto.AcwrStatusDto;
import com.running.api.dto.HrZonesResponseDto;
import com.running.api.dto.SplitDto;
import com.running.api.dto.TrainingLoadResponseDto;
import com.running.api.dto.Vo2MaxVamResponseDto;
import com.running.api.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AnalyticsTools {

    private final AnalyticsService analyticsService;

    public String getSplits(String activityId) {
        try {
            List<SplitDto> splits = analyticsService.calculateSplits(activityId);
            if (splits == null || splits.isEmpty()) {
                return "No hay splits disponibles para la actividad: " + activityId;
            }
            StringBuilder sb = new StringBuilder("🏃 Splits por Kilómetro\n\n");
            for (SplitDto s : splits) {
                sb.append(String.format("- Km %d: %s (Ritmo: %s min/km) | FC: %d bpm\n",
                        s.getKm(),
                        formatTime(s.getTimeSeconds()),
                        s.getPaceMinPerKm(),
                        s.getAvgHeartRate()));
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error obteniendo splits para la actividad " + activityId + ": " + e.getMessage();
        }
    }

    public String getHrZones(String activityId) {
        try {
            HrZonesResponseDto zones = analyticsService.calculateHrZones(activityId);
            if (zones == null || zones.getZones() == null) {
                return "No hay datos de zonas de FC para la actividad: " + activityId;
            }
            var z = zones.getZones();
            return String.format("""
                    ❤️ Zonas de Frecuencia Cardíaca

                    FC Máxima usada: %d bpm

                    Z1 Recovery: %d puntos (%.1f%%)
                    Z2 Endurance: %d puntos (%.1f%%)
                    Z3 Tempo: %d puntos (%.1f%%)
                    Z4 Threshold: %d puntos (%.1f%%)
                    Z5 Anaerobic: %d puntos (%.1f%%)
                    """,
                    zones.getMaxHrUsed(),
                    z.getZ1Recovery().getSeconds(), z.getZ1Recovery().getPercentage(),
                    z.getZ2Endurance().getSeconds(), z.getZ2Endurance().getPercentage(),
                    z.getZ3Tempo().getSeconds(), z.getZ3Tempo().getPercentage(),
                    z.getZ4Threshold().getSeconds(), z.getZ4Threshold().getPercentage(),
                    z.getZ5Anaerobic().getSeconds(), z.getZ5Anaerobic().getPercentage());
        } catch (Exception e) {
            return "Error obteniendo zonas de FC para la actividad " + activityId + ": " + e.getMessage();
        }
    }

    public String getTrainingLoad(String activityId) {
        try {
            TrainingLoadResponseDto tl = analyticsService.calculateTrainingLoad(activityId);
            if (tl == null) {
                return "No hay datos de carga de entrenamiento para la actividad: " + activityId;
            }
            return String.format("""
                    💪 Carga de Entrenamiento

                    Carga Principal: %.1f
                    Edwards TRIMP: %.1f
                    Banister TRIMP: %.1f
                    HRSS: %.1f
                    Nivel de Esfuerzo: %s
                    """,
                    tl.getTrainingLoad(),
                    tl.getEdwardsTrimp(),
                    tl.getBanisterTrimp(),
                    tl.getHrss(),
                    tl.getEffortLevel());
        } catch (Exception e) {
            return "Error obteniendo carga de entrenamiento para la actividad " + activityId + ": " + e.getMessage();
        }
    }

    public String getVo2Max(String activityId) {
        try {
            Vo2MaxVamResponseDto vo2 = analyticsService.calculateVo2MaxAndVam(activityId);
            if (vo2 == null) {
                return "No hay datos de VO2Max para la actividad: " + activityId;
            }
            return String.format("""
                    🫁 VO2Max y VAM

                    VO2Max Estimado: %.1f
                    VAM: %.2f km/h
                    Categoría de Fitness: %s

                    Predicciones de Carrera:
                    - 5k: %s
                    - 10k: %s
                    - 21k: %s
                    - 42k: %s
                    """,
                    vo2.getVo2MaxEstimated(),
                    vo2.getVamKmH(),
                    vo2.getFitnessCategory(),
                    vo2.getPredictedRaceTimes().getK5(),
                    vo2.getPredictedRaceTimes().getK10(),
                    vo2.getPredictedRaceTimes().getK21(),
                    vo2.getPredictedRaceTimes().getK42());
        } catch (Exception e) {
            return "Error obteniendo VO2Max para la actividad " + activityId + ": " + e.getMessage();
        }
    }

    public String getAcwrStatus() {
        try {
            AcwrStatusDto acwr = analyticsService.calculateCurrentAcwr();
            if (acwr == null) {
                return "No hay datos de ACWR disponibles";
            }
            return String.format("""
                    ⚖️ Estado ACWR (Riesgo de Lesión)

                    Fecha: %s
                    Carga Aguda (7 días): %.2f
                    Carga Crónica (42 días): %.2f
                    Ratio ACWR: %.2f
                    Zona: %s

                    Recomendación: %s
                    """,
                    acwr.date(),
                    acwr.acuteLoad(),
                    acwr.chronicLoad(),
                    acwr.acwr(),
                    acwr.zone(),
                    acwr.recommendation());
        } catch (Exception e) {
            return "Error obteniendo estado ACWR: " + e.getMessage();
        }
    }

    private String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return String.format("%02d:%02d:%02d", h, m, s);
        }
        return String.format("%02d:%02d", m, s);
    }
}
