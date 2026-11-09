package com.running.api.exception;

import java.time.LocalDate;

public class PlanAlreadyExistsException extends RuntimeException {

    public PlanAlreadyExistsException(LocalDate weekStart) {
        super("Ya existe un plan activo para la semana " + weekStart
                + ". Usa replace=true para regenerarlo.");
    }
}
