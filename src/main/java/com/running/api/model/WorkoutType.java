package com.running.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/** Tipo de sesión planificada. */
public enum WorkoutType {
    EASY,
    RECOVERY,
    LONG_RUN,
    TEMPO,
    INTERVALS,
    HILLS,
    RACE_PACE,
    RACE,
    CROSS_TRAINING,
    REST,
    OTHER;

    @JsonCreator
    public static WorkoutType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "LONG", "LONGRUN", "LONG_RUN" -> LONG_RUN;
            case "INTERVAL", "INTERVALS", "VO2MAX", "SPEED" -> INTERVALS;
            case "HILL", "HILLS", "HILL_REPEATS" -> HILLS;
            case "RACE_PACE", "THRESHOLD", "T" -> RACE_PACE;
            case "CROSS", "CROSS_TRAINING", "CROSSTRAINING", "STRENGTH", "BIKE", "SWIM" -> CROSS_TRAINING;
            default -> WorkoutType.valueOf(normalized);
        };
    }
}
