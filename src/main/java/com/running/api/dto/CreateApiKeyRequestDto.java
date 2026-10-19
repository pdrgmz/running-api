package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos para crear una clave de API")
public class CreateApiKeyRequestDto {

    @Schema(description = "Nombre descriptivo para identificar la clave", example = "n8n",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String name;

    @Schema(description = "Días de vigencia de la clave. Si se omite, la clave no caduca", example = "365")
    @Min(value = 1, message = "La vigencia debe ser de al menos 1 día")
    @Max(value = 3650, message = "La vigencia no puede superar los 3650 días")
    private Integer expiresInDays;
}
