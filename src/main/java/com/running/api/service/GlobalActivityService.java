package com.running.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.running.api.dto.ElevationCoverageDto;
import com.running.api.repository.ActivityTrackpointRepository;
import com.running.api.repository.projection.ElevationCoverageProjection;
import com.running.api.util.AltitudeUtils;
import com.running.api.util.FlatAltitudeDetector;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GlobalActivityService {

    private final ActivityTrackpointRepository trackpointRepository;

    @Transactional(readOnly = true)
    public ElevationCoverageDto getElevationCoverage(Long userId) {
        ElevationCoverageProjection coverage = trackpointRepository.getElevationCoverage(
                userId, AltitudeUtils.ALTITUDE_EPSILON);

        long total = coverage == null || coverage.getTotalTrackpoints() == null
                ? 0L
                : coverage.getTotalTrackpoints();
        long withElevation = coverage == null || coverage.getTrackpointsWithElevation() == null
                ? 0L
                : coverage.getTrackpointsWithElevation();

        long flatElevation = trackpointRepository.findFlatElevationPoints(
                        userId,
                        AltitudeUtils.ALTITUDE_EPSILON,
                        FlatAltitudeDetector.FLAT_ALTITUDE_PERCENT,
                        FlatAltitudeDetector.MIN_FLAT_ALTITUDE_POINTS)
                .stream()
                .mapToLong(point -> point.getOccurrences() == null ? 0L : point.getOccurrences())
                .sum();

        return buildElevationCoverage(total, withElevation, flatElevation);
    }

    static ElevationCoverageDto buildElevationCoverage(long total, long withElevation,
                                                       long flatElevation) {
        long validWithElevation = Math.min(Math.max(withElevation, 0L), total);
        long validFlatElevation = Math.min(Math.max(flatElevation, 0L), validWithElevation);
        long validElevation = validWithElevation - validFlatElevation;
        long missing = Math.max(0L, total - validElevation);
        double withPercent = total == 0
                ? 0.0
                : roundPercentage(validElevation * 100.0 / total);
        double missingPercent = total == 0
                ? 0.0
                : roundPercentage(100.0 - withPercent);
        double flatPercent = total == 0
                ? 0.0
                : roundPercentage(validFlatElevation * 100.0 / total);

        return ElevationCoverageDto.builder()
                .totalTrackpoints(total)
                .trackpointsWithElevation(validElevation)
                .trackpointsMissingElevation(missing)
                .trackpointsWithFlatElevation(validFlatElevation)
                .withElevationPercent(withPercent)
                .missingElevationPercent(missingPercent)
                .flatElevationPercent(flatPercent)
                .build();
    }

    private static double roundPercentage(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
