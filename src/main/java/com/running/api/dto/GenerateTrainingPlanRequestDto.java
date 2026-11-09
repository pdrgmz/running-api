package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Petición para generar un plan semanal")
public record GenerateTrainingPlanRequestDto(
        @Schema(description = "Lunes de la semana a generar. Si se omite, se usa el próximo lunes en la zona horaria del atleta",
                example = "2026-11-16")
        LocalDate weekStart,
        @Schema(description = "Si ya existe un plan para la semana, archiva el anterior y crea una versión nueva",
                example = "false")
        Boolean replace
) {
}
