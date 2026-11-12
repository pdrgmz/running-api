package com.running.api.service;

import com.running.api.dto.DailyRecommendationPayload;
import com.running.api.dto.DailyRecommendationPayload.WorkoutPayload;
import com.running.api.exception.RecommendationValidationException;
import com.running.api.model.WorkoutType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyRecommendationValidatorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 11);

    private final DailyRecommendationValidator validator = new DailyRecommendationValidator();

    private DailyRecommendationPayload payload(WorkoutPayload workout) {
        return new DailyRecommendationPayload(
                "Rodaje suave",
                "El ACWR está estable y no hay carga acumulada",
                "Mantén la respiración cómoda",
                workout,
                "gpt-4.1-mini",
                "daily-recommendation-v1");
    }

    private WorkoutPayload easy() {
        return new WorkoutPayload(WorkoutType.EASY, "Rodaje suave", "45 min en Z2",
                8000.0, 2700.0, 5.6, 2, 50.0, "45 min continuos");
    }

    @Test
    void acceptsAValidEasyWorkout() {
        assertThatCode(() -> validator.validate(DATE, payload(easy())))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsRestWithoutTargets() {
        WorkoutPayload rest = new WorkoutPayload(WorkoutType.REST, "Descanso",
                "Día de descanso completo", null, null, null, null, 0.0, null);

        assertThatCode(() -> validator.validate(DATE, payload(rest)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsRestWithDistance() {
        WorkoutPayload rest = new WorkoutPayload(WorkoutType.REST, "Descanso", null,
                5000.0, null, null, null, null, null);

        assertThatThrownBy(() -> validator.validate(DATE, payload(rest)))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("no es válida");
    }

    @Test
    void rejectsWorkoutWithoutDistanceOrDuration() {
        WorkoutPayload workout = new WorkoutPayload(WorkoutType.TEMPO, "Tempo", null,
                null, null, null, 4, null, "3x10'");

        assertThatThrownBy(() -> validator.validate(DATE, payload(workout)))
                .isInstanceOf(RecommendationValidationException.class);
    }

    @Test
    void rejectsOutOfRangePace() {
        WorkoutPayload workout = new WorkoutPayload(WorkoutType.EASY, "Rodaje", null,
                8000.0, null, 25.0, 2, null, null);

        assertThatThrownBy(() -> validator.validate(DATE, payload(workout)))
                .isInstanceOf(RecommendationValidationException.class);
    }

    @Test
    void rejectsOutOfRangeHeartRateZone() {
        WorkoutPayload workout = new WorkoutPayload(WorkoutType.EASY, "Rodaje", null,
                8000.0, null, 5.5, 9, null, null);

        assertThatThrownBy(() -> validator.validate(DATE, payload(workout)))
                .isInstanceOf(RecommendationValidationException.class);
    }

    @Test
    void rejectsMissingWorkout() {
        assertThatThrownBy(() -> validator.validate(DATE, payload(null)))
                .isInstanceOf(RecommendationValidationException.class);
    }

    @Test
    void rejectsMissingTitle() {
        WorkoutPayload workout = new WorkoutPayload(WorkoutType.EASY, " ", null,
                8000.0, null, 5.5, 2, null, null);

        assertThatThrownBy(() -> validator.validate(DATE, payload(workout)))
                .isInstanceOf(RecommendationValidationException.class);
    }

    @Test
    void rejectsMissingDate() {
        assertThatThrownBy(() -> validator.validate(null, payload(easy())))
                .isInstanceOf(RecommendationValidationException.class);
    }
}
