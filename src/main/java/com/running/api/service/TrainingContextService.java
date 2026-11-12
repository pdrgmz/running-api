package com.running.api.service;

import com.running.api.dto.AcwrStatusDto;
import com.running.api.dto.DailyRecommendationResponseDto;
import com.running.api.dto.RecordResponseDto;
import com.running.api.dto.SummaryStatsDto;
import com.running.api.dto.TrainingContextDto;
import com.running.api.dto.TrainingPreferencesDto;
import com.running.api.model.Activity;
import com.running.api.model.TrainingPreferences;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.TrainingPreferencesRepository;
import com.running.api.util.TrainingTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
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
    private static final int RECENT_RECOMMENDATIONS = 14;

    private final ActivityRepository activityRepository;
    private final TrainingPreferencesRepository preferencesRepository;
    private final DailyRecommendationService recommendationService;
    private final StatsService statsService;
    private final AnalyticsService analyticsService;

    @Transactional
    public TrainingContextDto getContext(Long userId, LocalDate requestedDate) {
        TrainingPreferences preferences = preferencesRepository.findByUserId(userId).orElse(null);
        ZoneId zone = TrainingTimeUtils.zoneOrDefault(
                preferences == null ? null : preferences.getTimezone());
        LocalDate today = LocalDate.now(zone);
        LocalDate date = requestedDate != null ? requestedDate : today;

        SummaryStatsDto summary;
        try {
            summary = statsService.getSummary(userId, null, null);
        } catch (Exception ex) {
            log.warn("No se pudo calcular el resumen para el contexto del usuario {}: {}",
                    userId, ex.getMessage());
            summary = null;
        }

        AcwrStatusDto acwr;
        try {
            acwr = analyticsService.calculateCurrentAcwr(userId);
        } catch (Exception ex) {
            log.warn("No se pudo calcular el ACWR para el contexto del usuario {}: {}",
                    userId, ex.getMessage());
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

        List<TrainingContextDto.RecentActivityDto> activitiesOnDate = activityRepository
                .findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(
                        userId, date.atStartOfDay(), date.atTime(23, 59, 59))
                .stream()
                .map(this::toRecentActivity)
                .toList();

        TrainingContextDto.Preferences prefsDto = new TrainingContextDto.Preferences(
                preferences != null,
                preferences == null ? Boolean.FALSE : preferences.getEnabled(),
                preferences == null ? TrainingTimeUtils.DEFAULT_TIMEZONE : preferences.getTimezone(),
                preferences == null
                        ? TrainingTimeUtils.DEFAULT_GENERATION_HOUR
                        : TrainingTimeUtils.normalizeHour(preferences.getGenerationHour()),
                preferences == null ? null : preferences.getNotes());

        DailyRecommendationResponseDto recommendationForDate = recommendationService
                .getRecommendation(userId, date)
                .orElse(null);

        return new TrainingContextDto(
                date,
                today,
                zone.getId(),
                prefsDto,
                summary,
                acwr,
                athleteMaxHr,
                recentActivities,
                activitiesOnDate,
                weeklySummaries(userId, date),
                personalRecords(userId),
                recommendationForDate,
                recommendationService.recentRecommendations(userId, RECENT_RECOMMENDATIONS));
    }

    public TrainingPreferencesDto getPreferences(Long userId) {
        return recommendationService.getPreferences(userId);
    }

    private List<TrainingContextDto.WeeklySummaryDto> weeklySummaries(Long userId, LocalDate date) {
        LocalDate currentWeek = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate firstWeek = currentWeek.minusWeeks(RECENT_WEEKS);
        LocalDate lastDay = currentWeek.minusDays(1);
        List<Activity> activities = activityRepository.findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(
                userId, firstWeek.atStartOfDay(), lastDay.atTime(23, 59, 59));

        Map<LocalDate, List<Activity>> byWeek = new LinkedHashMap<>();
        for (Activity activity : activities) {
            if (activity.getStartTime() == null) {
                continue;
            }
            LocalDate week = activity.getStartTime().toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            byWeek.computeIfAbsent(week, k -> new ArrayList<>()).add(activity);
        }

        List<TrainingContextDto.WeeklySummaryDto> summaries = new ArrayList<>();
        for (int i = RECENT_WEEKS - 1; i >= 0; i--) {
            LocalDate week = currentWeek.minusWeeks(i + 1L);
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
            log.warn("No se pudieron calcular las marcas personales para el usuario {}: {}",
                    userId, ex.getMessage());
            return List.of();
        }
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
