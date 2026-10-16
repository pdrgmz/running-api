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
@Schema(description = "Marca personal (Personal Best) para una distancia concreta")
public class RecordResponseDto {

    @Schema(description = "Categoría de distancia (1k, 5k, 10k, 15k, 21k, 42k)", example = "10k")
    private String distanceCategory;

    @Schema(description = "Distancia objetivo en metros", example = "10000.0")
    private Double targetDistanceMeters;

    @Schema(description = "Mejor tiempo en segundos", example = "2712.0")
    private Double bestTimeSeconds;

    @Schema(description = "Mejor tiempo formateado (HH:MM:SS)", example = "00:45:12")
    private String bestTimeFormatted;

    @Schema(description = "ID de la actividad donde se logró la marca", example = "2026-09-05T07:00:00Z")
    private String activityId;

    @Schema(description = "Fecha de la actividad donde se logró la marca", example = "2026-09-05T07:00:00")
    private LocalDateTime activityDate;

    @Schema(description = "Velocidad media en m/s", example = "3.69")
    private Double avgSpeed;

    @Schema(description = "Ritmo medio en min/km (ej. 4.52 = 4:31 min/km)", example = "4.52")
    private Double avgPaceMinPerKm;
}
