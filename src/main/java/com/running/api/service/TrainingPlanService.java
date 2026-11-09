package com.running.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.running.api.dto.PlanViolationDto;
import com.running.api.dto.PlanGenerationJobResponseDto;
import com.running.api.dto.TrainingPlanResponseDto;
import com.running.api.dto.TrainingPlanSummaryDto;
import com.running.api.dto.TrainingPreferencesDto;
import com.running.api.dto.UpdateTrainingPreferencesRequestDto;
import com.running.api.dto.WeeklyPlanPayload;
import com.running.api.dto.WeeklyPlanPayload.PlannedWorkoutPayload;
import com.running.api.exception.PlanAlreadyExistsException;
import com.running.api.exception.PlanJobStateException;
import com.running.api.exception.PlanValidationException;
import com.running.api.exception.ResourceNotFoundException;
import com.running.api.model.Activity;
import com.running.api.model.PlanGenerationJob;
import com.running.api.model.PlanStatus;
import com.running.api.model.PlannedWorkout;
import com.running.api.model.TrainingJobSource;
import com.running.api.model.TrainingJobStatus;
import com.running.api.model.TrainingPlan;
import com.running.api.model.TrainingPreferences;
import com.running.api.model.WorkoutStatus;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.PlanGenerationJobRepository;
import com.running.api.repository.TrainingPlanRepository;
import com.running.api.repository.TrainingPreferencesRepository;
import com.running.api.util.TrainingWeekUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingPlanService {

    private static final List<TrainingJobStatus> ACTIVE_JOB_STATUSES =
            List.of(TrainingJobStatus.PENDING, TrainingJobStatus.RUNNING);

    private final TrainingPlanRepository planRepository;
    private final PlanGenerationJobRepository jobRepository;
    private final TrainingPreferencesRepository preferencesRepository;
    private final ActivityRepository activityRepository;
    private final TrainingPlanValidator validator;
    private final ObjectMapper objectMapper;

    // ---------------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<TrainingPlanResponseDto> getCurrentPlan(Long userId) {
        ZoneId zone = preferencesRepository.findByUserId(userId)
                .map(TrainingPreferences::getTimezone)
                .map(TrainingWeekUtils::zoneOrDefault)
                .orElse(ZoneId.of(TrainingWeekUtils.DEFAULT_TIMEZONE));
        LocalDate currentWeek = TrainingWeekUtils.currentWeekStart(zone);

        Optional<TrainingPlan> plan = planRepository
                .findFirstByUserIdAndWeekStartAndStatusOrderByVersionDesc(userId, currentWeek, PlanStatus.ACTIVE)
                .or(() -> planRepository.findFirstByUserIdAndStatusOrderByWeekStartDesc(userId, PlanStatus.ACTIVE));
        return plan.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<TrainingPlanSummaryDto> listPlans(Long userId, LocalDate from, LocalDate to) {
        List<TrainingPlan> plans;
        if (from != null && to != null) {
            plans = planRepository.findByUserIdAndWeekStartBetweenOrderByWeekStartDesc(userId, from, to);
        } else {
            plans = planRepository.findByUserIdOrderByWeekStartDesc(userId);
        }
        return plans.stream().distinct().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public Optional<TrainingPlanResponseDto> getPlan(Long userId, String planId) {
        return planRepository.findByIdAndUserId(planId, userId).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<TrainingPlanResponseDto> getPlanByWeek(Long userId, LocalDate weekStart) {
        return planRepository.findFirstByUserIdAndWeekStartOrderByVersionDesc(userId, weekStart)
                .map(this::toResponse);
    }

    // ---------------------------------------------------------------------
    // Borrado de planes
    // ---------------------------------------------------------------------

    /**
     * Elimina un plan (y sus sesiones) junto con los jobs de generación que lo
     * referencian. Si se quiere regenerar conservando el histórico, es preferible
     * {@code POST /plans/generate} con {@code replace=true}.
     */
    @Transactional
    public void deletePlan(Long userId, String planId) {
        TrainingPlan plan = planRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan no encontrado: " + planId));
        purgePlans(userId, List.of(plan));
    }

    /**
     * Elimina todas las versiones (activas y archivadas) de una semana.
     *
     * @return número de versiones eliminadas
     */
    @Transactional
    public int deletePlansByWeek(Long userId, LocalDate weekStart) {
        if (weekStart == null) {
            throw new IllegalArgumentException("weekStart es obligatorio");
        }
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStart debe ser un lunes: " + weekStart);
        }
        List<TrainingPlan> plans = planRepository
                .findByUserIdAndWeekStartOrderByVersionDesc(userId, weekStart);
        if (plans.isEmpty()) {
            return 0;
        }
        purgePlans(userId, plans);
        return plans.size();
    }

    private void purgePlans(Long userId, List<TrainingPlan> plans) {
        List<String> planIds = plans.stream().map(TrainingPlan::getId).toList();
        List<PlanGenerationJob> jobs = jobRepository.findByUserIdAndPlanIdIn(userId, planIds);
        if (!jobs.isEmpty()) {
            jobRepository.deleteAllInBatch(jobs);
        }
        // deleteAll (no deleteAllInBatch) para que el cascade elimine planned_workouts.
        planRepository.deleteAll(plans);
        log.info("Eliminados {} plan(es) del userId={} (planIds={}) y {} job(s) asociados",
                plans.size(), userId, planIds, jobs.size());
    }

    // ---------------------------------------------------------------------
    // Guardado transaccional del plan + job
    // ---------------------------------------------------------------------

    /**
     * Guarda el plan y marca el job como COMPLETED en la misma transacción.
     * Si cualquiera de las dos escrituras falla, no se persiste ninguna.
     */
    @Transactional
    public TrainingPlanResponseDto saveWeeklyPlan(Long userId,
                                                  String requestId,
                                                  LocalDate weekStart,
                                                  boolean replace,
                                                  WeeklyPlanPayload payload) {
        if (requestId == null || requestId.isBlank()) {
            throw new PlanJobStateException("Falta el requestId del job de generación");
        }

        PlanGenerationJob job = jobRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job de generación no encontrado: " + requestId));

        if (job.getStatus() == TrainingJobStatus.COMPLETED) {
            if (job.getPlanId() == null) {
                throw new PlanJobStateException("El job está completado pero no tiene plan asociado");
            }
            return planRepository.findByIdAndUserId(job.getPlanId(), userId)
                    .map(this::toResponse)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Plan del job completado no encontrado: " + job.getPlanId()));
        }
        if (job.getStatus() == TrainingJobStatus.FAILED
                || job.getStatus() == TrainingJobStatus.TIMEOUT) {
            throw new PlanJobStateException("El job ya terminó con estado " + job.getStatus());
        }

        LocalDate effectiveWeekStart = weekStart == null ? job.getWeekStart() : weekStart;
        if (!effectiveWeekStart.equals(job.getWeekStart())) {
            throw new PlanJobStateException("weekStart (" + effectiveWeekStart
                    + ") no coincide con la semana del job (" + job.getWeekStart() + ")");
        }

        if (job.getStatus() == TrainingJobStatus.PENDING) {
            job.setStatus(TrainingJobStatus.RUNNING);
            job.setStartedAt(LocalDateTime.now());
        }
        job.setAttempts(job.getAttempts() == null ? 1 : job.getAttempts() + 1);
        job.setUpdatedAt(LocalDateTime.now());

        Double previousAverage = previousFourWeekAverageDistanceMeters(userId, effectiveWeekStart);
        validator.validate(effectiveWeekStart, payload, previousAverage);

        Optional<TrainingPlan> activePlan = planRepository
                .findFirstByUserIdAndWeekStartAndStatusOrderByVersionDesc(
                        userId, effectiveWeekStart, PlanStatus.ACTIVE);
        if (activePlan.isPresent() && !replace) {
            throw new PlanAlreadyExistsException(effectiveWeekStart);
        }
        activePlan.ifPresent(previous -> {
            previous.setStatus(PlanStatus.ARCHIVED);
            previous.setUpdatedAt(LocalDateTime.now());
            planRepository.save(previous);
        });

        int version = planRepository.findFirstByUserIdAndWeekStartOrderByVersionDesc(userId, effectiveWeekStart)
                .map(previous -> previous.getVersion() + 1)
                .orElse(1);

        TrainingPlan plan = TrainingPlan.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .weekStart(effectiveWeekStart)
                .weekEnd(effectiveWeekStart.plusDays(6))
                .status(PlanStatus.ACTIVE)
                .version(version)
                .requestId(requestId)
                .summary(payload.summary())
                .coachNotes(payload.coachNotes())
                .targetWeeklyDistanceMeters(payload.targetWeeklyDistanceMeters())
                .targetWeeklyLoad(payload.targetWeeklyLoad())
                .model(payload.model())
                .promptVersion(payload.promptVersion())
                .generatedAt(LocalDateTime.now())
                .build();

        for (PlannedWorkoutPayload workout : payload.workouts()) {
            plan.addWorkout(toEntity(workout));
        }
        planRepository.save(plan);

        job.setStatus(TrainingJobStatus.COMPLETED);
        job.setPlanId(plan.getId());
        job.setLastError(null);
        job.setCompletedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        jobRepository.save(job);

        log.info("Plan semanal guardado: userId={}, weekStart={}, planId={}, requestId={}, version={}",
                userId, effectiveWeekStart, plan.getId(), requestId, version);
        return toResponse(plan);
    }

    // ---------------------------------------------------------------------
    // Jobs
    // ---------------------------------------------------------------------

    @Transactional
    public PlanGenerationJob createJob(Long userId, LocalDate weekStart, boolean replace,
                                       TrainingJobSource source) {
        if (weekStart == null) {
            throw new IllegalArgumentException("weekStart es obligatorio");
        }
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStart debe ser un lunes: " + weekStart);
        }
        if (!replace && planRepository.existsByUserIdAndWeekStart(userId, weekStart)) {
            throw new PlanAlreadyExistsException(weekStart);
        }
        if (jobRepository.existsByUserIdAndWeekStartAndStatusIn(userId, weekStart, ACTIVE_JOB_STATUSES)) {
            throw new PlanJobStateException("Ya hay un job de generación en curso para la semana " + weekStart);
        }

        LocalDateTime now = LocalDateTime.now();
        PlanGenerationJob job = PlanGenerationJob.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .weekStart(weekStart)
                .status(TrainingJobStatus.PENDING)
                .source(source)
                .attempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return jobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public PlanGenerationJobResponseDto getJob(Long userId, String requestId) {
        return jobRepository.findByIdAndUserId(requestId, userId)
                .map(this::toJobResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Job de generación no encontrado: " + requestId));
    }

    @Transactional
    public PlanGenerationJobResponseDto failJob(Long userId, String requestId, String error) {
        PlanGenerationJob job = jobRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Job de generación no encontrado: " + requestId));
        if (job.getStatus() == TrainingJobStatus.COMPLETED) {
            // Un plan ya persistido no debe marcarse como fallido por un error de Telegram.
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
        log.warn("Job de plan marcado como FAILED: requestId={}, userId={}, error={}",
                requestId, userId, job.getLastError());
        return toJobResponse(job);
    }

    /**
     * Elimina el registro de un job. Útil para limpiar jobs que quedaron
     * atascados en {@code PENDING} y así poder volver a generar esa semana.
     * <p>
     * No elimina el plan ya guardado. Si n8n sigue ejecutándose en paralelo,
     * su llamada posterior a {@code save_weekly_plan} fallará con "job no
     * encontrado". Por eso los jobs {@code RUNNING} requieren {@code force}.
     */
    @Transactional
    public void deleteJob(Long userId, String requestId, boolean force) {
        PlanGenerationJob job = jobRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job de generación no encontrado: " + requestId));
        requireDeletable(job, force);
        jobRepository.delete(job);
        log.info("Job de plan eliminado: requestId={}, userId={}, status={}",
                requestId, userId, job.getStatus());
    }

    /**
     * Elimina todos los jobs del usuario con un estado concreto.
     *
     * @return número de jobs eliminados
     */
    @Transactional
    public int deleteJobs(Long userId, TrainingJobStatus status, boolean force) {
        if (status == null) {
            throw new IllegalArgumentException("El estado de los jobs es obligatorio");
        }
        if (status == TrainingJobStatus.RUNNING && !force) {
            throw new PlanJobStateException(
                    "Hay jobs en ejecución (RUNNING). Espera a que terminen o usa force=true");
        }
        List<PlanGenerationJob> jobs = jobRepository.findByUserIdAndStatus(userId, status);
        if (jobs.isEmpty()) {
            return 0;
        }
        jobRepository.deleteAllInBatch(jobs);
        log.info("Eliminados {} jobs de plan con estado {} para userId={}",
                jobs.size(), status, userId);
        return jobs.size();
    }

    private static void requireDeletable(PlanGenerationJob job, boolean force) {
        if (job.getStatus() == TrainingJobStatus.RUNNING && !force) {
            throw new PlanJobStateException(
                    "El job está en ejecución (RUNNING). Espera a que termine o usa force=true");
        }
    }

    @Transactional
    public int reconcileStaleJobs(Duration timeout) {        LocalDateTime threshold = LocalDateTime.now().minus(timeout);
        List<PlanGenerationJob> stale = jobRepository.findByStatusInAndUpdatedAtBefore(
                ACTIVE_JOB_STATUSES, threshold);
        for (PlanGenerationJob job : stale) {
            job.setStatus(TrainingJobStatus.TIMEOUT);
            job.setLastError("Timeout: n8n no guardó el plan en el tiempo esperado");
            job.setCompletedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
        if (!stale.isEmpty()) {
            log.warn("Reconciliados {} jobs de plan caducados", stale.size());
        }
        return stale.size();
    }

    /**
     * Marca un job como RUNNING cuando n8n empieza a trabajar. Es opcional;
     * save_weekly_plan también lo hace si llega directamente en PENDING.
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

    // ---------------------------------------------------------------------
    // Preferencias
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TrainingPreferencesDto getPreferences(Long userId) {
        return preferencesRepository.findByUserId(userId)
                .map(this::toPreferencesDto)
                .orElse(new TrainingPreferencesDto(false, false,
                        TrainingWeekUtils.DEFAULT_TIMEZONE, null, null, null, null));
    }

    @Transactional
    public TrainingPreferencesDto updatePreferences(Long userId, UpdateTrainingPreferencesRequestDto request) {
        TrainingPreferences preferences = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> TrainingPreferences.builder()
                        .userId(userId)
                        .enabled(false)
                        .timezone(TrainingWeekUtils.DEFAULT_TIMEZONE)
                        .build());

        if (request != null) {
            if (request.enabled() != null) {
                preferences.setEnabled(request.enabled());
            }
            if (request.timezone() != null) {
                if (!TrainingPlanValidator.isValidTimezone(request.timezone())) {
                    throw new IllegalArgumentException("Zona horaria no válida: " + request.timezone());
                }
                preferences.setTimezone(request.timezone().trim());
            }
            if (request.daysPerWeek() != null) {
                if (request.daysPerWeek() < 1 || request.daysPerWeek() > 7) {
                    throw new IllegalArgumentException("daysPerWeek debe estar entre 1 y 7");
                }
                preferences.setDaysPerWeek(request.daysPerWeek());
            }
            if (request.preferredLongRunDay() != null) {
                String normalized = TrainingPlanValidator.normalizePreferredLongRunDay(
                        request.preferredLongRunDay());
                if (normalized == null) {
                    throw new IllegalArgumentException("Día no válido: " + request.preferredLongRunDay());
                }
                preferences.setPreferredLongRunDay(normalized);
            }
            if (request.notes() != null) {
                preferences.setNotes(request.notes());
            }
        }
        return toPreferencesDto(preferencesRepository.save(preferences));
    }

    // ---------------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------------

    public WeeklyPlanPayload parsePlanJson(String planJson) {
        if (planJson == null || planJson.isBlank()) {
            throw new PlanValidationException(List.of(new PlanViolationDto(
                    "PLAN_JSON_REQUIRED", "El parámetro planJson es obligatorio")));
        }
        try {
            WeeklyPlanPayload payload = objectMapper.readValue(planJson, WeeklyPlanPayload.class);
            if (payload == null) {
                throw new PlanValidationException(List.of(new PlanViolationDto(
                        "PLAN_JSON_INVALID", "planJson no contiene un objeto de plan")));
            }
            return payload;
        } catch (JsonProcessingException ex) {
            String detail = ex.getOriginalMessage() == null ? ex.getMessage() : ex.getOriginalMessage();
            throw new PlanValidationException(List.of(new PlanViolationDto(
                    "PLAN_JSON_INVALID", "JSON de plan inválido: " + detail)));
        }
    }

    public LocalDate parseWeekStart(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ex) {
            throw new PlanValidationException(List.of(new PlanViolationDto(
                    "WEEK_START_INVALID", "weekStart debe tener formato YYYY-MM-DD: " + value)));
        }
    }

    // ---------------------------------------------------------------------
    // Mapeos
    // ---------------------------------------------------------------------

    private PlannedWorkout toEntity(PlannedWorkoutPayload source) {
        return PlannedWorkout.builder()
                .workoutDate(source.date())
                .type(source.type())
                .title(source.title().trim())
                .description(source.description())
                .targetDistanceMeters(source.targetDistanceMeters())
                .targetDurationSeconds(source.targetDurationSeconds())
                .targetPaceMinPerKm(source.targetPaceMinPerKm())
                .targetHrZone(source.targetHrZone())
                .targetLoad(source.targetLoad())
                .mainSet(source.mainSet())
                .status(WorkoutStatus.PLANNED)
                .build();
    }

    private TrainingPlanResponseDto toResponse(TrainingPlan plan) {
        List<TrainingPlanResponseDto.WorkoutDto> workouts = plan.getWorkouts().stream()
                .sorted(Comparator.comparing(PlannedWorkout::getWorkoutDate)
                        .thenComparing(w -> w.getId() == null ? Long.MAX_VALUE : w.getId()))
                .map(w -> new TrainingPlanResponseDto.WorkoutDto(
                        w.getId(),
                        w.getWorkoutDate(),
                        w.getType(),
                        w.getTitle(),
                        w.getDescription(),
                        w.getTargetDistanceMeters(),
                        w.getTargetDurationSeconds(),
                        w.getTargetPaceMinPerKm(),
                        w.getTargetHrZone(),
                        w.getTargetLoad(),
                        w.getMainSet(),
                        w.getStatus() == null ? null : w.getStatus().name(),
                        w.getCompletedActivityId(),
                        w.getRpe(),
                        w.getFeedback()))
                .toList();

        return new TrainingPlanResponseDto(
                plan.getId(),
                plan.getUserId(),
                plan.getWeekStart(),
                plan.getWeekEnd(),
                plan.getStatus(),
                plan.getVersion(),
                plan.getRequestId(),
                plan.getSummary(),
                plan.getCoachNotes(),
                plan.getTargetWeeklyDistanceMeters(),
                plan.getTargetWeeklyLoad(),
                plan.getModel(),
                plan.getPromptVersion(),
                plan.getGeneratedAt(),
                workouts);
    }

    private TrainingPlanSummaryDto toSummary(TrainingPlan plan) {
        return new TrainingPlanSummaryDto(
                plan.getId(),
                plan.getWeekStart(),
                plan.getWeekEnd(),
                plan.getStatus(),
                plan.getVersion(),
                plan.getSummary(),
                plan.getTargetWeeklyDistanceMeters(),
                plan.getTargetWeeklyLoad(),
                plan.getWorkouts() == null ? 0 : plan.getWorkouts().size(),
                plan.getGeneratedAt());
    }

    private TrainingPreferencesDto toPreferencesDto(TrainingPreferences preferences) {
        return new TrainingPreferencesDto(
                true,
                preferences.getEnabled(),
                preferences.getTimezone(),
                preferences.getDaysPerWeek(),
                preferences.getPreferredLongRunDay(),
                preferences.getNotes(),
                preferences.getUpdatedAt());
    }

    private PlanGenerationJobResponseDto toJobResponse(PlanGenerationJob job) {
        return new PlanGenerationJobResponseDto(
                job.getId(),
                job.getUserId(),
                job.getWeekStart(),
                job.getStatus(),
                job.getSource() == null ? null : job.getSource().name(),
                job.getAttempts(),
                job.getPlanId(),
                job.getLastError(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getCompletedAt());
    }

    /**
     * Media de distancia de las últimas 4 semanas con actividad antes de la
     * semana planificada. Se usa para limitar aumentos bruscos de volumen.
     */
    private Double previousFourWeekAverageDistanceMeters(Long userId, LocalDate weekStart) {
        LocalDate from = weekStart.minusWeeks(4);
        LocalDate to = weekStart.minusDays(1);
        List<Activity> activities = activityRepository
                .findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(
                        userId, from.atStartOfDay(), to.atTime(23, 59, 59));
        if (activities.isEmpty()) {
            return null;
        }
        Map<LocalDate, Double> perWeek = new LinkedHashMap<>();
        for (Activity activity : activities) {
            if (activity.getStartTime() == null) {
                continue;
            }
            LocalDate week = TrainingWeekUtils.normalizeToMonday(activity.getStartTime().toLocalDate());
            double distance = activity.getDistanceMeters() == null ? 0.0 : activity.getDistanceMeters();
            perWeek.merge(week, distance, Double::sum);
        }
        if (perWeek.isEmpty()) {
            return null;
        }
        return perWeek.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    public List<PlanGenerationJobResponseDto> listRecentJobs(Long userId) {
        return jobRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(20)
                .map(this::toJobResponse)
                .toList();
    }

    public TrainingPreferencesDto defaultPreferences() {
        return new TrainingPreferencesDto(false, false,
                TrainingWeekUtils.DEFAULT_TIMEZONE, null, null, null, null);
    }
}
