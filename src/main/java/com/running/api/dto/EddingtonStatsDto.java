package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estadísticas del Número de Eddington (E)")
public record EddingtonStatsDto(
    @Schema(description = "Número de Eddington actual (E km recorridos en al menos E días distintos)", example = "15")
    int eddingtonNumber,

    @Schema(description = "Días acumulados con distancia mayor o igual al número actual E", example = "18")
    int currentCountForNumber,

    @Schema(description = "Cantidad de carreras de al menos (E + 1) km necesarias para subir de nivel", example = "3")
    int runsNeededForNext,

    @Schema(description = "Siguiente meta de distancia en kilómetros (E + 1)", example = "16")
    int nextTargetKm
) {}
