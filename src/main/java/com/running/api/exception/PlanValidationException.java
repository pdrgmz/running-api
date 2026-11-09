package com.running.api.exception;

import com.running.api.dto.PlanViolationDto;

import java.util.List;

/**
 * El plan propuesto por la IA incumple las reglas de negocio. n8n debe recibir
 * las violaciones para que el agente corrija y reintente.
 */
public class PlanValidationException extends RuntimeException {

    private final List<PlanViolationDto> violations;

    public PlanValidationException(List<PlanViolationDto> violations) {
        super("El plan semanal no es válido");
        this.violations = List.copyOf(violations);
    }

    public List<PlanViolationDto> getViolations() {
        return violations;
    }
}
