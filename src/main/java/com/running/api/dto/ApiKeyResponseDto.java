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
@Schema(description = "Metadatos de una clave de API (sin su valor secreto)")
public class ApiKeyResponseDto {

    @Schema(description = "ID de la clave", example = "1")
    private Long id;

    @Schema(description = "Nombre descriptivo de la clave", example = "n8n")
    private String name;

    @Schema(description = "Primeros caracteres de la clave, para identificarla", example = "rk_Xk3pQ8aB")
    private String keyPrefix;

    @Schema(description = "Fecha y hora de creación", example = "2026-10-18T09:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha y hora de caducidad (null si no caduca)", example = "2027-10-18T09:00:00")
    private LocalDateTime expiresAt;

    @Schema(description = "Última vez que la clave autenticó una petición", example = "2026-10-18T10:30:00")
    private LocalDateTime lastUsedAt;
}
