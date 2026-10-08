package com.running.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Predicciones de tiempos estimados para distancias clave de carrera")
public class RacePredictionsDto {

    @JsonProperty("5k")
    @Schema(description = "Tiempo estimado para 5k (HH:MM:SS o MM:SS)", example = "00:22:30")
    private String k5;

    @JsonProperty("10k")
    @Schema(description = "Tiempo estimado para 10k (HH:MM:SS o MM:SS)", example = "00:46:15")
    private String k10;

    @JsonProperty("21k")
    @Schema(description = "Tiempo estimado para 21k (HH:MM:SS)", example = "01:42:10")
    private String k21;

    @JsonProperty("42k")
    @Schema(description = "Tiempo estimado para 42k (HH:MM:SS)", example = "03:35:00")
    private String k42;
}
