package com.running.api.exception;

/** El servicio de generación (webhook de n8n) no está configurado. */
public class PlanGenerationNotConfiguredException extends RuntimeException {
    public PlanGenerationNotConfiguredException(String message) {
        super(message);
    }
}
