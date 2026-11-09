package com.running.api.controller;

import com.running.api.dto.ErrorResponseDto;
import com.running.api.dto.DeletedJobsResponseDto;
import com.running.api.dto.DeletedPlansResponseDto;
import com.running.api.dto.GenerateTrainingPlanRequestDto;
import com.running.api.dto.GenerateTrainingPlanResponseDto;
import com.running.api.dto.PlanGenerationJobResponseDto;
import com.running.api.dto.TrainingPlanResponseDto;
import com.running.api.dto.TrainingPlanSummaryDto;
import com.running.api.dto.TrainingPreferencesDto;
import com.running.api.dto.UpdateTrainingPreferencesRequestDto;
import com.running.api.model.TrainingJobStatus;
import com.running.api.security.UserPrincipal;
import com.running.api.service.TrainingPlanGenerationService;
import com.running.api.service.TrainingPlanService;
import com.running.api.util.TrainingWeekUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/training")
@RequiredArgsConstructor
@Tag(name = "Entrenamientos", description = "Planes semanales generados por IA, estado de generación y preferencias del atleta")
public class TrainingPlanController {

    private final TrainingPlanService trainingPlanService;
    private final TrainingPlanGenerationService generationService;

    @GetMapping("/plans/current")
    @Operation(summary = "Obtener el plan actual",
            description = "Devuelve el plan activo de la semana actual del atleta o, si aún no existe, el último plan activo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan encontrado"),
            @ApiResponse(responseCode = "204", description = "El usuario todavía no tiene ningún plan"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<TrainingPlanResponseDto> getCurrentPlan(
            @AuthenticationPrincipal UserPrincipal principal) {
        return trainingPlanService.getCurrentPlan(principal.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/plans")
    @Operation(summary = "Listar planes semanales",
            description = "Lista los planes del usuario, opcionalmente filtrados por un rango de semanas (from/to inclusivos).")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Listado de planes"))
    public ResponseEntity<List<TrainingPlanSummaryDto>> listPlans(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Lunes inicial del rango", example = "2026-10-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Lunes final del rango", example = "2026-12-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(trainingPlanService.listPlans(principal.id(), from, to));
    }

    @GetMapping("/plans/{id}")
    @Operation(summary = "Obtener un plan por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan encontrado"),
            @ApiResponse(responseCode = "404", description = "El plan no existe o no pertenece al usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<TrainingPlanResponseDto> getPlan(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id) {
        return trainingPlanService.getPlan(principal.id(), id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/plans/week/{weekStart}")
    @Operation(summary = "Obtener el plan de una semana",
            description = "weekStart debe ser el lunes de la semana. Devuelve la versión más reciente, sea activa o archivada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan encontrado"),
            @ApiResponse(responseCode = "400", description = "La fecha no es un lunes",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "No hay plan para esa semana",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<TrainingPlanResponseDto> getPlanByWeek(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Lunes de la semana", example = "2026-11-16")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStart debe ser un lunes: " + weekStart);
        }
        return trainingPlanService.getPlanByWeek(principal.id(), weekStart)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/plans/{id}")
    @Operation(summary = "Eliminar un plan",
            description = "Elimina el plan, sus sesiones y los jobs que lo referencian. "
                    + "Para regenerar conservando el histórico, usa POST /plans/generate con replace=true.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Plan eliminado"),
            @ApiResponse(responseCode = "404", description = "El plan no existe o no pertenece al usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> deletePlan(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID del plan", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            @PathVariable String id) {
        trainingPlanService.deletePlan(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/plans/week/{weekStart}")
    @Operation(summary = "Eliminar todas las versiones de una semana",
            description = "Elimina las versiones activas y archivadas de la semana, sus sesiones y sus jobs. "
                    + "Útil para volver a generar la semana desde cero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Planes eliminados"),
            @ApiResponse(responseCode = "400", description = "La fecha no es un lunes",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<DeletedPlansResponseDto> deletePlansByWeek(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Lunes de la semana", example = "2026-11-16")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        int deleted = trainingPlanService.deletePlansByWeek(principal.id(), weekStart);
        return ResponseEntity.ok(new DeletedPlansResponseDto(deleted));
    }

    @PostMapping("/plans/generate")    @Operation(summary = "Generar un plan semanal con IA",
            description = "Crea un job, firma un JWT efímero con aud=mcp y lo envía al webhook genérico de n8n. "
                    + "Responde 202; el estado se consulta en /generation-jobs/{requestId}. "
                    + "El plan solo existe cuando n8n llama a save_weekly_plan y la API lo persiste.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Generación encolada"),
            @ApiResponse(responseCode = "409", description = "Ya existe un plan o un job en curso para esa semana",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "503", description = "El webhook de n8n no está configurado",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<GenerateTrainingPlanResponseDto> generatePlan(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) GenerateTrainingPlanRequestDto request) {
        boolean replace = request != null && Boolean.TRUE.equals(request.replace());
        LocalDate weekStart = request == null ? null : request.weekStart();
        if (weekStart == null) {
            String timezone = trainingPlanService.getPreferences(principal.id()).timezone();
            weekStart = TrainingWeekUtils.nextWeekStart(TrainingWeekUtils.zoneOrDefault(timezone));
        }
        GenerateTrainingPlanResponseDto response = generationService.generateNow(
                principal.id(), weekStart, replace);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/generation-jobs/{requestId}")
    @Operation(summary = "Consultar el estado de un job de generación")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado del job"),
            @ApiResponse(responseCode = "404", description = "El job no existe o no pertenece al usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<PlanGenerationJobResponseDto> getJob(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String requestId) {
        return ResponseEntity.ok(generationService.getJob(principal.id(), requestId));
    }

    @GetMapping("/generation-jobs")
    @Operation(summary = "Listar los últimos jobs de generación del usuario")
    public ResponseEntity<List<PlanGenerationJobResponseDto>> listJobs(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(trainingPlanService.listRecentJobs(principal.id()));
    }

    @DeleteMapping("/generation-jobs/{requestId}")
    @Operation(summary = "Eliminar un job de generación",
            description = "Borra el registro del job. No elimina el plan ya guardado. "
                    + "Los jobs en estado RUNNING requieren force=true porque n8n podría seguir trabajando.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Job eliminado"),
            @ApiResponse(responseCode = "404", description = "El job no existe o no pertenece al usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "El job está RUNNING y no se envió force=true",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> deleteJob(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "requestId del job", example = "8f3c2a1e-...")
            @PathVariable String requestId,
            @Parameter(description = "Permite eliminar también jobs en RUNNING", example = "false")
            @RequestParam(defaultValue = "false") boolean force) {
        trainingPlanService.deleteJob(principal.id(), requestId, force);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/generation-jobs")
    @Operation(summary = "Eliminar jobs de generación por estado",
            description = "Borra todos los jobs del usuario con el estado indicado. "
                    + "Útil para limpiar jobs atascados en PENDING antes de volver a generar la semana. "
                    + "Los jobs RUNNING requieren force=true.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jobs eliminados"),
            @ApiResponse(responseCode = "400", description = "Estado no válido",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "Hay jobs RUNNING y no se envió force=true",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<DeletedJobsResponseDto> deleteJobs(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Estado a eliminar", example = "PENDING")
            @RequestParam TrainingJobStatus status,
            @Parameter(description = "Permite eliminar también jobs en RUNNING", example = "false")
            @RequestParam(defaultValue = "false") boolean force) {
        int deleted = trainingPlanService.deleteJobs(principal.id(), status, force);
        return ResponseEntity.ok(new DeletedJobsResponseDto(deleted));
    }

    @GetMapping("/preferences")
    @Operation(summary = "Obtener las preferencias de planificación",
            description = "Si el usuario no las ha configurado, devuelve configured=false y enabled=false.")
    public ResponseEntity<TrainingPreferencesDto> getPreferences(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(trainingPlanService.getPreferences(principal.id()));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Actualizar las preferencias de planificación",
            description = "Activa o desactiva la generación semanal, define la zona horaria y las preferencias "
                    + "que se envían al LLM en get_training_context.")
    public ResponseEntity<TrainingPreferencesDto> updatePreferences(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody UpdateTrainingPreferencesRequestDto request) {
        return ResponseEntity.ok(trainingPlanService.updatePreferences(principal.id(), request));
    }
}
