package com.running.api.exception;

/** El servicio de generación (webhook de n8n) no está configurado. */
public class GenerationNotConfiguredException extends RuntimeException {
    public GenerationNotConfiguredException(String message) {
        super(message);
    }
}
