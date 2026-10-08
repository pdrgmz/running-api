package com.running.api.service;

import com.running.api.dto.RecordResponseDto;
import com.running.api.dto.SummaryStatsDto;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.ActivityTrackpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.running.api.model.GlobalSummaryStats;
import com.running.api.model.PersonalRecord;
import com.running.api.repository.GlobalSummaryStatsRepository;
import com.running.api.repository.PersonalRecordRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatsService {

    private final ActivityRepository activityRepository;
    private final ActivityTrackpointRepository trackpointRepository;
    private final GlobalSummaryStatsRepository summaryStatsRepository;
    private final PersonalRecordRepository personalRecordRepository;

    private static final LinkedHashMap<String, Double> TARGET_DISTANCES = new LinkedHashMap<>() {{
        put("1k", 1000.0);
        put("5k", 5000.0);
        put("10k", 10000.0);
        put("15k", 15000.0);
        put("21k", 21097.5);
        put("42k", 42195.0);
    }};

    @Transactional(readOnly = true)
    public SummaryStatsDto getGlobalSummary() {
        return getSummary(null, null);
    }

    @Transactional(readOnly = true)
    public SummaryStatsDto getSummary(String period, Integer year) {
        LocalDateTime[] range = resolveDateRange(period, year);
        GlobalSummaryStats stats;

        if (range != null) {
            stats = activityRepository.calculateAggregatedSummaryBetween(range[0], range[1]);
            log.info("Estadísticas calculadas para el rango: {} - {}", range[0], range[1]);
            log.info("Estadísticas: {}", stats);
            
            if (stats == null) {
                stats = GlobalSummaryStats.builder().id(1L).build();
            }
        } else {
            stats = summaryStatsRepository.findById(1L)
                    .orElseGet(this::calculateAndSaveInitialStats);
        }

        long totalActivities = stats.getTotalActivities();
        double totalDistance = stats.getTotalDistanceMeters();
        double totalTime = stats.getTotalTimeSeconds();
        double totalElevation = stats.getTotalElevationGain();
        long totalCalories = stats.getTotalCalories();
        int maxHrGlobal = stats.getMaxHeartRateGlobal();

        long hours = (long) totalTime / 3600;
        long minutes = ((long) totalTime % 3600) / 60;
        long seconds = (long) totalTime % 60;

        return SummaryStatsDto.builder()
                .totalActivities(totalActivities)
                .totalDistanceMeters(totalDistance)
                .totalDistanceKm(Math.round((totalDistance / 1000.0) * 100.0) / 100.0)
                .totalTimeSeconds(totalTime)
                .totalTimeFormatted(String.format("%02d:%02d:%02d", hours, minutes, seconds))
                .totalElevationGain(Math.round(totalElevation * 10.0) / 10.0)
                .totalCalories((int) totalCalories)
                .avgDistanceKm(totalActivities > 0 ? Math.round((totalDistance / 1000.0 / totalActivities) * 100.0) / 100.0 : 0.0)
                .maxHeartRateGlobal(maxHrGlobal)
                .build();
    }

    private LocalDateTime[] resolveDateRange(String period, Integer year) {
        LocalDateTime[] range = calculateDateRange(period, year);
        if (range != null) {
            log.info("Rango temporal resuelto para period='{}', year={}: desde {} hasta {}", period, year, range[0], range[1]);
        } else {
            log.info("Sin filtro de rango temporal para period='{}', year={}", period, year);
        }
        return range;
    }

    private LocalDateTime[] calculateDateRange(String period, Integer year) {
    if (period != null && !period.trim().isEmpty()) {
        String p = period.trim().toUpperCase();

        if (p.matches("^\\d{4}$")) {
            int y = Integer.parseInt(p);
            return createYearRange(y);
        }

        if (p.matches("^\\d{4}-\\d{2}$")) {
            java.time.YearMonth ym = java.time.YearMonth.parse(p);
            return new LocalDateTime[]{
                    ym.atDay(1).atStartOfDay(),
                    ym.atEndOfMonth().atTime(23, 59, 59, 999_000_000)
            };
        }

        if ("THIS_MONTH".equals(p)) {
            java.time.YearMonth ym = java.time.YearMonth.now();
            return new LocalDateTime[]{
                    ym.atDay(1).atStartOfDay(),
                    ym.atEndOfMonth().atTime(23, 59, 59, 999_000_000)
            };
        }

        if ("LAST_MONTH".equals(p)) {
            java.time.YearMonth ym = java.time.YearMonth.now().minusMonths(1);
            return new LocalDateTime[]{
                    ym.atDay(1).atStartOfDay(),
                    ym.atEndOfMonth().atTime(23, 59, 59, 999_000_000)
            };
        }

        if ("THIS_YEAR".equals(p)) {
            int y = LocalDateTime.now().getYear();
            return createYearRange(y);
        }

        if ("LAST_YEAR".equals(p)) {
            int y = LocalDateTime.now().getYear() - 1;
            return createYearRange(y);
        }

        if ("THIS_WEEK".equals(p)) {
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDate monday = today.with(java.time.DayOfWeek.MONDAY);
            java.time.LocalDate sunday = today.with(java.time.DayOfWeek.SUNDAY);

            return new LocalDateTime[]{
                    monday.atStartOfDay(),
                    sunday.atTime(23, 59, 59, 999_000_000)
            };
        }

        if ("LAST_WEEK".equals(p)) {
            java.time.LocalDate lastWeekDay = java.time.LocalDate.now().minusWeeks(1);
            java.time.LocalDate monday = lastWeekDay.with(java.time.DayOfWeek.MONDAY);
            java.time.LocalDate sunday = lastWeekDay.with(java.time.DayOfWeek.SUNDAY);

            return new LocalDateTime[]{
                    monday.atStartOfDay(),
                    sunday.atTime(23, 59, 59, 999_000_000)
            };
        }
    }

    if (year != null) {
        return createYearRange(year);
    }

    return null;
}

    private LocalDateTime[] createYearRange(int year) {
        return new LocalDateTime[]{
                LocalDateTime.of(year, 1, 1, 0, 0, 0),
                LocalDateTime.of(year, 12, 31, 23, 59, 59, 999999999)
        };
    }

    @Transactional
    public List<RecordResponseDto> getPersonalRecords() {
        List<PersonalRecord> records = personalRecordRepository.findAll();

        if (records.isEmpty()) {
            records = recalculateAllPersonalRecords();
        }

        return records.stream().map(r -> {
            long h = (long) (r.getBestTimeSeconds() / 3600);
            long m = (long) ((r.getBestTimeSeconds() % 3600) / 60);
            long s = (long) (r.getBestTimeSeconds() % 60);

            return RecordResponseDto.builder()
                    .distanceCategory(r.getDistanceCategory())
                    .targetDistanceMeters(r.getTargetDistanceMeters())
                    .bestTimeSeconds(r.getBestTimeSeconds())
                    .bestTimeFormatted(String.format("%02d:%02d:%02d", h, m, s))
                    .activityId(r.getActivityId())
                    .activityDate(r.getActivityDate())
                    .avgSpeed(r.getAvgSpeed())
                    .avgPaceMinPerKm(r.getAvgPaceMinPerKm())
                    .build();
        }).toList();
    }

    private Double findFastestSegment(List<ActivityTrackpoint> points, double targetMeters) {
        Double minTimeSec = null;

        for (int left = 0; left < points.size(); left++) {
            ActivityTrackpoint startPt = points.get(left);
            if (startPt.getDistanceMeters() == null) continue;

            for (int right = left + 1; right < points.size(); right++) {
                ActivityTrackpoint endPt = points.get(right);
                if (endPt.getDistanceMeters() == null) continue;

                double distDiff = endPt.getDistanceMeters() - startPt.getDistanceMeters();
                if (distDiff >= targetMeters) {
                    long duration = Duration.between(startPt.getTimestamp(), endPt.getTimestamp()).getSeconds();
                    if (duration > 0 && (minTimeSec == null || duration < minTimeSec)) {
                        minTimeSec = (double) duration;
                    }
                    break;
                }
            }
        }
        return minTimeSec;
    }
    
    private GlobalSummaryStats calculateAndSaveInitialStats() {
        GlobalSummaryStats initialStats = activityRepository.calculateAggregatedSummary();
        
        if (initialStats == null) {
            initialStats = GlobalSummaryStats.builder().id(1L).build();
        }

        return summaryStatsRepository.save(initialStats);
    }

    @Transactional
    public void checkAndSetPersonalRecords(Activity activity) {
        if (activity.getDistanceMeters() == null) return;

        List<ActivityTrackpoint> points = trackpointRepository.findByActivityIdOrderByTimestampAsc(activity.getId());
        if (points == null || points.isEmpty()) return;

        for (Map.Entry<String, Double> entry : TARGET_DISTANCES.entrySet()) {
            String category = entry.getKey();
            Double targetMeters = entry.getValue();

            if (activity.getDistanceMeters() < targetMeters) continue;

            Double bestTimeForActivity = findFastestSegment(points, targetMeters);
            if (bestTimeForActivity == null) continue;

            Optional<PersonalRecord> existingRecordOpt = personalRecordRepository.findByDistanceCategory(category);

            if (existingRecordOpt.isEmpty() || bestTimeForActivity < existingRecordOpt.get().getBestTimeSeconds()) {
                double avgSpeed = targetMeters / bestTimeForActivity;
                double pace = (1000.0 / (avgSpeed * 60.0));

                PersonalRecord record = existingRecordOpt.orElseGet(() -> PersonalRecord.builder()
                        .distanceCategory(category)
                        .targetDistanceMeters(targetMeters)
                        .build());

                record.setBestTimeSeconds(bestTimeForActivity);
                record.setActivityId(activity.getId());
                record.setActivityDate(activity.getStartTime());
                record.setAvgSpeed(Math.round(avgSpeed * 100.0) / 100.0);
                record.setAvgPaceMinPerKm(Math.round(pace * 100.0) / 100.0);

                personalRecordRepository.save(record);
            }
        }
    }

    public List<PersonalRecord> recalculateAllPersonalRecords() {
        List<PersonalRecord> calculatedRecords = new ArrayList<>();

        for (Map.Entry<String, Double> entry : TARGET_DISTANCES.entrySet()) {
            String category = entry.getKey();
            Double targetMeters = entry.getValue();

            List<Activity> eligibleActivities = activityRepository.findByDistanceMetersGreaterThanEqual(targetMeters);

            PersonalRecord bestRecordForCategory = null;

            for (Activity activity : eligibleActivities) {
                List<ActivityTrackpoint> points = trackpointRepository.findByActivityIdOrderByTimestampAsc(activity.getId());
                if (points == null || points.isEmpty()) continue;

                Double bestTimeForActivity = findFastestSegment(points, targetMeters);
                if (bestTimeForActivity != null) {
                    if (bestRecordForCategory == null || bestTimeForActivity < bestRecordForCategory.getBestTimeSeconds()) {
                        double avgSpeed = targetMeters / bestTimeForActivity;
                        double pace = (1000.0 / (avgSpeed * 60.0));

                        bestRecordForCategory = PersonalRecord.builder()
                                .distanceCategory(category)
                                .targetDistanceMeters(targetMeters)
                                .bestTimeSeconds(bestTimeForActivity)
                                .activityId(activity.getId())
                                .activityDate(activity.getStartTime())
                                .avgSpeed(Math.round(avgSpeed * 100.0) / 100.0)
                                .avgPaceMinPerKm(Math.round(pace * 100.0) / 100.0)
                                .build();
                    }
                }
            }

            if (bestRecordForCategory != null) {
                calculatedRecords.add(bestRecordForCategory);
            }
        }

        if (!calculatedRecords.isEmpty()) {
            personalRecordRepository.saveAll(calculatedRecords);
        }

        return calculatedRecords;
    }

    @Transactional
    public void recalculateStatsAndRecords() {
        summaryStatsRepository.deleteAll();
        GlobalSummaryStats newStats = activityRepository.calculateAggregatedSummary();
        if (newStats == null) {
            newStats = GlobalSummaryStats.builder().id(1L).build();
        }
        summaryStatsRepository.save(newStats);

        personalRecordRepository.deleteAll();
        recalculateAllPersonalRecords();
    }
}
