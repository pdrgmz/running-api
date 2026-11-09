package com.running.api.exception;

/** Conflicto de estado de un job de generación (completado, fallido o caducado). */
public class PlanJobStateException extends RuntimeException {
    public PlanJobStateException(String message) {
        super(message);
    }
}
