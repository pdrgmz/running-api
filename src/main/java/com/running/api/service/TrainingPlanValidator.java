package com.running.api.service;

import com.running.api.dto.PlanViolationDto;
import com.running.api.dto.WeeklyPlanPayload;
import com.running.api.dto.WeeklyPlanPayload.PlannedWorkoutPayload;
import com.running.api.exception.PlanValidationException;
import com.running.api.model.WorkoutType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reglas de negocio que debe cumplir un plan antes de persistirse. La IA
 * propone; la API decide.
 */
@Component
public class TrainingPlanValidator {

    private static final Set<String> VALID_DAYS = Set.of(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
    private static final int MAX_WORKOUTS_PER_WEEK = 7;

    private final double maxWeeklyIncreasePercent;

    public TrainingPlanValidator(
            @Value("${app.training.plan.max-weekly-increase-percent:25}") double maxWeeklyIncreasePercent) {
        this.maxWeeklyIncreasePercent = Math.max(0.0, maxWeeklyIncreasePercent);
    }

    public void validate(LocalDate weekStart,
                         WeeklyPlanPayload payload,
                         Double previousFourWeekAverageDistanceMeters) {
        List<PlanViolationDto> violations = new ArrayList<>();

        if (weekStart == null) {
            violations.add(v("WEEK_START_REQUIRED", "Falta la fecha de inicio de semana"));
        } else if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            violations.add(v("WEEK_START_NOT_MONDAY",
                    "weekStart debe ser un lunes; recibido: " + weekStart));
        }

        if (payload == null) {
            violations.add(v("PLAN_REQUIRED", "El plan está vacío"));
            throw new PlanValidationException(violations);
        }

        if (payload.workouts() == null || payload.workouts().isEmpty()) {
            violations.add(v("NO_WORKOUTS", "El plan debe incluir al menos una sesión"));
        } else if (payload.workouts().size() > MAX_WORKOUTS_PER_WEEK) {
            violations.add(v("TOO_MANY_WORKOUTS",
                    "El plan no puede tener más de " + MAX_WORKOUTS_PER_WEEK + " sesiones"));
        }

        double sumDistanceMeters = 0.0;
        Set<LocalDate> seenDates = new HashSet<>();
        if (payload.workouts() != null) {
            for (int i = 0; i < payload.workouts().size(); i++) {
                PlannedWorkoutPayload workout = payload.workouts().get(i);
                String prefix = "workouts[" + i + "]";
                if (workout == null) {
                    violations.add(v("WORKOUT_NULL", prefix + " es nulo"));
                    continue;
                }
                if (workout.date() == null) {
                    violations.add(v("WORKOUT_DATE_REQUIRED", prefix + ".date es obligatorio"));
                } else {
                    if (weekStart != null
                            && (workout.date().isBefore(weekStart)
                            || workout.date().isAfter(weekStart.plusDays(6)))) {
                        violations.add(v("WORKOUT_OUT_OF_WEEK",
                                prefix + ".date (" + workout.date() + ") está fuera de la semana "
                                        + weekStart + " a " + weekStart.plusDays(6)));
                    }
                    if (!seenDates.add(workout.date())) {
                        violations.add(v("DUPLICATE_WORKOUT_DATE",
                                "Hay más de una sesión para el día " + workout.date()));
                    }
                }
                if (workout.type() == null) {
                    violations.add(v("WORKOUT_TYPE_REQUIRED", prefix + ".type es obligatorio"));
                } else if (workout.type() == WorkoutType.REST) {
                    violations.add(v("REST_WORKOUT_NOT_ALLOWED",
                            prefix + " es REST: los días de descanso se representan omitiendo la sesión"));
                }
                if (workout.title() == null || workout.title().isBlank()) {
                    violations.add(v("WORKOUT_TITLE_REQUIRED", prefix + ".title es obligatorio"));
                }
                if (workout.targetDistanceMeters() != null) {
                    if (workout.targetDistanceMeters() < 0) {
                        violations.add(v("NEGATIVE_WORKOUT_DISTANCE",
                                prefix + ".targetDistanceMeters no puede ser negativo"));
                    } else {
                        sumDistanceMeters += workout.targetDistanceMeters();
                    }
                }
                if (workout.targetDurationSeconds() != null && workout.targetDurationSeconds() < 0) {
                    violations.add(v("NEGATIVE_WORKOUT_DURATION",
                            prefix + ".targetDurationSeconds no puede ser negativo"));
                }
                if (workout.targetPaceMinPerKm() != null
                        && (workout.targetPaceMinPerKm() < 2.0 || workout.targetPaceMinPerKm() > 15.0)) {
                    violations.add(v("PACE_OUT_OF_RANGE",
                            prefix + ".targetPaceMinPerKm debe estar entre 2.0 y 15.0 min/km"));
                }
                if (workout.targetHrZone() != null
                        && (workout.targetHrZone() < 1 || workout.targetHrZone() > 5)) {
                    violations.add(v("HR_ZONE_OUT_OF_RANGE",
                            prefix + ".targetHrZone debe estar entre 1 y 5"));
                }
                if (workout.targetLoad() != null && workout.targetLoad() < 0) {
                    violations.add(v("NEGATIVE_WORKOUT_LOAD",
                            prefix + ".targetLoad no puede ser negativo"));
                }
            }
        }

        if (payload.targetWeeklyDistanceMeters() != null && payload.targetWeeklyDistanceMeters() < 0) {
            violations.add(v("NEGATIVE_WEEKLY_DISTANCE",
                    "targetWeeklyDistanceMeters no puede ser negativo"));
        }
        if (payload.targetWeeklyLoad() != null && payload.targetWeeklyLoad() < 0) {
            violations.add(v("NEGATIVE_WEEKLY_LOAD", "targetWeeklyLoad no puede ser negativo"));
        }

        double targetDistance = payload.targetWeeklyDistanceMeters() != null
                ? payload.targetWeeklyDistanceMeters()
                : sumDistanceMeters;

        // Regla de progresión: no aumentar más del porcentaje configurado sobre la
        // media de las últimas 4 semanas. Evita planes agresivos que disparen el ACWR.
        if (previousFourWeekAverageDistanceMeters != null
                && previousFourWeekAverageDistanceMeters > 0
                && targetDistance > 0) {
            double limit = previousFourWeekAverageDistanceMeters * (1.0 + maxWeeklyIncreasePercent / 100.0);
            if (targetDistance > limit) {
                violations.add(v("WEEKLY_INCREASE_TOO_HIGH", String.format(Locale.ROOT,
                        "El volumen semanal (%.0f m) supera en más del %.0f%% la media de las últimas "
                                + "4 semanas (%.0f m). Máximo permitido: %.0f m.",
                        targetDistance, maxWeeklyIncreasePercent,
                        previousFourWeekAverageDistanceMeters, limit)));
            }
        }

        if (!violations.isEmpty()) {
            throw new PlanValidationException(violations);
        }
    }

    public static boolean isValidTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return false;
        }
        try {
            java.time.ZoneId.of(timezone);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static String normalizePreferredLongRunDay(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return VALID_DAYS.contains(normalized) ? normalized : null;
    }

    private static PlanViolationDto v(String code, String message) {
        return new PlanViolationDto(code, message);
    }
}
