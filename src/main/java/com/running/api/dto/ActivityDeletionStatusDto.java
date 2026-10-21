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
@Schema(description = "Estado del vaciado asíncrono de las actividades del usuario")
public class ActivityDeletionStatusDto {

    @Schema(description = "Identificador único del vaciado", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String deleteId;

    @Schema(description = "Estado del vaciado: PENDING, PROCESSING, COMPLETED o FAILED", example = "PROCESSING")
    private String status;

    @Schema(description = "Cantidad total de actividades a eliminar", example = "50")
    private long totalActivities;

    @Schema(description = "Cantidad de actividades eliminadas hasta el momento", example = "32")
    private long deletedActivities;

    @Schema(description = "Porcentaje de progreso del vaciado", example = "64.0")
    private double progressPercent;

    @Schema(description = "Mensaje informativo del estado", example = "Vaciado en proceso")
    private String message;

    @Schema(description = "Detalle del error cuando el estado es FAILED", example = "No se pudo completar el vaciado")
    private String error;
}
