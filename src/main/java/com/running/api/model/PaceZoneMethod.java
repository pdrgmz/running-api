package com.running.api.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Método usado para calcular las zonas de ritmo")
public enum PaceZoneMethod {

    /** Porcentajes del ritmo umbral del atleta (segundos por kilómetro). */
    THRESHOLD_PACE_PERCENT,

    /** Rangos de ritmo definidos manualmente por el atleta. */
    CUSTOM
}
