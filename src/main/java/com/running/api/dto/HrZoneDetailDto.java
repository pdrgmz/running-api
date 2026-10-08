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
@Schema(description = "Detalle de tiempo y porcentaje en una zona de frecuencia cardíaca")
public class HrZoneDetailDto {

    @Schema(description = "Segundos transcurridos en esta zona", example = "350")
    private Integer seconds;

    @Schema(description = "Porcentaje de tiempo transcurrido en esta zona", example = "25.5")
    private Double percentage;
}
