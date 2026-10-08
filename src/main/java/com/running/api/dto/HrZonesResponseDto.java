package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta del análisis de distribución de tiempo en zonas de frecuencia cardíaca")
public class HrZonesResponseDto {

    @Schema(description = "Frecuencia cardíaca máxima utilizada como referencia", example = "185")
    private Integer maxHrUsed;

    @Schema(description = "Zonas de frecuencia cardíaca Z1 a Z5")
    private HrZonesMapDto zones;
}
