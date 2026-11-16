package com.running.api.util;

import com.running.api.dto.HeartRateZoneDto;
import com.running.api.dto.PaceZoneDto;
import com.running.api.model.HrZoneMethod;
import java.util.ArrayList;
import java.util.List;

/**
 * Construcción de las zonas estándar del corredor.
 *
 * <p>Las zonas de FC usan los cortes clásicos 50-60-70-80-90-100 % de la FC máxima o de la
 * reserva de FC (Karvonen). Las zonas de ritmo se expresan como porcentajes del ritmo umbral
 * en segundos por kilómetro: Z1 es la más lenta y Z5 la más rápida.
 */
public final class ZoneProfileCalculator {

    public static final int ZONE_COUNT = 5;

    private static final int[] HEART_RATE_BOUNDARIES_PERCENT = {50, 60, 70, 80, 90, 100};

    /**
     * Cortes de ritmo de lento a rápido. Para la zona i, el límite lento es
     * {@code PACE_BOUNDARIES_PERCENT[i]} y el rápido {@code PACE_BOUNDARIES_PERCENT[i + 1]}.
     */
    private static final double[] PACE_BOUNDARIES_PERCENT = {150, 130, 115, 105, 95, 85};

    private static final String[] ZONE_NAMES = {
            "Recuperación",
            "Resistencia",
            "Tempo",
            "Umbral",
            "Anaeróbico"
    };

    private ZoneProfileCalculator() {
    }

    public static List<HeartRateZoneDto> standardHeartRateZones(int maxHeartRate,
                                                                int restingHeartRate,
                                                                HrZoneMethod method) {
        if (method == null || method == HrZoneMethod.CUSTOM) {
            throw new IllegalArgumentException(
                    "Las zonas de FC estándar requieren el método MAX_HR_PERCENT o HEART_RATE_RESERVE");
        }

        List<HeartRateZoneDto> zones = new ArrayList<>(ZONE_COUNT);
        for (int i = 0; i < ZONE_COUNT; i++) {
            int minBpm = toBpm(HEART_RATE_BOUNDARIES_PERCENT[i], maxHeartRate, restingHeartRate, method);
            int maxBpm = toBpm(HEART_RATE_BOUNDARIES_PERCENT[i + 1], maxHeartRate, restingHeartRate, method);
            if (minBpm >= maxBpm) {
                maxBpm = minBpm + 1;
            }
            zones.add(heartRateZone(i + 1, minBpm, maxBpm, maxHeartRate, restingHeartRate, method));
        }
        return zones;
    }

    public static List<PaceZoneDto> standardPaceZones(double thresholdPaceSecondsPerKm) {
        List<PaceZoneDto> zones = new ArrayList<>(ZONE_COUNT);
        for (int i = 0; i < ZONE_COUNT; i++) {
            double slowest = thresholdPaceSecondsPerKm * PACE_BOUNDARIES_PERCENT[i] / 100.0;
            double fastest = thresholdPaceSecondsPerKm * PACE_BOUNDARIES_PERCENT[i + 1] / 100.0;
            zones.add(paceZone(i + 1, fastest, slowest, thresholdPaceSecondsPerKm));
        }
        return zones;
    }

    public static HeartRateZoneDto heartRateZone(int zone,
                                                 int minBpm,
                                                 int maxBpm,
                                                 int maxHeartRate,
                                                 int restingHeartRate,
                                                 HrZoneMethod method) {
        double minPercent;
        double maxPercent;

        if (method == HrZoneMethod.HEART_RATE_RESERVE && maxHeartRate > restingHeartRate) {
            minPercent = roundOne((minBpm - restingHeartRate) * 100.0 / (maxHeartRate - restingHeartRate));
            maxPercent = roundOne((maxBpm - restingHeartRate) * 100.0 / (maxHeartRate - restingHeartRate));
        } else if (maxHeartRate > 0) {
            minPercent = roundOne(minBpm * 100.0 / maxHeartRate);
            maxPercent = roundOne(maxBpm * 100.0 / maxHeartRate);
        } else {
            minPercent = 0.0;
            maxPercent = 0.0;
        }

        return new HeartRateZoneDto(zone, zoneName(zone), minBpm, maxBpm, minPercent, maxPercent);
    }

    public static PaceZoneDto paceZone(int zone,
                                       double fastestPaceSecondsPerKm,
                                       double slowestPaceSecondsPerKm,
                                       double thresholdPaceSecondsPerKm) {
        double fastest = roundOne(fastestPaceSecondsPerKm);
        double slowest = roundOne(slowestPaceSecondsPerKm);
        double minPercent = thresholdPaceSecondsPerKm > 0
                ? roundOne(fastest * 100.0 / thresholdPaceSecondsPerKm)
                : 0.0;
        double maxPercent = thresholdPaceSecondsPerKm > 0
                ? roundOne(slowest * 100.0 / thresholdPaceSecondsPerKm)
                : 0.0;

        return new PaceZoneDto(zone, zoneName(zone), fastest, slowest,
                formatPace(fastest), formatPace(slowest), minPercent, maxPercent);
    }

    public static String zoneName(int zone) {
        if (zone >= 1 && zone <= ZONE_NAMES.length) {
            return ZONE_NAMES[zone - 1];
        }
        return "Zona " + zone;
    }

    public static String formatPace(double secondsPerKm) {
        long totalSeconds = Math.round(secondsPerKm);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private static int toBpm(int percent, int maxHeartRate, int restingHeartRate, HrZoneMethod method) {
        double value = method == HrZoneMethod.HEART_RATE_RESERVE
                ? restingHeartRate + (maxHeartRate - restingHeartRate) * percent / 100.0
                : maxHeartRate * percent / 100.0;
        return (int) Math.round(value);
    }

    private static double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
