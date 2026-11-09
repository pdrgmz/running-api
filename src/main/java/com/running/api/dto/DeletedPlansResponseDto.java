package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado de un borrado masivo de planes semanales")
public record DeletedPlansResponseDto(
        @Schema(description = "Número de versiones de plan eliminadas", example = "2")
        int deleted
) {
}
