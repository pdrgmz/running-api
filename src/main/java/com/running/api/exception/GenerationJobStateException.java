package com.running.api.exception;

/** Conflicto de estado de un job de generación (completado, fallido o caducado). */
public class GenerationJobStateException extends RuntimeException {
    public GenerationJobStateException(String message) {
        super(message);
    }
}
