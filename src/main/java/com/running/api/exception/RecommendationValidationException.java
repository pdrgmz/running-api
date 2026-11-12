package com.running.api.exception;

import com.running.api.dto.ValidationViolationDto;

import java.util.List;

/**
 * La recomendación propuesta por la IA incumple las reglas de negocio. n8n
 * recibe las violaciones para que el agente corrija y reintente.
 */
public class RecommendationValidationException extends RuntimeException {

    private final List<ValidationViolationDto> violations;

    public RecommendationValidationException(List<ValidationViolationDto> violations) {
        super("La recomendación diaria no es válida");
        this.violations = List.copyOf(violations);
    }

    public List<ValidationViolationDto> getViolations() {
        return violations;
    }
}
