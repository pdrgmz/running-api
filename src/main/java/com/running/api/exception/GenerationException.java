package com.running.api.exception;

/** No se pudo entregar el job al webhook de n8n. */
public class GenerationException extends RuntimeException {
    public GenerationException(String message) {
        super(message);
    }

    public GenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
