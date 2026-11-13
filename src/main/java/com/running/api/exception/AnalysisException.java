package com.running.api.exception;

/** No se pudo entregar el job de análisis al webhook de n8n. */
public class AnalysisException extends RuntimeException {
    public AnalysisException(String message) {
        super(message);
    }

    public AnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
