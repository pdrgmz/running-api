package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "Evaluación del ratio de carga aguda vs crónica (ACWR) y nivel de riesgo de lesión")
public record AcwrStatusDto(
    @Schema(description = "Fecha de la evaluación", example = "2026-10-08")
    LocalDate date,

    @Schema(description = "Carga Aguda / Fatiga (ATL 7 días)", example = "65.0")
    double acuteLoad,

    @Schema(description = "Carga Crónica / Fitness (CTL 42 días)", example = "50.0")
    double chronicLoad,

    @Schema(description = "Valor numérico del ratio (ATL / CTL)", example = "1.30")
    double acwr,

    @Schema(description = "Categoría o zona de riesgo", example = "SWEET_SPOT")
    String zone,

    @Schema(description = "Recomendación/Diagnóstico para el corredor", example = "Carga de entrenamiento óptima. Progresión segura.")
    String recommendation
) {}