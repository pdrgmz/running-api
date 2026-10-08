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
@Schema(description = "Resumen de split por kilómetro")
public class SplitDto {

    @Schema(description = "Número de kilómetro", example = "1")
    private Integer km;

    @Schema(description = "Distancia del split en metros", example = "1000.0")
    private Double distanceMeters;

    @Schema(description = "Tiempo del split en segundos", example = "300")
    private Long timeSeconds;

    @Schema(description = "Paso/Pace en min/km", example = "5.00")
    private String paceMinPerKm;

    @Schema(description = "Frecuencia cardíaca media en el split (bpm)", example = "155")
    private Integer avgHeartRate;
}
