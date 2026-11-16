package com.running.api.dto;

import com.running.api.model.ZoneProfileSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Perfil de zonas de frecuencia cardíaca y de ritmo del corredor")
public record RunnerZonesResponseDto(
        @Schema(description = "true si el atleta tiene un perfil guardado", example = "true")
        boolean configured,

        @Schema(description = "Origen del perfil", example = "CALCULATED")
        ZoneProfileSource source,

        @Schema(description = "Última vez que se calcularon las zonas desde el historial")
        LocalDateTime calculatedAt,

        @Schema(description = "Última actualización del perfil")
        LocalDateTime updatedAt,

        @Schema(description = "Zonas de frecuencia cardíaca")
        HeartRateZonesProfileDto heartRate,

        @Schema(description = "Zonas de ritmo; null si todavía no hay una referencia de ritmo")
        PaceZonesProfileDto pace
) {
}
