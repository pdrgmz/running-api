package com.running.api.exception;

/** El job de análisis está en un estado que no permite la operación solicitada. */
public class AnalysisJobStateException extends RuntimeException {
    public AnalysisJobStateException(String message) {
        super(message);
    }
}
