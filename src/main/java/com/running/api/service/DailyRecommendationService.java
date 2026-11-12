package com.running.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.running.api.dto.DailyGenerationJobResponseDto;
import com.running.api.dto.DailyRecommendationPayload;
import com.running.api.dto.DailyRecommendationResponseDto;
import com.running.api.dto.DailyRecommendationSummaryDto;
import com.running.api.dto.TrainingPreferencesDto;
import com.running.api.dto.UpdateTrainingPreferencesRequestDto;
import com.running.api.dto.ValidationViolationDto;
import com.running.api.exception.GenerationJobStateException;
import com.running.api.exception.RecommendationAlreadyExistsException;
import com.running.api.exception.RecommendationValidationException;
import com.running.api.exception.ResourceNotFoundException;
import com.running.api.model.DailyGenerationJob;
import com.running.api.model.DailyRecommendation;
import com.running.api.model.TrainingJobSource;
import com.running.api.model.TrainingJobStatus;
import com.running.api.model.TrainingPreferences;
import com.running.api.repository.DailyGenerationJobRepository;
import com.running.api.repository.DailyRecommendationRepository;
import com.running.api.repository.TrainingPreferencesRepository;
import com.running.api.util.TrainingTimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class DailyRecommendationService {

    private static final List<TrainingJobStatus> ACTIVE_JOB_STATUSES =
            List.of(TrainingJobStatus.PENDING, TrainingJobStatus.RUNNING);

    private final DailyRecommendationRepository recommendationRepository;
    private final DailyGenerationJobRepository jobRepository;
    private final TrainingPreferencesRepository preferencesRepository;
    private final DailyRecommendationValidator validator;
    private final ObjectMapper objectMapper;
    private final int defaultGenerationHour;

    public DailyRecommendationService(
            DailyRecommendationRepository recommendationRepository,
            DailyGenerationJobRepository jobRepository,
            TrainingPreferencesRepository preferencesRepository,
            DailyRecommendationValidator validator,
            ObjectMapper objectMapper,
            @Value("${app.training.daily.default-generation-hour:6}") int defaultGenerationHour) {
        this.recommendationRepository = recommendationRepository;
        this.jobRepository = jobRepository;
        this.preferencesRepository = preferencesRepository;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.defaultGenerationHour = TrainingTimeUtils.normalizeHour(defaultGenerationHour);
    }

    // ---------------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<DailyRecommendationResponseDto> getToday(Long userId) {
        LocalDate today = LocalDate.now(zoneFor(userId));
        return recommendationRepository.findByUserIdAndRecommendationDate(userId, today)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<DailyRecommendationResponseDto> getRecommendation(Long userId, LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("La fecha es obligatoria");
        }
        return recommendationRepository.findByUserIdAndRecommendationDate(userId, date)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<DailyRecommendationResponseDto> getRecommendationById(Long userId, String recommendationId) {
        return recommendationRepository.findByIdAndUserId(recommendationId, userId)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<DailyRecommendationSummaryDto> listRecommendations(Long userId, LocalDate from, LocalDate to) {
        List<DailyRecommendation> recommendations;
        if (from != null && to != null) {
            recommendations = recommendationRepository
                    .findByUserIdAndRecommendationDateBetweenOrderByRecommendationDateDesc(userId, from, to);
        } else {
            recommendations = recommendationRepository
                    .findTop30ByUserIdOrderByRecommendationDateDesc(userId);
        }
        return recommendations.stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<DailyRecommendationSummaryDto> recentRecommendations(Long userId, int limit) {
        return recommendationRepository.findTop30ByUserIdOrderByRecommendationDateDesc(userId).stream()
                .limit(Math.max(1, limit))
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasRecommendation(Long userId, LocalDate date) {
        return recommendationRepository.existsByUserIdAndRecommendationDate(userId, date);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveJob(Long userId, LocalDate date) {
        return jobRepository.existsByUserIdAndRecommendationDateAndStatusIn(
                userId, date, ACTIVE_JOB_STATUSES);
    }

    // ---------------------------------------------------------------------
    // Borrado
    // ---------------------------------------------------------------------

    @Transactional
    public void deleteRecommendation(Long userId, String recommendationId) {
        DailyRecommendation recommendation = recommendationRepository
                .findByIdAndUserId(recommendationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Recomendación no encontrada: " + recommendationId));
        purgeRecommendations(userId, List.of(recommendation));
    }

    @Transactional
    public void deleteRecommendationByDate(Long userId, LocalDate date) {
        DailyRecommendation recommendation = recommendationRepository
                .findByUserIdAndRecommendationDate(userId, date)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hay recomendación para el día " + date));
        purgeRecommendations(userId, List.of(recommendation));
    }

    private void purgeRecommendations(Long userId, List<DailyRecommendation> recommendations) {
        for (DailyRecommendation recommendation : recommendations) {
            List<DailyGenerationJob> jobs = jobRepository
                    .findByUserIdAndRecommendationId(userId, recommendation.getId());
            if (!jobs.isEmpty()) {
                jobRepository.deleteAllInBatch(jobs);
            }
        }
        recommendationRepository.deleteAllInBatch(recommendations);
        log.info("Eliminadas {} recomendación(es) del userId={}", recommendations.size(), userId);
    }

    // ---------------------------------------------------------------------
    // Guardado transaccional de la recomendación + job
    // ---------------------------------------------------------------------

    /**
     * Guarda la recomendación y marca el job como COMPLETED en la misma
     * transacción. Si cualquiera de las dos escrituras falla, no se persiste
     * ninguna.
     */
    @Transactional
    public DailyRecommendationResponseDto saveDailyRecommendation(Long userId,
                                                                  String requestId,
                                                                  LocalDate date,
                                                                  boolean replace,
                                                                  DailyRecommendationPayload payload) {
        if (requestId == null || requestId.isBlank()) {
            throw new GenerationJobStateException("Falta el requestId del job de generación");
        }

        DailyGenerationJob job = jobRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job de generación no encontrado: " + requestId));

        if (job.getStatus() == TrainingJobStatus.COMPLETED) {
            if (job.getRecommendationId() == null) {
                throw new GenerationJobStateException(
                        "El job está completado pero no tiene recomendación asociada");
            }
            return recommendationRepository
                    .findByIdAndUserId(job.getRecommendationId(), userId)
                    .map(this::toResponse)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Recomendación del job completado no encontrada: " + job.getRecommendationId()));
        }
        if (job.getStatus() == TrainingJobStatus.FAILED
                || job.getStatus() == TrainingJobStatus.TIMEOUT) {
            throw new GenerationJobStateException("El job ya terminó con estado " + job.getStatus());
        }

        LocalDate effectiveDate = date == null ? job.getRecommendationDate() : date;
        if (!effectiveDate.equals(job.getRecommendationDate())) {
            throw new GenerationJobStateException("La fecha (" + effectiveDate
                    + ") no coincide con la fecha del job (" + job.getRecommendationDate() + ")");
        }

        if (job.getStatus() == TrainingJobStatus.PENDING) {
            job.setStatus(TrainingJobStatus.RUNNING);
            job.setStartedAt(LocalDateTime.now());
        }
        job.setAttempts(job.getAttempts() == null ? 1 : job.getAttempts() + 1);
        job.setUpdatedAt(LocalDateTime.now());

        validator.validate(effectiveDate, payload);

        Optional<DailyRecommendation> existing = recommendationRepository
                .findByUserIdAndRecommendationDate(userId, effectiveDate);
        if (existing.isPresent() && !replace) {
            throw new RecommendationAlreadyExistsException(effectiveDate);
        }

        DailyRecommendation recommendation = existing.orElseGet(() -> DailyRecommendation.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .recommendationDate(effectiveDate)
                .build());

        applyPayload(recommendation, requestId, payload);
        recommendation = recommendationRepository.save(recommendation);

        job.setStatus(TrainingJobStatus.COMPLETED);
        job.setRecommendationId(recommendation.getId());
        job.setLastError(null);
        job.setCompletedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        jobRepository.save(job);

        log.info("Recomendación diaria guardada: userId={}, date={}, recommendationId={}, requestId={}",
                userId, effectiveDate, recommendation.getId(), requestId);
        return toResponse(recommendation);
    }

    private void applyPayload(DailyRecommendation recommendation, String requestId,
                              DailyRecommendationPayload payload) {
        DailyRecommendationPayload.WorkoutPayload workout = payload.workout();
        recommendation.setRequestId(requestId);
        recommendation.setType(workout.type());
        recommendation.setTitle(workout.title().trim());
        recommendation.setDescription(workout.description());
        recommendation.setTargetDistanceMeters(workout.targetDistanceMeters());
        recommendation.setTargetDurationSeconds(workout.targetDurationSeconds());
        recommendation.setTargetPaceMinPerKm(workout.targetPaceMinPerKm());
        recommendation.setTargetHrZone(workout.targetHrZone());
        recommendation.setTargetLoad(workout.targetLoad());
        recommendation.setMainSet(workout.mainSet());
        recommendation.setSummary(payload.summary());
        recommendation.setAnalysis(payload.analysis());
        recommendation.setCoachNotes(payload.coachNotes());
        recommendation.setModel(payload.model());
        recommendation.setPromptVersion(payload.promptVersion());
        recommendation.setGeneratedAt(LocalDateTime.now());
    }

    // ---------------------------------------------------------------------
    // Jobs
    // ---------------------------------------------------------------------

    @Transactional
    public DailyGenerationJob createJob(Long userId, LocalDate date, boolean replace,
                                        TrainingJobSource source) {
        if (date == null) {
            throw new IllegalArgumentException("La fecha de la recomendación es obligatoria");
        }
        if (!replace && recommendationRepository.existsByUserIdAndRecommendationDate(userId, date)) {
            throw new RecommendationAlreadyExistsException(date);
        }
        if (jobRepository.existsByUserIdAndRecommendationDateAndStatusIn(userId, date, ACTIVE_JOB_STATUSES)) {
            throw new GenerationJobStateException(
                    "Ya hay un job de generación en curso para el día " + date);
        }

        LocalDateTime now = LocalDateTime.now();
        DailyGenerationJob job = DailyGenerationJob.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .recommendationDate(date)
                .status(TrainingJobStatus.PENDING)
                .source(source)
                .attempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return jobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public DailyGenerationJobResponseDto getJob(Long userId, String requestId) {
        return jobRepository.findByIdAndUserId(requestId, userId)
                .map(this::toJobResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job de generación no encontrado: " + requestId));
    }

    @Transactional
    public DailyGenerationJobResponseDto failJob(Long userId, String requestId, String error) {
        DailyGenerationJob job = jobRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job de generación no encontrado: " + requestId));
        if (job.getStatus() == TrainingJobStatus.COMPLETED) {
            // Una recomendación ya persistida no debe marcarse como fallida por un error de Telegram.
            return toJobResponse(job);
        }
        if (job.getStatus() == TrainingJobStatus.FAILED || job.getStatus() == TrainingJobStatus.TIMEOUT) {
            return toJobResponse(job);
        }
        job.setStatus(TrainingJobStatus.FAILED);
        job.setLastError(truncate(error == null ? "Error desconocido" : error, 4000));
        job.setCompletedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        jobRepository.save(job);
        log.warn("Job de recomendación marcado como FAILED: requestId={}, userId={}, error={}",
                requestId, userId, job.getLastError());
        return toJobResponse(job);
    }

    @Transactional
    public void deleteJob(Long userId, String requestId, boolean force) {
        DailyGenerationJob job = jobRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job de generación no encontrado: " + requestId));
        requireDeletable(job, force);
        jobRepository.delete(job);
        log.info("Job de recomendación eliminado: requestId={}, userId={}, status={}",
                requestId, userId, job.getStatus());
    }

    @Transactional
    public int deleteJobs(Long userId, TrainingJobStatus status, boolean force) {
        if (status == null) {
            throw new IllegalArgumentException("El estado de los jobs es obligatorio");
        }
        if (status == TrainingJobStatus.RUNNING && !force) {
            throw new GenerationJobStateException(
                    "Hay jobs en ejecución (RUNNING). Espera a que terminen o usa force=true");
        }
        List<DailyGenerationJob> jobs = jobRepository.findByUserIdAndStatus(userId, status);
        if (jobs.isEmpty()) {
            return 0;
        }
        jobRepository.deleteAllInBatch(jobs);
        log.info("Eliminados {} jobs de recomendación con estado {} para userId={}",
                jobs.size(), status, userId);
        return jobs.size();
    }

    private static void requireDeletable(DailyGenerationJob job, boolean force) {
        if (job.getStatus() == TrainingJobStatus.RUNNING && !force) {
            throw new GenerationJobStateException(
                    "El job está en ejecución (RUNNING). Espera a que termine o usa force=true");
        }
    }

    @Transactional
    public int reconcileStaleJobs(Duration timeout) {
        LocalDateTime threshold = LocalDateTime.now().minus(timeout);
        List<DailyGenerationJob> stale = jobRepository.findByStatusInAndUpdatedAtBefore(
                ACTIVE_JOB_STATUSES, threshold);
        for (DailyGenerationJob job : stale) {
            job.setStatus(TrainingJobStatus.TIMEOUT);
            job.setLastError("Timeout: n8n no guardó la recomendación en el tiempo esperado");
            job.setCompletedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
        if (!stale.isEmpty()) {
            log.warn("Reconciliados {} jobs de recomendación caducados", stale.size());
        }
        return stale.size();
    }

    /**
     * Marca un job como RUNNING cuando n8n empieza a trabajar. Es opcional;
     * save_daily_recommendation también lo hace si llega directamente en PENDING.
     */
    @Transactional
    public void markRunning(Long userId, String requestId) {
        jobRepository.findByIdAndUserId(requestId, userId).ifPresent(job -> {
            if (job.getStatus() == TrainingJobStatus.PENDING) {
                job.setStatus(TrainingJobStatus.RUNNING);
                job.setStartedAt(LocalDateTime.now());
                job.setUpdatedAt(LocalDateTime.now());
                jobRepository.save(job);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<DailyGenerationJobResponseDto> listRecentJobs(Long userId) {
        return jobRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(20)
                .map(this::toJobResponse)
                .toList();
    }

    // ---------------------------------------------------------------------
    // Preferencias
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TrainingPreferencesDto getPreferences(Long userId) {
        return preferencesRepository.findByUserId(userId)
                .map(this::toPreferencesDto)
                .orElse(defaultPreferences());
    }

    @Transactional
    public TrainingPreferencesDto updatePreferences(Long userId, UpdateTrainingPreferencesRequestDto request) {
        TrainingPreferences preferences = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> TrainingPreferences.builder()
                        .userId(userId)
                        .enabled(false)
                        .timezone(TrainingTimeUtils.DEFAULT_TIMEZONE)
                        .generationHour(defaultGenerationHour)
                        .build());

        if (preferences.getGenerationHour() == null) {
            preferences.setGenerationHour(defaultGenerationHour);
        }
        if (request != null) {
            if (request.enabled() != null) {
                preferences.setEnabled(request.enabled());
            }
            if (request.timezone() != null) {
                if (!TrainingTimeUtils.isValidTimezone(request.timezone())) {
                    throw new IllegalArgumentException("Zona horaria no válida: " + request.timezone());
                }
                preferences.setTimezone(request.timezone().trim());
            }
            if (request.generationHour() != null) {
                if (request.generationHour() < 0 || request.generationHour() > 23) {
                    throw new IllegalArgumentException("generationHour debe estar entre 0 y 23");
                }
                preferences.setGenerationHour(request.generationHour());
            }
            if (request.notes() != null) {
                preferences.setNotes(request.notes());
            }
        }
        return toPreferencesDto(preferencesRepository.save(preferences));
    }

    public TrainingPreferencesDto defaultPreferences() {
        return new TrainingPreferencesDto(false, false,
                TrainingTimeUtils.DEFAULT_TIMEZONE, defaultGenerationHour, null, null);
    }

    @Transactional(readOnly = true)
    public ZoneId zoneFor(Long userId) {
        return preferencesRepository.findByUserId(userId)
                .map(TrainingPreferences::getTimezone)
                .map(TrainingTimeUtils::zoneOrDefault)
                .orElseGet(() -> ZoneId.of(TrainingTimeUtils.DEFAULT_TIMEZONE));
    }

    // ---------------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------------

    public DailyRecommendationPayload parseRecommendationJson(String recommendationJson) {
        if (recommendationJson == null || recommendationJson.isBlank()) {
            throw new RecommendationValidationException(List.of(new ValidationViolationDto(
                    "RECOMMENDATION_JSON_REQUIRED",
                    "El parámetro recommendationJson es obligatorio")));
        }
        try {
            DailyRecommendationPayload payload = objectMapper.readValue(
                    recommendationJson, DailyRecommendationPayload.class);
            if (payload == null) {
                throw new RecommendationValidationException(List.of(new ValidationViolationDto(
                        "RECOMMENDATION_JSON_INVALID",
                        "recommendationJson no contiene un objeto de recomendación")));
            }
            return payload;
        } catch (JsonProcessingException ex) {
            String detail = ex.getOriginalMessage() == null ? ex.getMessage() : ex.getOriginalMessage();
            throw new RecommendationValidationException(List.of(new ValidationViolationDto(
                    "RECOMMENDATION_JSON_INVALID", "JSON de recomendación inválido: " + detail)));
        }
    }

    public LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ex) {
            throw new RecommendationValidationException(List.of(new ValidationViolationDto(
                    "DATE_INVALID", "date debe tener formato YYYY-MM-DD: " + value)));
        }
    }

    // ---------------------------------------------------------------------
    // Mapeos
    // ---------------------------------------------------------------------

    private DailyRecommendationResponseDto toResponse(DailyRecommendation recommendation) {
        return new DailyRecommendationResponseDto(
                recommendation.getId(),
                recommendation.getUserId(),
                recommendation.getRecommendationDate(),
                recommendation.getRequestId(),
                recommendation.getSummary(),
                recommendation.getAnalysis(),
                recommendation.getCoachNotes(),
                new DailyRecommendationResponseDto.WorkoutDto(
                        recommendation.getType(),
                        recommendation.getTitle(),
                        recommendation.getDescription(),
                        recommendation.getTargetDistanceMeters(),
                        recommendation.getTargetDurationSeconds(),
                        recommendation.getTargetPaceMinPerKm(),
                        recommendation.getTargetHrZone(),
                        recommendation.getTargetLoad(),
                        recommendation.getMainSet()),
                recommendation.getModel(),
                recommendation.getPromptVersion(),
                recommendation.getGeneratedAt());
    }

    private DailyRecommendationSummaryDto toSummary(DailyRecommendation recommendation) {
        return new DailyRecommendationSummaryDto(
                recommendation.getId(),
                recommendation.getRecommendationDate(),
                recommendation.getType(),
                recommendation.getTitle(),
                recommendation.getTargetDistanceMeters(),
                recommendation.getTargetDurationSeconds(),
                recommendation.getTargetPaceMinPerKm(),
                recommendation.getTargetHrZone(),
                recommendation.getTargetLoad(),
                recommendation.getSummary(),
                recommendation.getGeneratedAt());
    }

    private TrainingPreferencesDto toPreferencesDto(TrainingPreferences preferences) {
        return new TrainingPreferencesDto(
                true,
                preferences.getEnabled(),
                preferences.getTimezone(),
                TrainingTimeUtils.normalizeHour(preferences.getGenerationHour()),
                preferences.getNotes(),
                preferences.getUpdatedAt());
    }

    private DailyGenerationJobResponseDto toJobResponse(DailyGenerationJob job) {
        return new DailyGenerationJobResponseDto(
                job.getId(),
                job.getUserId(),
                job.getRecommendationDate(),
                job.getStatus(),
                job.getSource() == null ? null : job.getSource().name(),
                job.getAttempts(),
                job.getRecommendationId(),
                job.getLastError(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getCompletedAt());
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
