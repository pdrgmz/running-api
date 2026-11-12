package com.running.api.exception;

import java.time.LocalDate;

public class RecommendationAlreadyExistsException extends RuntimeException {

    public RecommendationAlreadyExistsException(LocalDate date) {
        super("Ya existe una recomendación para el día " + date
                + ". Usa replace=true para regenerarla.");
    }
}
