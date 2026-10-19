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
@Schema(description = "Clave de API recién creada. El campo 'key' solo se muestra en esta respuesta")
public class ApiKeyCreatedResponseDto {

    @Schema(description = "ID de la clave", example = "1")
    private Long id;

    @Schema(description = "Nombre descriptivo de la clave", example = "n8n")
    private String name;

    @Schema(description = "Valor completo de la clave. Guárdalo de forma segura: no se volverá a mostrar",
            example = "rk_Xk3pQ8aBcDeFgHiJkLmNoPqRsTuVwXyZ0123456789")
    private String key;

    @Schema(description = "Primeros caracteres de la clave, para identificarla en listados", example = "rk_Xk3pQ8aB")
    private String keyPrefix;

    @Schema(description = "Fecha y hora de creación", example = "2026-10-18T09:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha y hora de caducidad (null si no caduca)", example = "2027-10-18T09:00:00")
    private LocalDateTime expiresAt;
}
