package com.running.api.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

public final class TrainingWeekUtils {

    public static final String DEFAULT_TIMEZONE = "Europe/Madrid";

    private TrainingWeekUtils() {
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

    public static LocalDate currentWeekStart(ZoneId zone) {
        return LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate nextWeekStart(ZoneId zone) {
        return LocalDate.now(zone).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate currentOrNextWeekStart(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate normalizeToMonday(LocalDate date) {
        return date == null ? null : date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
