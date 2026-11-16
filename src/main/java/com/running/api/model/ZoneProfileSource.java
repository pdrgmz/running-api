package com.running.api.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Origen del perfil de zonas del corredor")
public enum ZoneProfileSource {

    /** Perfil por defecto, calculado al vuelo y todavía no guardado. */
    DEFAULT,

    /** Perfil calculado automáticamente desde el historial. */
    CALCULATED,

    /** Perfil editado manualmente por el atleta. */
    MANUAL
}
