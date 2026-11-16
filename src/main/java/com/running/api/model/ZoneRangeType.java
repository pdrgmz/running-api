package com.running.api.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tipo de zona del perfil del corredor")
public enum ZoneRangeType {

    /** Zona de frecuencia cardíaca; los valores se expresan en bpm. */
    HEART_RATE,

    /** Zona de ritmo; los valores se expresan en segundos por kilómetro. */
    PACE
}
