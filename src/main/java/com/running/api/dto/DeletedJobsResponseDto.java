package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado de un borrado masivo de jobs de generación")
public record DeletedJobsResponseDto(
        @Schema(description = "Número de jobs eliminados", example = "3")
        int deleted
) {
}
