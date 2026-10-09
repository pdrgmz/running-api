package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "Punto diario de la curva PMC (Fitness, Fatiga, Forma y ACWR)")
public record PmcPointDto(
    @Schema(description = "Fecha de la muestra", example = "2026-09-05")
    LocalDate date,

    @Schema(description = "Carga TRIMP total acumulada en el día", example = "85.5")
    double dailyTrimp,

    @Schema(description = "Chronic Training Load (42 días) - Aptitud física / Fitness", example = "52.4")
    double ctl,

    @Schema(description = "Acute Training Load (7 días) - Fatiga acumulada", example = "68.1")
    double atl,

    @Schema(description = "Training Stress Balance (CTL - ATL) - Estado de forma", example = "-15.7")
    double tsb,

    @Schema(description = "Acute:Chronic Workload Ratio (ATL / CTL) - Ratio de riesgo de lesión", example = "1.30")
    double acwr
) {}