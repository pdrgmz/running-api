package com.running.api.service;

import com.running.api.dto.AcwrStatusDto;
import com.running.api.dto.RecordResponseDto;
import com.running.api.dto.SummaryStatsDto;
import com.running.api.dto.TrainingContextDto;
import com.running.api.dto.TrainingPlanResponseDto;
import com.running.api.dto.TrainingPreferencesDto;
import com.running.api.model.Activity;
import com.running.api.model.TrainingPreferences;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.TrainingPreferencesRepository;
import com.running.api.util.TrainingWeekUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingContextService {

    private static final int RECENT_ACTIVITY_DAYS = 28;
    private static final int RECENT_ACTIVITY_LIMIT = 30;
    private static final int RECENT_WEEKS = 4;

    private final ActivityRepository activityRepository;
    private final TrainingPreferencesRepository preferencesRepository;
    private final TrainingPlanService trainingPlanService;
    private final StatsService statsService;
    private final AnalyticsService analyticsService;

    @Transactional
    public TrainingContextDto getContext(Long userId, LocalDate requestedWeekStart) {
        TrainingPreferences preferences = preferencesRepository.findByUserId(userId).orElse(null);
        ZoneId zone = TrainingWeekUtils.zoneOrDefault(
                preferences == null ? null : preferences.getTimezone());
        LocalDate today = LocalDate.now(zone);
        LocalDate weekStart = requestedWeekStart != null
                ? requestedWeekStart
                : TrainingWeekUtils.currentWeekStart(zone);

        SummaryStatsDto summary;
        try {
            summary = statsService.getSummary(userId, null, null);
        } catch (Exception ex) {
            log.warn("No se pudo calcular el resumen para el contexto del usuario {}: {}", userId, ex.getMessage());
            summary = null;
        }

        AcwrStatusDto acwr;
        try {
            acwr = analyticsService.calculateCurrentAcwr(userId);
        } catch (Exception ex) {
            log.warn("No se pudo calcular el ACWR para el contexto del usuario {}: {}", userId, ex.getMessage());
            acwr = null;
        }

        int athleteMaxHr;
        try {
            athleteMaxHr = analyticsService.resolveAthleteMaxHr(userId, null);
        } catch (Exception ex) {
            athleteMaxHr = 0;
        }

        LocalDate recentFrom = today.minusDays(RECENT_ACTIVITY_DAYS);
        List<Activity> activities = activityRepository.findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(
                userId, recentFrom.atStartOfDay(), today.atTime(23, 59, 59));
        List<TrainingContextDto.RecentActivityDto> recentActivities = activities.stream()
                .sorted(Comparator.comparing(Activity::getStartTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(RECENT_ACTIVITY_LIMIT)
                .map(this::toRecentActivity)
                .toList();

        TrainingContextDto.Preferences prefsDto = new TrainingContextDto.Preferences(
                preferences != null,
                preferences == null ? Boolean.FALSE : preferences.getEnabled(),
                preferences == null ? null : preferences.getDaysPerWeek(),
                preferences == null ? null : preferences.getPreferredLongRunDay(),
                preferences == null ? null : preferences.getNotes());

        return new TrainingContextDto(
                weekStart,
                today,
                zone.getId(),
                prefsDto,
                summary,
                acwr,
                athleteMaxHr,
                recentActivities,
                weeklySummaries(userId, weekStart),
                personalRecords(userId),
                currentPlan(userId));
    }

    public TrainingPreferencesDto getPreferences(Long userId) {
        return trainingPlanService.getPreferences(userId);
    }

    private List<TrainingContextDto.WeeklySummaryDto> weeklySummaries(Long userId, LocalDate weekStart) {
        LocalDate firstWeek = weekStart.minusWeeks(RECENT_WEEKS);
        LocalDate lastDay = weekStart.minusDays(1);
        List<Activity> activities = activityRepository.findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(
                userId, firstWeek.atStartOfDay(), lastDay.atTime(23, 59, 59));

        Map<LocalDate, List<Activity>> byWeek = new LinkedHashMap<>();
        for (Activity activity : activities) {
            if (activity.getStartTime() == null) {
                continue;
            }
            LocalDate week = TrainingWeekUtils.normalizeToMonday(activity.getStartTime().toLocalDate());
            byWeek.computeIfAbsent(week, k -> new ArrayList<>()).add(activity);
        }

        List<TrainingContextDto.WeeklySummaryDto> summaries = new ArrayList<>();
        for (int i = RECENT_WEEKS - 1; i >= 0; i--) {
            LocalDate week = weekStart.minusWeeks(i + 1L);
            List<Activity> weekActivities = byWeek.getOrDefault(week, List.of());
            double distance = weekActivities.stream()
                    .mapToDouble(a -> a.getDistanceMeters() == null ? 0.0 : a.getDistanceMeters())
                    .sum();
            double duration = weekActivities.stream()
                    .mapToDouble(a -> a.getTotalTimeSeconds() == null ? 0.0 : a.getTotalTimeSeconds())
                    .sum();
            double load = weekActivities.stream()
                    .mapToDouble(a -> a.getTrainingLoad() == null ? 0.0 : a.getTrainingLoad())
                    .sum();
            double longest = weekActivities.stream()
                    .mapToDouble(a -> a.getDistanceMeters() == null ? 0.0 : a.getDistanceMeters())
                    .max()
                    .orElse(0.0);
            summaries.add(new TrainingContextDto.WeeklySummaryDto(
                    week,
                    week.plusDays(6),
                    weekActivities.size(),
                    distance,
                    duration,
                    load,
                    longest));
        }
        return summaries;
    }

    private List<RecordResponseDto> personalRecords(Long userId) {
        try {
            return statsService.getPersonalRecords(userId);
        } catch (Exception ex) {
            log.warn("No se pudieron calcular las marcas personales para el usuario {}: {}", userId, ex.getMessage());
            return List.of();
        }
    }

    private TrainingContextDto.CurrentPlan currentPlan(Long userId) {
        return trainingPlanService.getCurrentPlan(userId)
                .map(this::toCurrentPlan)
                .orElse(null);
    }

    private TrainingContextDto.CurrentPlan toCurrentPlan(TrainingPlanResponseDto plan) {
        return new TrainingContextDto.CurrentPlan(
                plan.planId(),
                plan.weekStart(),
                plan.weekEnd(),
                plan.status() == null ? null : plan.status().name(),
                plan.version(),
                plan.summary(),
                plan.workouts());
    }

    private TrainingContextDto.RecentActivityDto toRecentActivity(Activity activity) {
        return new TrainingContextDto.RecentActivityDto(
                activity.getId(),
                activity.getStartTime(),
                activity.getName(),
                activity.getDistanceMeters(),
                activity.getTotalTimeSeconds(),
                activity.getAvgPaceMinPerKm(),
                activity.getAvgHeartRate(),
                activity.getMaxHeartRate(),
                activity.getTrainingLoad(),
                null);
    }
}
