package com.running.api.service;

import com.running.api.dto.WeeklyPlanPayload;
import com.running.api.dto.WeeklyPlanPayload.PlannedWorkoutPayload;
import com.running.api.exception.PlanValidationException;
import com.running.api.model.WorkoutType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrainingPlanValidatorTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    private final TrainingPlanValidator validator = new TrainingPlanValidator();

    private WeeklyPlanPayload validPlan() {
        return new WeeklyPlanPayload(
                "Semana de carga moderada",
                "Si el ACWR sube, reduce el jueves",
                24000.0,
                220.0,
                List.of(
                        new PlannedWorkoutPayload(MONDAY, WorkoutType.EASY, "Rodaje suave",
                                "Z2", 8000.0, 2880.0, 5.4, 2, 60.0, null),
                        new PlannedWorkoutPayload(MONDAY.plusDays(2), WorkoutType.TEMPO, "Tempo",
                                "3x10' a ritmo umbral", 10000.0, 3600.0, 4.6, 4, 90.0, "3x10'"),
                        new PlannedWorkoutPayload(MONDAY.plusDays(5), WorkoutType.LONG_RUN, "Tirada larga",
                                "Ritmo cómodo", 16000.0, 6000.0, 5.8, 2, 120.0, null)),
                "modelo-test",
                "v1");
    }

    @Test
    void acceptsAValidPlan() {
        assertThatCode(() -> validator.validate(MONDAY, validPlan()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWeekStartThatIsNotMonday() {
        assertThatThrownBy(() -> validator.validate(MONDAY.plusDays(1), validPlan()))
                .isInstanceOf(PlanValidationException.class)
                .satisfies(ex -> assertThat(((PlanValidationException) ex).getViolations())
                        .extracting(v -> v.code())
                        .contains("WEEK_START_NOT_MONDAY"));
    }

    @Test
    void rejectsWorkoutOutsideWeek() {
        WeeklyPlanPayload payload = new WeeklyPlanPayload(
                null, null, null, null,
                List.of(new PlannedWorkoutPayload(MONDAY.plusDays(7), WorkoutType.EASY,
                        "Fuera", null, 5000.0, null, null, null, null, null)),
                null, null);

        assertThatThrownBy(() -> validator.validate(MONDAY, payload))
                .isInstanceOf(PlanValidationException.class)
                .satisfies(ex -> assertThat(((PlanValidationException) ex).getViolations())
                        .extracting(v -> v.code())
                        .contains("WORKOUT_OUT_OF_WEEK"));
    }

    @Test
    void rejectsDuplicateWorkoutDatesAndInvalidValues() {
        WeeklyPlanPayload payload = new WeeklyPlanPayload(
                null, null, -1.0, -1.0,
                List.of(
                        new PlannedWorkoutPayload(MONDAY, WorkoutType.REST, "", null,
                                -1.0, -1.0, 1.0, 9, -1.0, null),
                        new PlannedWorkoutPayload(MONDAY, WorkoutType.EASY, "Dos veces", null,
                                5000.0, null, null, null, null, null)),
                null, null);

        assertThatThrownBy(() -> validator.validate(MONDAY, payload))
                .isInstanceOf(PlanValidationException.class)
                .satisfies(ex -> assertThat(((PlanValidationException) ex).getViolations())
                        .extracting(v -> v.code())
                        .contains("DUPLICATE_WORKOUT_DATE", "REST_WORKOUT_NOT_ALLOWED",
                                "NEGATIVE_WEEKLY_DISTANCE", "PACE_OUT_OF_RANGE",
                                "HR_ZONE_OUT_OF_RANGE"));
    }

    @Test
    void rejectsEmptyPlan() {
        WeeklyPlanPayload payload = new WeeklyPlanPayload(
                null, null, null, null, List.of(), null, null);

        assertThatThrownBy(() -> validator.validate(MONDAY, payload))
                .isInstanceOf(PlanValidationException.class)
                .satisfies(ex -> assertThat(((PlanValidationException) ex).getViolations())
                        .extracting(v -> v.code())
                        .contains("NO_WORKOUTS"));
    }
}
