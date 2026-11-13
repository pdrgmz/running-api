package com.running.api.exception;

/** El servicio de análisis (webhook de n8n) no está configurado. */
public class AnalysisNotConfiguredException extends RuntimeException {
    public AnalysisNotConfiguredException(String message) {
        super(message);
    }
}
