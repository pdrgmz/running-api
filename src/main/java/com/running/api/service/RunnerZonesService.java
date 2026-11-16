package com.running.api.service;

import com.running.api.dto.HeartRateZoneDto;
import com.running.api.dto.HeartRateZonesProfileDto;
import com.running.api.dto.PaceZoneDto;
import com.running.api.dto.PaceZonesProfileDto;
import com.running.api.dto.RunnerZonesResponseDto;
import com.running.api.dto.UpdateRunnerZonesRequestDto;
import com.running.api.model.Activity;
import com.running.api.model.HrZoneMethod;
import com.running.api.model.PaceZoneMethod;
import com.running.api.model.PersonalRecord;
import com.running.api.model.RunnerZoneProfile;
import com.running.api.model.RunnerZoneRange;
import com.running.api.model.ZoneProfileSource;
import com.running.api.model.ZoneRangeType;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.PersonalRecordRepository;
import com.running.api.repository.RunnerZoneProfileRepository;
import com.running.api.util.RunningMetrics;
import com.running.api.util.ZoneProfileCalculator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil de zonas del corredor.
 *
 * <p>El perfil combina zonas de frecuencia cardíaca y de ritmo. Se puede obtener (con valores
 * por defecto si aún no está guardado), recalcular desde el historial y guardar, o editar
 * manualmente. El cálculo de FC usa la FC máxima robusta de las sesiones del atleta y el
 * cálculo de ritmo usa sus marcas personales, con las actividades recientes como respaldo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RunnerZonesService {

    private static final int DEFAULT_RESTING_HEART_RATE = 60;
    private static final int FALLBACK_MAX_HEART_RATE = 185;

    private static final int MIN_MAX_HEART_RATE = 100;
    private static final int MAX_MAX_HEART_RATE = 240;
    private static final int MIN_RESTING_HEART_RATE = 30;
    private static final int MAX_RESTING_HEART_RATE = 100;
    private static final int MIN_HEART_RATE_ZONE_BPM = 30;
    private static final int MAX_HEART_RATE_ZONE_BPM = 250;

    private static final double MIN_THRESHOLD_PACE_SECONDS = 120.0; // 2:00 min/km
    private static final double MAX_THRESHOLD_PACE_SECONDS = 1200.0; // 20:00 min/km
    private static final double MIN_ZONE_PACE_SECONDS = 120.0;
    private static final double MAX_ZONE_PACE_SECONDS = 1800.0; // 30:00 min/km
    private static final double ACTIVITY_PACE_TO_THRESHOLD_FACTOR = 1.05;

    /**
     * Conversión de la mejor marca personal a ritmo umbral. El 10k es la referencia directa;
     * las distancias más cortas se corrigen hacia arriba (umbral más lento) y las más largas
     * hacia abajo (umbral más rápido).
     */
    private static final Map<String, Double> RECORD_PACE_FACTORS = new LinkedHashMap<>();

    static {
        RECORD_PACE_FACTORS.put("10k", 1.00);
        RECORD_PACE_FACTORS.put("5k", 1.08);
        RECORD_PACE_FACTORS.put("15k", 0.98);
        RECORD_PACE_FACTORS.put("21k", 0.95);
        RECORD_PACE_FACTORS.put("1k", 1.15);
        RECORD_PACE_FACTORS.put("42k", 0.92);
    }

    private final ActivityRepository activityRepository;
    private final PersonalRecordRepository personalRecordRepository;
    private final RunnerZoneProfileRepository zoneProfileRepository;

    @Value("${app.user.max-hr:185}")
    private Integer configuredMaxHr;

    /**
     * Devuelve el perfil guardado o, si no existe, un perfil por defecto calculado al vuelo
     * (FC máxima robusta del historial y marcas personales) sin persistirlo.
     */
    @Transactional(readOnly = true)
    public RunnerZonesResponseDto getZones(Long userId) {
        return zoneProfileRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(() -> defaultResponse(userId));
    }

    /**
     * Recalcula las zonas desde el historial y las guarda. La FC máxima sale de los picos
     * robustos de las sesiones; el ritmo umbral, de las marcas personales o, si no hay,
     * de la actividad elegible más rápida. Los parámetros manuales que no se pueden derivar
     * (FC de reposo y ritmo umbral ya guardados) se conservan.
     */
    @Transactional
    public RunnerZonesResponseDto calculateAndSave(Long userId) {
        RunnerZoneProfile profile = zoneProfileRepository.findByUserId(userId)
                .orElseGet(() -> RunnerZoneProfile.builder().userId(userId).build());

        int maxHeartRate = resolveCalculatedMaxHeartRate(userId, profile.getMaxHeartRate());
        int restingHeartRate = saneRestingHeartRate(profile.getRestingHeartRate(), maxHeartRate);
        HrZoneMethod heartRateMethod = calculatedHeartRateMethod(profile.getHeartRateMethod());

        Double thresholdPace = resolveCalculatedThresholdPace(userId);
        if (thresholdPace == null) {
            thresholdPace = profile.getThresholdPaceSecondsPerKm();
        }
        PaceZoneMethod paceMethod = calculatedPaceMethod(profile.getPaceMethod());

        profile.setUserId(userId);
        profile.setMaxHeartRate(maxHeartRate);
        profile.setRestingHeartRate(restingHeartRate);
        profile.setHeartRateMethod(heartRateMethod);
        profile.setSource(ZoneProfileSource.CALCULATED);
        profile.setCalculatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());

        List<RunnerZoneRange> heartRateRanges = ZoneProfileCalculator
                .standardHeartRateZones(maxHeartRate, restingHeartRate, heartRateMethod)
                .stream()
                .map(zone -> range(ZoneRangeType.HEART_RATE, zone.zone(), zone.minBpm(), zone.maxBpm()))
                .toList();
        replaceRanges(profile, ZoneRangeType.HEART_RATE, heartRateRanges);

        if (thresholdPace != null && isPlausibleThresholdPace(thresholdPace)) {
            double normalizedThreshold = roundOne(thresholdPace);
            profile.setThresholdPaceSecondsPerKm(normalizedThreshold);
            profile.setPaceMethod(paceMethod);

            List<RunnerZoneRange> paceRanges = ZoneProfileCalculator.standardPaceZones(normalizedThreshold)
                    .stream()
                    .map(zone -> range(ZoneRangeType.PACE, zone.zone(),
                            zone.fastestPaceSecondsPerKm(), zone.slowestPaceSecondsPerKm()))
                    .toList();
            replaceRanges(profile, ZoneRangeType.PACE, paceRanges);
        } else {
            profile.setThresholdPaceSecondsPerKm(null);
            profile.setPaceMethod(paceMethod);
            replaceRanges(profile, ZoneRangeType.PACE, List.of());
        }

        log.info("Zonas del corredor recalculadas para el usuario {}: FC máx {} bpm, ritmo umbral {}",
                userId, maxHeartRate, thresholdPace);
        return toResponse(zoneProfileRepository.save(profile));
    }

    /**
     * Edita manualmente el perfil. Las secciones omitidas conservan sus valores actuales.
     * Si una sección incluye {@code zones}, se validan los cinco rangos y el método pasa a
     * {@code CUSTOM}; si no, los rangos se recalculan con el método indicado.
     */
    @Transactional
    public RunnerZonesResponseDto updateZones(Long userId, UpdateRunnerZonesRequestDto request) {
        if (request == null || (request.heartRate() == null && request.pace() == null)) {
            throw new IllegalArgumentException("Debes enviar al menos heartRate o pace");
        }

        RunnerZoneProfile profile = zoneProfileRepository.findByUserId(userId)
                .orElseGet(() -> RunnerZoneProfile.builder().userId(userId).build());

        int maxHeartRate = profile.getMaxHeartRate() != null
                ? profile.getMaxHeartRate() : configuredMaxHeartRate();
        int restingHeartRate = saneRestingHeartRate(profile.getRestingHeartRate(), maxHeartRate);
        HrZoneMethod heartRateMethod = storedHeartRateMethod(profile.getHeartRateMethod());
        List<RunnerZoneRange> heartRateRanges = rangesOf(profile, ZoneRangeType.HEART_RATE);

        if (request.heartRate() != null) {
            UpdateRunnerZonesRequestDto.HeartRateZones heartRate = request.heartRate();
            if (heartRate.maxHeartRate() != null) {
                maxHeartRate = heartRate.maxHeartRate();
            }
            if (heartRate.restingHeartRate() != null) {
                restingHeartRate = heartRate.restingHeartRate();
            }
            validatePhysiology(maxHeartRate, restingHeartRate);

            HrZoneMethod requestedMethod = heartRate.method() != null
                    ? heartRate.method() : heartRateMethod;

            if (heartRate.zones() != null) {
                requireCustomMethod(heartRate.method());
                requestedMethod = HrZoneMethod.CUSTOM;
                validateHeartRateRanges(heartRate.zones());
                heartRateRanges = heartRate.zones().stream()
                        .sorted(Comparator.comparing(UpdateRunnerZonesRequestDto.HeartRateZoneRange::zone))
                        .map(zone -> range(ZoneRangeType.HEART_RATE, zone.zone(),
                                zone.minBpm(), zone.maxBpm()))
                        .toList();
            } else if (requestedMethod == HrZoneMethod.CUSTOM) {
                if (heartRateRanges.size() != ZoneProfileCalculator.ZONE_COUNT) {
                    throw new IllegalArgumentException(
                            "method=CUSTOM requiere enviar las 5 zonas de frecuencia cardíaca");
                }
            } else {
                heartRateRanges = ZoneProfileCalculator
                        .standardHeartRateZones(maxHeartRate, restingHeartRate, requestedMethod)
                        .stream()
                        .map(zone -> range(ZoneRangeType.HEART_RATE, zone.zone(), zone.minBpm(), zone.maxBpm()))
                        .toList();
            }
            heartRateMethod = requestedMethod;
        } else if (heartRateRanges.size() != ZoneProfileCalculator.ZONE_COUNT) {
            heartRateMethod = calculatedHeartRateMethod(heartRateMethod);
            heartRateRanges = ZoneProfileCalculator
                    .standardHeartRateZones(maxHeartRate, restingHeartRate, heartRateMethod)
                    .stream()
                    .map(zone -> range(ZoneRangeType.HEART_RATE, zone.zone(), zone.minBpm(), zone.maxBpm()))
                    .toList();
        }

        Double thresholdPace = profile.getThresholdPaceSecondsPerKm();
        PaceZoneMethod paceMethod = storedPaceMethod(profile.getPaceMethod());
        List<RunnerZoneRange> paceRanges = rangesOf(profile, ZoneRangeType.PACE);

        if (request.pace() != null) {
            UpdateRunnerZonesRequestDto.PaceZones pace = request.pace();
            if (pace.thresholdPaceSecondsPerKm() != null) {
                thresholdPace = pace.thresholdPaceSecondsPerKm();
            }
            if (thresholdPace == null) {
                throw new IllegalArgumentException(
                        "thresholdPaceSecondsPerKm es obligatorio para definir las zonas de ritmo");
            }
            validateThresholdPace(thresholdPace);

            PaceZoneMethod requestedMethod = pace.method() != null ? pace.method() : paceMethod;

            if (pace.zones() != null) {
                requireCustomMethod(pace.method());
                requestedMethod = PaceZoneMethod.CUSTOM;
                validatePaceRanges(pace.zones());
                paceRanges = pace.zones().stream()
                        .sorted(Comparator.comparing(UpdateRunnerZonesRequestDto.PaceZoneRange::zone))
                        .map(zone -> range(ZoneRangeType.PACE, zone.zone(),
                                zone.fastestPaceSecondsPerKm(), zone.slowestPaceSecondsPerKm()))
                        .toList();
            } else if (requestedMethod == PaceZoneMethod.CUSTOM) {
                if (paceRanges.size() != ZoneProfileCalculator.ZONE_COUNT) {
                    throw new IllegalArgumentException(
                            "method=CUSTOM requiere enviar las 5 zonas de ritmo");
                }
            } else {
                paceRanges = ZoneProfileCalculator.standardPaceZones(thresholdPace)
                        .stream()
                        .map(zone -> range(ZoneRangeType.PACE, zone.zone(),
                                zone.fastestPaceSecondsPerKm(), zone.slowestPaceSecondsPerKm()))
                        .toList();
            }
            paceMethod = requestedMethod;
        } else if (thresholdPace != null
                && isPlausibleThresholdPace(thresholdPace)
                && paceRanges.size() != ZoneProfileCalculator.ZONE_COUNT) {
            paceMethod = calculatedPaceMethod(paceMethod);
            paceRanges = ZoneProfileCalculator.standardPaceZones(thresholdPace)
                    .stream()
                    .map(zone -> range(ZoneRangeType.PACE, zone.zone(),
                            zone.fastestPaceSecondsPerKm(), zone.slowestPaceSecondsPerKm()))
                    .toList();
        }

        profile.setUserId(userId);
        profile.setMaxHeartRate(maxHeartRate);
        profile.setRestingHeartRate(restingHeartRate);
        profile.setHeartRateMethod(heartRateMethod);
        profile.setThresholdPaceSecondsPerKm(thresholdPace);
        profile.setPaceMethod(paceMethod);
        profile.setSource(ZoneProfileSource.MANUAL);
        profile.setUpdatedAt(LocalDateTime.now());
        replaceRanges(profile, ZoneRangeType.HEART_RATE, heartRateRanges);
        replaceRanges(profile, ZoneRangeType.PACE, paceRanges);

        log.info("Zonas del corredor actualizadas manualmente para el usuario {}", userId);
        return toResponse(zoneProfileRepository.save(profile));
    }

    private RunnerZonesResponseDto defaultResponse(Long userId) {
        int maxHeartRate = resolveCalculatedMaxHeartRate(userId, null);
        int restingHeartRate = DEFAULT_RESTING_HEART_RATE;
        HrZoneMethod heartRateMethod = HrZoneMethod.MAX_HR_PERCENT;

        HeartRateZonesProfileDto heartRate = new HeartRateZonesProfileDto(
                maxHeartRate,
                restingHeartRate,
                heartRateMethod,
                ZoneProfileCalculator.standardHeartRateZones(
                        maxHeartRate, restingHeartRate, heartRateMethod));

        Double thresholdPace = findThresholdPaceFromRecords(userId);
        PaceZonesProfileDto pace = null;
        if (thresholdPace != null) {
            pace = new PaceZonesProfileDto(
                    thresholdPace,
                    ZoneProfileCalculator.formatPace(thresholdPace),
                    PaceZoneMethod.THRESHOLD_PACE_PERCENT,
                    ZoneProfileCalculator.standardPaceZones(thresholdPace));
        }

        return new RunnerZonesResponseDto(false, ZoneProfileSource.DEFAULT,
                null, null, heartRate, pace);
    }

    private RunnerZonesResponseDto toResponse(RunnerZoneProfile profile) {
        int maxHeartRate = isValidMaxHeartRate(profile.getMaxHeartRate())
                ? profile.getMaxHeartRate() : configuredMaxHeartRate();
        int restingHeartRate = saneRestingHeartRate(profile.getRestingHeartRate(), maxHeartRate);

        HrZoneMethod heartRateMethod = storedHeartRateMethod(profile.getHeartRateMethod());
        HrZoneMethod zoneCalculationMethod = heartRateMethod;
        List<RunnerZoneRange> heartRateRanges = rangesOf(profile, ZoneRangeType.HEART_RATE);
        List<HeartRateZoneDto> heartRateZones;

        if (heartRateRanges.size() == ZoneProfileCalculator.ZONE_COUNT) {
            heartRateZones = heartRateRanges.stream()
                    .map(range -> ZoneProfileCalculator.heartRateZone(
                            range.getZoneNumber(),
                            range.getMinValue().intValue(),
                            range.getMaxValue().intValue(),
                            maxHeartRate,
                            restingHeartRate,
                            zoneCalculationMethod))
                    .toList();
        } else {
            heartRateMethod = calculatedHeartRateMethod(heartRateMethod);
            heartRateZones = ZoneProfileCalculator.standardHeartRateZones(
                    maxHeartRate, restingHeartRate, heartRateMethod);
        }

        HeartRateZonesProfileDto heartRate = new HeartRateZonesProfileDto(
                maxHeartRate, restingHeartRate, heartRateMethod, heartRateZones);

        PaceZonesProfileDto pace = null;
        Double thresholdPace = profile.getThresholdPaceSecondsPerKm();
        List<RunnerZoneRange> paceRanges = rangesOf(profile, ZoneRangeType.PACE);
        if (thresholdPace != null && isPlausibleThresholdPace(thresholdPace)) {
            PaceZoneMethod paceMethod = storedPaceMethod(profile.getPaceMethod());
            List<PaceZoneDto> paceZones;

            if (paceRanges.size() == ZoneProfileCalculator.ZONE_COUNT) {
                paceZones = paceRanges.stream()
                        .map(range -> ZoneProfileCalculator.paceZone(
                                range.getZoneNumber(),
                                range.getMinValue(),
                                range.getMaxValue(),
                                thresholdPace))
                        .toList();
            } else {
                paceMethod = calculatedPaceMethod(paceMethod);
                paceZones = ZoneProfileCalculator.standardPaceZones(thresholdPace);
            }

            pace = new PaceZonesProfileDto(
                    roundOne(thresholdPace),
                    ZoneProfileCalculator.formatPace(thresholdPace),
                    paceMethod,
                    paceZones);
        }

        ZoneProfileSource source = profile.getSource() != null
                ? profile.getSource() : ZoneProfileSource.MANUAL;

        return new RunnerZonesResponseDto(true, source,
                profile.getCalculatedAt(), profile.getUpdatedAt(), heartRate, pace);
    }

    private int resolveCalculatedMaxHeartRate(Long userId, Integer storedMaxHeartRate) {
        int robust = RunningMetrics.robustMaxHeartRate(
                activityRepository.findMaxHeartRatesByUserId(userId));
        if (isValidMaxHeartRate(robust)) {
            return robust;
        }
        if (isValidMaxHeartRate(storedMaxHeartRate)) {
            return storedMaxHeartRate;
        }
        return configuredMaxHeartRate();
    }

    private Double resolveCalculatedThresholdPace(Long userId) {
        Double fromRecords = findThresholdPaceFromRecords(userId);
        if (fromRecords != null) {
            return fromRecords;
        }
        return findThresholdPaceFromActivities(userId);
    }

    private Double findThresholdPaceFromRecords(Long userId) {
        List<PersonalRecord> records = personalRecordRepository.findByUserId(userId);
        if (records == null || records.isEmpty()) {
            return null;
        }

        Map<String, PersonalRecord> byCategory = records.stream()
                .filter(record -> record.getDistanceCategory() != null)
                .collect(Collectors.toMap(
                        PersonalRecord::getDistanceCategory,
                        record -> record,
                        (first, second) -> first));

        for (Map.Entry<String, Double> entry : RECORD_PACE_FACTORS.entrySet()) {
            PersonalRecord record = byCategory.get(entry.getKey());
            if (record == null) {
                continue;
            }

            Double bestTime = record.getBestTimeSeconds();
            Double targetDistance = record.getTargetDistanceMeters();
            if (bestTime == null || targetDistance == null || bestTime <= 0 || targetDistance <= 0) {
                continue;
            }

            double paceSecondsPerKm = bestTime / (targetDistance / 1000.0);
            double thresholdPace = paceSecondsPerKm * entry.getValue();
            if (isPlausibleThresholdPace(thresholdPace)) {
                return roundOne(thresholdPace);
            }
        }

        return null;
    }

    private Double findThresholdPaceFromActivities(Long userId) {
        List<Activity> activities = activityRepository.findByUserIdOrderByStartTimeDesc(userId);
        Double bestPace = null;

        for (Activity activity : activities) {
            if (activity.getDistanceMeters() == null || activity.getTotalTimeSeconds() == null) {
                continue;
            }
            if (activity.getDistanceMeters() < 5000.0 || activity.getTotalTimeSeconds() < 1200.0) {
                continue;
            }

            double paceSecondsPerKm = activity.getTotalTimeSeconds()
                    / (activity.getDistanceMeters() / 1000.0);
            if (!isPlausibleThresholdPace(paceSecondsPerKm)) {
                continue;
            }
            if (bestPace == null || paceSecondsPerKm < bestPace) {
                bestPace = paceSecondsPerKm;
            }
        }

        if (bestPace == null) {
            return null;
        }

        double thresholdPace = bestPace * ACTIVITY_PACE_TO_THRESHOLD_FACTOR;
        return isPlausibleThresholdPace(thresholdPace) ? roundOne(thresholdPace) : null;
    }

    private List<RunnerZoneRange> rangesOf(RunnerZoneProfile profile, ZoneRangeType type) {
        if (profile.getZones() == null || profile.getZones().isEmpty()) {
            return List.of();
        }
        return profile.getZones().stream()
                .filter(range -> range.getZoneType() == type)
                .sorted(Comparator.comparing(RunnerZoneRange::getZoneNumber))
                .toList();
    }

    private void replaceRanges(RunnerZoneProfile profile, ZoneRangeType type,
                               List<RunnerZoneRange> ranges) {
        if (profile.getZones() == null) {
            profile.setZones(new ArrayList<>());
        }
        profile.getZones().removeIf(range -> range.getZoneType() == type);
        for (RunnerZoneRange range : ranges) {
            range.setProfile(profile);
            profile.getZones().add(range);
        }
    }

    private RunnerZoneRange range(ZoneRangeType type, int zone, double minValue, double maxValue) {
        return RunnerZoneRange.builder()
                .zoneType(type)
                .zoneNumber(zone)
                .minValue(minValue)
                .maxValue(maxValue)
                .build();
    }

    private void validatePhysiology(int maxHeartRate, int restingHeartRate) {
        if (!isValidMaxHeartRate(maxHeartRate)) {
            throw new IllegalArgumentException(
                    "La FC máxima debe estar entre " + MIN_MAX_HEART_RATE + " y " + MAX_MAX_HEART_RATE + " bpm");
        }
        if (restingHeartRate < MIN_RESTING_HEART_RATE || restingHeartRate > MAX_RESTING_HEART_RATE) {
            throw new IllegalArgumentException(
                    "La FC de reposo debe estar entre " + MIN_RESTING_HEART_RATE + " y " + MAX_RESTING_HEART_RATE + " bpm");
        }
        if (restingHeartRate >= maxHeartRate - 10) {
            throw new IllegalArgumentException(
                    "La FC de reposo debe ser al menos 10 bpm inferior a la FC máxima");
        }
    }

    private void validateHeartRateRanges(List<UpdateRunnerZonesRequestDto.HeartRateZoneRange> zones) {
        if (zones == null || zones.size() != ZoneProfileCalculator.ZONE_COUNT) {
            throw new IllegalArgumentException("Debes enviar exactamente 5 zonas de frecuencia cardíaca");
        }

        List<UpdateRunnerZonesRequestDto.HeartRateZoneRange> sorted = new ArrayList<>(zones);
        sorted.sort(Comparator.comparingInt(zone -> zone.zone() == null ? Integer.MAX_VALUE : zone.zone()));
        boolean[] seen = new boolean[ZoneProfileCalculator.ZONE_COUNT + 1];

        for (UpdateRunnerZonesRequestDto.HeartRateZoneRange zone : sorted) {
            if (zone.zone() == null || zone.zone() < 1 || zone.zone() > ZoneProfileCalculator.ZONE_COUNT) {
                throw new IllegalArgumentException("El número de zona debe estar entre 1 y 5");
            }
            if (seen[zone.zone()]) {
                throw new IllegalArgumentException("La zona " + zone.zone() + " está repetida");
            }
            seen[zone.zone()] = true;

            if (zone.minBpm() == null || zone.maxBpm() == null) {
                throw new IllegalArgumentException("Cada zona debe indicar minBpm y maxBpm");
            }
            if (zone.minBpm() < MIN_HEART_RATE_ZONE_BPM || zone.maxBpm() > MAX_HEART_RATE_ZONE_BPM) {
                throw new IllegalArgumentException(
                        "Los límites de las zonas deben estar entre " + MIN_HEART_RATE_ZONE_BPM
                                + " y " + MAX_HEART_RATE_ZONE_BPM + " bpm");
            }
            if (zone.minBpm() >= zone.maxBpm()) {
                throw new IllegalArgumentException(
                        "La zona " + zone.zone() + " debe tener minBpm menor que maxBpm");
            }
        }

        for (int i = 0; i < sorted.size() - 1; i++) {
            if (sorted.get(i).maxBpm() > sorted.get(i + 1).minBpm()) {
                throw new IllegalArgumentException(
                        "Las zonas de FC no pueden solaparse: Z" + sorted.get(i).zone()
                                + " termina antes de que empiece Z" + sorted.get(i + 1).zone());
            }
        }
    }

    private void validatePaceRanges(List<UpdateRunnerZonesRequestDto.PaceZoneRange> zones) {
        if (zones == null || zones.size() != ZoneProfileCalculator.ZONE_COUNT) {
            throw new IllegalArgumentException("Debes enviar exactamente 5 zonas de ritmo");
        }

        List<UpdateRunnerZonesRequestDto.PaceZoneRange> sorted = new ArrayList<>(zones);
        sorted.sort(Comparator.comparingInt(zone -> zone.zone() == null ? Integer.MAX_VALUE : zone.zone()));
        boolean[] seen = new boolean[ZoneProfileCalculator.ZONE_COUNT + 1];

        for (UpdateRunnerZonesRequestDto.PaceZoneRange zone : sorted) {
            if (zone.zone() == null || zone.zone() < 1 || zone.zone() > ZoneProfileCalculator.ZONE_COUNT) {
                throw new IllegalArgumentException("El número de zona debe estar entre 1 y 5");
            }
            if (seen[zone.zone()]) {
                throw new IllegalArgumentException("La zona " + zone.zone() + " está repetida");
            }
            seen[zone.zone()] = true;

            if (zone.fastestPaceSecondsPerKm() == null || zone.slowestPaceSecondsPerKm() == null) {
                throw new IllegalArgumentException(
                        "Cada zona debe indicar fastestPaceSecondsPerKm y slowestPaceSecondsPerKm");
            }
            if (zone.fastestPaceSecondsPerKm() < MIN_ZONE_PACE_SECONDS
                    || zone.slowestPaceSecondsPerKm() > MAX_ZONE_PACE_SECONDS) {
                throw new IllegalArgumentException(
                        "Los ritmos de las zonas deben estar entre " + MIN_ZONE_PACE_SECONDS
                                + " y " + MAX_ZONE_PACE_SECONDS + " segundos/km");
            }
            if (zone.fastestPaceSecondsPerKm() >= zone.slowestPaceSecondsPerKm()) {
                throw new IllegalArgumentException(
                        "La zona " + zone.zone()
                                + " debe tener un ritmo rápido menor que el ritmo lento");
            }
        }

        for (int i = 0; i < sorted.size() - 1; i++) {
            if (sorted.get(i).fastestPaceSecondsPerKm() < sorted.get(i + 1).slowestPaceSecondsPerKm()) {
                throw new IllegalArgumentException(
                        "Las zonas de ritmo no pueden solaparse: Z" + sorted.get(i).zone()
                                + " debe ser más lenta que Z" + sorted.get(i + 1).zone());
            }
        }
    }

    private void validateThresholdPace(double thresholdPace) {
        if (!isPlausibleThresholdPace(thresholdPace)) {
            throw new IllegalArgumentException(
                    "El ritmo umbral debe estar entre " + ZoneProfileCalculator.formatPace(MIN_THRESHOLD_PACE_SECONDS)
                            + " y " + ZoneProfileCalculator.formatPace(MAX_THRESHOLD_PACE_SECONDS) + " min/km");
        }
    }

    private void requireCustomMethod(HrZoneMethod method) {
        if (method != null && method != HrZoneMethod.CUSTOM) {
            throw new IllegalArgumentException(
                    "Si envías zonas de frecuencia cardíaca personalizadas, method debe ser CUSTOM");
        }
    }

    private void requireCustomMethod(PaceZoneMethod method) {
        if (method != null && method != PaceZoneMethod.CUSTOM) {
            throw new IllegalArgumentException(
                    "Si envías zonas de ritmo personalizadas, method debe ser CUSTOM");
        }
    }

    private int saneRestingHeartRate(Integer storedRestingHeartRate, int maxHeartRate) {
        if (storedRestingHeartRate != null
                && storedRestingHeartRate >= MIN_RESTING_HEART_RATE
                && storedRestingHeartRate <= MAX_RESTING_HEART_RATE
                && storedRestingHeartRate < maxHeartRate - 10) {
            return storedRestingHeartRate;
        }

        int fallback = DEFAULT_RESTING_HEART_RATE;
        if (fallback >= maxHeartRate - 10) {
            fallback = Math.max(MIN_RESTING_HEART_RATE, maxHeartRate - 20);
        }
        return fallback;
    }

    private int configuredMaxHeartRate() {
        int value = configuredMaxHr != null ? configuredMaxHr : FALLBACK_MAX_HEART_RATE;
        return isValidMaxHeartRate(value) ? value : FALLBACK_MAX_HEART_RATE;
    }

    private boolean isValidMaxHeartRate(Integer maxHeartRate) {
        return maxHeartRate != null
                && maxHeartRate >= MIN_MAX_HEART_RATE
                && maxHeartRate <= MAX_MAX_HEART_RATE;
    }

    private boolean isPlausibleThresholdPace(double thresholdPace) {
        return Double.isFinite(thresholdPace)
                && thresholdPace >= MIN_THRESHOLD_PACE_SECONDS
                && thresholdPace <= MAX_THRESHOLD_PACE_SECONDS;
    }

    private HrZoneMethod calculatedHeartRateMethod(HrZoneMethod method) {
        return method == null || method == HrZoneMethod.CUSTOM
                ? HrZoneMethod.MAX_HR_PERCENT : method;
    }

    private HrZoneMethod storedHeartRateMethod(HrZoneMethod method) {
        return method != null ? method : HrZoneMethod.MAX_HR_PERCENT;
    }

    private PaceZoneMethod calculatedPaceMethod(PaceZoneMethod method) {
        return method == null || method == PaceZoneMethod.CUSTOM
                ? PaceZoneMethod.THRESHOLD_PACE_PERCENT : method;
    }

    private PaceZoneMethod storedPaceMethod(PaceZoneMethod method) {
        return method != null ? method : PaceZoneMethod.THRESHOLD_PACE_PERCENT;
    }

    private static double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
