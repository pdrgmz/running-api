package com.running.api.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Método usado para calcular las zonas de frecuencia cardíaca")
public enum HrZoneMethod {

    /** Porcentajes de la FC máxima del atleta. */
    MAX_HR_PERCENT,

    /** Porcentajes de la reserva de FC (Karvonen): FC reposo + % (FC máx - FC reposo). */
    HEART_RATE_RESERVE,

    /** Rangos en bpm definidos manualmente por el atleta. */
    CUSTOM
}
