package com.running.api.event;

/**
 * Evento publicado cuando se crea una actividad nueva a partir de un archivo.
 * Se usa para lanzar el análisis automático con n8n una vez confirmada la
 * transacción (ver {@code ActivityAnalysisListener}).
 */
public record ActivityCreatedEvent(Long userId, String activityId) {
}
