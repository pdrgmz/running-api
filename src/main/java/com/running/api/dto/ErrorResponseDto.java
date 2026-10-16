package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Estructura estándar de error devuelta por la API")
public class ErrorResponseDto {

    @Schema(description = "Momento en que ocurrió el error", example = "2026-10-15T10:30:00")
    private LocalDateTime timestamp;

    @Schema(description = "Código de estado HTTP", example = "404")
    private int status;

    @Schema(description = "Frase de estado HTTP", example = "Not Found")
    private String error;

    @Schema(description = "Mensaje descriptivo del error",
            example = "Actividad no encontrada: 2026-09-05T07:00:00Z")
    private String message;

    @Schema(description = "Ruta de la petición que provocó el error",
            example = "/api/v1/activities/2026-09-05T07:00:00Z")
    private String path;
}
