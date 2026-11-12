package com.running.api.util;

import java.time.LocalDate;
import java.time.ZoneId;

/** Utilidades de zona horaria para la generación diaria. */
public final class TrainingTimeUtils {

    public static final String DEFAULT_TIMEZONE = "Europe/Madrid";
    public static final int DEFAULT_GENERATION_HOUR = 6;

    private TrainingTimeUtils() {
    }

    public static ZoneId zoneOrDefault(String timezone) {
        try {
            return timezone == null || timezone.isBlank()
                    ? ZoneId.of(DEFAULT_TIMEZONE)
                    : ZoneId.of(timezone);
        } catch (Exception ex) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }

    public static LocalDate today(ZoneId zone) {
        return LocalDate.now(zone);
    }

    public static boolean isValidTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return false;
        }
        try {
            ZoneId.of(timezone);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static int normalizeHour(Integer hour) {
        if (hour == null || hour < 0 || hour > 23) {
            return DEFAULT_GENERATION_HOUR;
        }
        return hour;
    }
}
