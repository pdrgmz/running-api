package com.running.api.dto;

import com.running.api.model.HrZoneMethod;
import com.running.api.model.PaceZoneMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Actualización manual del perfil de zonas. Se puede enviar solo {@code heartRate}, solo
 * {@code pace} o ambos. Dentro de cada sección, si se envían {@code zones} el método pasa a
 * {@code CUSTOM}; si no se envían, los rangos se recalculan con el método y los parámetros
 * fisiológicos indicados.
 */
@Schema(description = "Actualización manual del perfil de zonas del corredor")
public record UpdateRunnerZonesRequestDto(
        @Schema(description = "Parámetros y zonas de frecuencia cardíaca")
        HeartRateZones heartRate,

        @Schema(description = "Parámetros y zonas de ritmo")
        PaceZones pace
) {

    @Schema(description = "Parámetros y zonas de frecuencia cardíaca")
    public record HeartRateZones(
            @Schema(description = "FC máxima de referencia (bpm)", example = "190")
            Integer maxHeartRate,

            @Schema(description = "FC de reposo (bpm)", example = "52")
            Integer restingHeartRate,

            @Schema(description = "Método de cálculo", example = "MAX_HR_PERCENT")
            HrZoneMethod method,

            @Schema(description = "Rangos personalizados. Si se envían, deben ser 5 zonas sin solapes y method debe ser CUSTOM")
            List<HeartRateZoneRange> zones
    ) {
    }

    @Schema(description = "Rango personalizado de una zona de frecuencia cardíaca")
    public record HeartRateZoneRange(
            @Schema(description = "Número de zona (1-5)", example = "1")
            Integer zone,

            @Schema(description = "Límite inferior en bpm", example = "95")
            Integer minBpm,

            @Schema(description = "Límite superior en bpm", example = "114")
            Integer maxBpm
    ) {
    }

    @Schema(description = "Parámetros y zonas de ritmo")
    public record PaceZones(
            @Schema(description = "Ritmo umbral de referencia (segundos por kilómetro)", example = "270.0")
            Double thresholdPaceSecondsPerKm,

            @Schema(description = "Método de cálculo", example = "THRESHOLD_PACE_PERCENT")
            PaceZoneMethod method,

            @Schema(description = "Rangos personalizados. Si se envían, deben ser 5 zonas sin solapes y method debe ser CUSTOM")
            List<PaceZoneRange> zones
    ) {
    }

    @Schema(description = "Rango personalizado de una zona de ritmo")
    public record PaceZoneRange(
            @Schema(description = "Número de zona (1-5)", example = "1")
            Integer zone,

            @Schema(description = "Ritmo más rápido de la zona (segundos por kilómetro)", example = "390.0")
            Double fastestPaceSecondsPerKm,

            @Schema(description = "Ritmo más lento de la zona (segundos por kilómetro)", example = "450.0")
            Double slowestPaceSecondsPerKm
    ) {
    }
}
