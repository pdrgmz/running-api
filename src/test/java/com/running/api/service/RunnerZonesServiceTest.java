package com.running.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.running.api.dto.RunnerZonesResponseDto;
import com.running.api.dto.UpdateRunnerZonesRequestDto;
import com.running.api.dto.UpdateRunnerZonesRequestDto.HeartRateZoneRange;
import com.running.api.model.HrZoneMethod;
import com.running.api.model.PaceZoneMethod;
import com.running.api.model.PersonalRecord;
import com.running.api.model.RunnerZoneProfile;
import com.running.api.model.ZoneProfileSource;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.PersonalRecordRepository;
import com.running.api.repository.RunnerZoneProfileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunnerZonesServiceTest {

    private static final Long USER_ID = 1L;

    @Test
    void calculatesAndSavesZonesFromHistory() {
        ActivityRepository activityRepository = mock(ActivityRepository.class);
        when(activityRepository.findMaxHeartRatesByUserId(USER_ID))
                .thenReturn(List.of(190, 192, 188));

        PersonalRecordRepository personalRecordRepository = mock(PersonalRecordRepository.class);
        when(personalRecordRepository.findByUserId(USER_ID)).thenReturn(List.of(
                PersonalRecord.builder()
                        .distanceCategory("10k")
                        .targetDistanceMeters(10_000.0)
                        .bestTimeSeconds(2_700.0)
                        .build()));

        RunnerZoneProfileRepository zoneProfileRepository = mock(RunnerZoneProfileRepository.class);
        when(zoneProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(zoneProfileRepository.save(any(RunnerZoneProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RunnerZonesService service = new RunnerZonesService(
                activityRepository, personalRecordRepository, zoneProfileRepository);

        RunnerZonesResponseDto response = service.calculateAndSave(USER_ID);

        assertThat(response.configured()).isTrue();
        assertThat(response.source()).isEqualTo(ZoneProfileSource.CALCULATED);
        assertThat(response.heartRate().maxHeartRate()).isEqualTo(192);
        assertThat(response.heartRate().restingHeartRate()).isEqualTo(60);
        assertThat(response.heartRate().method()).isEqualTo(HrZoneMethod.MAX_HR_PERCENT);
        assertThat(response.heartRate().zones()).hasSize(5);
        assertThat(response.heartRate().zones().get(0).minBpm()).isEqualTo(96);
        assertThat(response.heartRate().zones().get(0).maxBpm()).isEqualTo(115);

        assertThat(response.pace()).isNotNull();
        assertThat(response.pace().thresholdPaceSecondsPerKm()).isEqualTo(270.0);
        assertThat(response.pace().zones().get(3).fastestPaceSecondsPerKm()).isEqualTo(256.5);
        assertThat(response.pace().zones().get(3).slowestPaceSecondsPerKm()).isEqualTo(283.5);
    }

    @Test
    void updatesCustomHeartRateZonesAndKeepsThePaceProfile() {
        RunnerZoneProfile existing = RunnerZoneProfile.builder()
                .userId(USER_ID)
                .maxHeartRate(190)
                .restingHeartRate(60)
                .heartRateMethod(HrZoneMethod.MAX_HR_PERCENT)
                .thresholdPaceSecondsPerKm(300.0)
                .paceMethod(PaceZoneMethod.THRESHOLD_PACE_PERCENT)
                .source(ZoneProfileSource.CALCULATED)
                .build();

        RunnerZoneProfileRepository zoneProfileRepository = mock(RunnerZoneProfileRepository.class);
        when(zoneProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
        when(zoneProfileRepository.save(any(RunnerZoneProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RunnerZonesService service = new RunnerZonesService(
                mock(ActivityRepository.class),
                mock(PersonalRecordRepository.class),
                zoneProfileRepository);

        RunnerZonesResponseDto response = service.updateZones(USER_ID, new UpdateRunnerZonesRequestDto(
                new UpdateRunnerZonesRequestDto.HeartRateZones(
                        190, 60, HrZoneMethod.CUSTOM,
                        List.of(
                                new HeartRateZoneRange(1, 100, 120),
                                new HeartRateZoneRange(2, 121, 140),
                                new HeartRateZoneRange(3, 141, 160),
                                new HeartRateZoneRange(4, 161, 180),
                                new HeartRateZoneRange(5, 181, 190))),
                null));

        assertThat(response.source()).isEqualTo(ZoneProfileSource.MANUAL);
        assertThat(response.heartRate().method()).isEqualTo(HrZoneMethod.CUSTOM);
        assertThat(response.heartRate().zones().get(0).minBpm()).isEqualTo(100);
        assertThat(response.heartRate().zones().get(0).maxBpm()).isEqualTo(120);
        assertThat(response.pace()).isNotNull();
        assertThat(response.pace().thresholdPaceSecondsPerKm()).isEqualTo(300.0);
        assertThat(response.pace().method()).isEqualTo(PaceZoneMethod.THRESHOLD_PACE_PERCENT);
    }

    @Test
    void rejectsOverlappingCustomHeartRateZones() {
        RunnerZoneProfileRepository zoneProfileRepository = mock(RunnerZoneProfileRepository.class);
        when(zoneProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        RunnerZonesService service = new RunnerZonesService(
                mock(ActivityRepository.class),
                mock(PersonalRecordRepository.class),
                zoneProfileRepository);

        UpdateRunnerZonesRequestDto request = new UpdateRunnerZonesRequestDto(
                new UpdateRunnerZonesRequestDto.HeartRateZones(
                        190, 60, HrZoneMethod.CUSTOM,
                        List.of(
                                new HeartRateZoneRange(1, 100, 150),
                                new HeartRateZoneRange(2, 140, 160),
                                new HeartRateZoneRange(3, 161, 175),
                                new HeartRateZoneRange(4, 176, 185),
                                new HeartRateZoneRange(5, 186, 190))),
                null);

        assertThatThrownBy(() -> service.updateZones(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("solaparse");

        verify(zoneProfileRepository, never()).save(any(RunnerZoneProfile.class));
    }

    @Test
    void rejectsRestingHeartRateTooCloseToMax() {
        RunnerZonesService service = new RunnerZonesService(
                mock(ActivityRepository.class),
                mock(PersonalRecordRepository.class),
                mock(RunnerZoneProfileRepository.class));

        UpdateRunnerZonesRequestDto request = new UpdateRunnerZonesRequestDto(
                new UpdateRunnerZonesRequestDto.HeartRateZones(105, 100, HrZoneMethod.MAX_HR_PERCENT, null),
                null);

        assertThatThrownBy(() -> service.updateZones(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 bpm inferior");
    }

    @Test
    void returnsUnconfiguredDefaultsWithoutSavingWhenThereIsNoProfile() {
        ActivityRepository activityRepository = mock(ActivityRepository.class);
        when(activityRepository.findMaxHeartRatesByUserId(USER_ID)).thenReturn(List.of(190));

        PersonalRecordRepository personalRecordRepository = mock(PersonalRecordRepository.class);
        when(personalRecordRepository.findByUserId(USER_ID)).thenReturn(List.of());

        RunnerZoneProfileRepository zoneProfileRepository = mock(RunnerZoneProfileRepository.class);
        when(zoneProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        RunnerZonesService service = new RunnerZonesService(
                activityRepository, personalRecordRepository, zoneProfileRepository);

        RunnerZonesResponseDto response = service.getZones(USER_ID);

        assertThat(response.configured()).isFalse();
        assertThat(response.source()).isEqualTo(ZoneProfileSource.DEFAULT);
        assertThat(response.heartRate().maxHeartRate()).isEqualTo(190);
        assertThat(response.heartRate().zones()).hasSize(5);
        assertThat(response.pace()).isNull();
        verify(zoneProfileRepository, never()).save(any(RunnerZoneProfile.class));
    }
}
