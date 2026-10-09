package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estadísticas agrupadas por franja horaria del día")
public record DaytimeStatsDto(
    @Schema(description = "Franja horaria (MADRUGADA, MAÑANA, TARDE, NOCHE)", example = "MAÑANA")
    String timeSlot,

    @Schema(description = "Total de carreras realizadas en la franja horaria", example = "115")
    long totalActivities,

    @Schema(description = "Porcentaje que representa sobre el total global", example = "65.5")
    double percentage,

    @Schema(description = "Ritmo promedio en min/km dentro de esta franja", example = "4.75")
    double avgPaceMinPerKm,

    @Schema(description = "Frecuencia cardíaca promedio en esta franja (bpm)", example = "148")
    double avgHeartRate
) {}
