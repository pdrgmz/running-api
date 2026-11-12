package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado de un borrado masivo de recomendaciones diarias")
public record DeletedRecommendationsResponseDto(
        @Schema(description = "Número de recomendaciones eliminadas", example = "3")
        int deleted
) {
}
