package com.running.api.service;

import com.running.api.dto.DailyRecommendationPayload;
import com.running.api.dto.DailyRecommendationPayload.WorkoutPayload;
import com.running.api.dto.ValidationViolationDto;
import com.running.api.exception.RecommendationValidationException;
import com.running.api.model.WorkoutType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reglas de negocio que debe cumplir la recomendación diaria antes de
 * persistirse. La IA propone; la API decide.
 */
@Component
public class DailyRecommendationValidator {

    private static final double MAX_DISTANCE_METERS = 100_000.0;
    private static final double MAX_DURATION_SECONDS = 86_400.0;

    public void validate(LocalDate date, DailyRecommendationPayload payload) {
        List<ValidationViolationDto> violations = new ArrayList<>();

        if (date == null) {
            violations.add(v("DATE_REQUIRED", "Falta la fecha de la recomendación"));
        }

        if (payload == null) {
            violations.add(v("RECOMMENDATION_REQUIRED", "La recomendación está vacía"));
            throw new RecommendationValidationException(violations);
        }

        WorkoutPayload workout = payload.workout();
        if (workout == null) {
            violations.add(v("WORKOUT_REQUIRED",
                    "La recomendación debe incluir un objeto workout, incluso en días de descanso (type=REST)"));
            throw new RecommendationValidationException(violations);
        }

        if (workout.type() == null) {
            violations.add(v("WORKOUT_TYPE_REQUIRED", "workout.type es obligatorio"));
        }
        if (workout.title() == null || workout.title().isBlank()) {
            violations.add(v("WORKOUT_TITLE_REQUIRED", "workout.title es obligatorio"));
        }

        boolean rest = workout.type() == WorkoutType.REST;
        boolean hasDistance = workout.targetDistanceMeters() != null;
        boolean hasDuration = workout.targetDurationSeconds() != null;

        if (!rest && !hasDistance && !hasDuration) {
            violations.add(v("WORKOUT_TARGET_REQUIRED",
                    "Una sesión que no es REST debe indicar targetDistanceMeters o targetDurationSeconds"));
        }

        if (workout.targetDistanceMeters() != null) {
            if (workout.targetDistanceMeters() < 0) {
                violations.add(v("NEGATIVE_WORKOUT_DISTANCE",
                        "workout.targetDistanceMeters no puede ser negativo"));
            } else if (workout.targetDistanceMeters() > MAX_DISTANCE_METERS) {
                violations.add(v("WORKOUT_DISTANCE_TOO_LARGE",
                        "workout.targetDistanceMeters no puede superar " + (int) MAX_DISTANCE_METERS + " m"));
            } else if (rest && workout.targetDistanceMeters() > 0) {
                violations.add(v("REST_WORKOUT_HAS_DISTANCE",
                        "Un día de descanso (REST) no puede tener distancia objetivo"));
            }
        }

        if (workout.targetDurationSeconds() != null) {
            if (workout.targetDurationSeconds() < 0) {
                violations.add(v("NEGATIVE_WORKOUT_DURATION",
                        "workout.targetDurationSeconds no puede ser negativo"));
            } else if (workout.targetDurationSeconds() > MAX_DURATION_SECONDS) {
                violations.add(v("WORKOUT_DURATION_TOO_LARGE",
                        "workout.targetDurationSeconds no puede superar 24 horas"));
            } else if (rest && workout.targetDurationSeconds() > 0) {
                violations.add(v("REST_WORKOUT_HAS_DURATION",
                        "Un día de descanso (REST) no puede tener duración objetivo"));
            }
        }

        if (workout.targetPaceMinPerKm() != null) {
            if (workout.targetPaceMinPerKm() < 2.0 || workout.targetPaceMinPerKm() > 15.0) {
                violations.add(v("PACE_OUT_OF_RANGE",
                        "workout.targetPaceMinPerKm debe estar entre 2.0 y 15.0 min/km"));
            } else if (rest) {
                violations.add(v("REST_WORKOUT_HAS_PACE",
                        "Un día de descanso (REST) no puede tener ritmo objetivo"));
            }
        }

        if (workout.targetHrZone() != null
                && (workout.targetHrZone() < 1 || workout.targetHrZone() > 5)) {
            violations.add(v("HR_ZONE_OUT_OF_RANGE", "workout.targetHrZone debe estar entre 1 y 5"));
        }

        if (workout.targetLoad() != null && workout.targetLoad() < 0) {
            violations.add(v("NEGATIVE_WORKOUT_LOAD", "workout.targetLoad no puede ser negativo"));
        }

        if (!violations.isEmpty()) {
            throw new RecommendationValidationException(violations);
        }
    }

    private static ValidationViolationDto v(String code, String message) {
        return new ValidationViolationDto(code, message);
    }
}
