package com.running.api.exception;

/** No se pudo entregar el job al webhook de n8n. */
public class PlanGenerationException extends RuntimeException {
    public PlanGenerationException(String message) {
        super(message);
    }

    public PlanGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
