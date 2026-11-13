package com.running.api.controller;

import java.util.List;
import java.util.Map;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.running.api.dto.ActivityAnalysisJobResponseDto;
import com.running.api.dto.ActivityDeletionStatusDto;
import com.running.api.dto.AnalyzeActivityRequestDto;
import com.running.api.dto.AnalyzeActivityResponseDto;
import com.running.api.dto.ErrorResponseDto;
import com.running.api.dto.MetricsRecalculationResponseDto;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.security.UserPrincipal;
import com.running.api.service.ActivityAnalysisGenerationService;
import com.running.api.service.ActivityDeletionStatusTracker;
import com.running.api.service.ActivityService;
import com.running.api.service.MetricsRecalculationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
@Tag(name = "Actividades", description = "Ingesta, consulta, eliminación y exportación de las carreras del usuario autenticado")
public class ActivityController {

        private final ActivityService activityService;
        private final ActivityDeletionStatusTracker deletionStatusTracker;
        private final MetricsRecalculationService metricsRecalculationService;
        private final ActivityAnalysisGenerationService analysisGenerationService;

        @GetMapping
        @Operation(summary = "Listar las carreras del usuario autenticado", description = "Devuelve una página de actividades ordenada por fecha de inicio descendente. Soporta paginación y ordenación mediante los parámetros page, size y sort.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Página de actividades del usuario"),
                        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
        })
        public ResponseEntity<Page<Activity>> getAllActivities(
                        @AuthenticationPrincipal UserPrincipal principal,
                        @ParameterObject @PageableDefault(page = 0, size = 20, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable) {
                return ResponseEntity.ok(activityService.getAllActivities(principal.id(), pageable));
        }

        @GetMapping("/{id}")
        @Operation(summary = "Obtener el resumen de una carrera por ID")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Actividad encontrada"),
                        @ApiResponse(responseCode = "404", description = "La actividad no existe o no pertenece al usuario", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
        })
        public ResponseEntity<Activity> getActivityById(
                        @AuthenticationPrincipal UserPrincipal principal,
                        @Parameter(description = "ID de la actividad (ISO Timestamp o UUID)", example = "2026-09-05T07:00:00Z") @PathVariable String id) {
                return activityService.getActivityById(principal.id(), id)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
        }

        @GetMapping("/{id}/trackpoints")
        @Operation(summary = "Obtener los trackpoints de una carrera", description = "Devuelve la telemetría segundo a segundo. Admite un muestreo opcional para reducir el tamaño de la respuesta.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Lista de trackpoints"),
                        @ApiResponse(responseCode = "404", description = "La actividad no existe o no tiene trackpoints", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
        })
        public ResponseEntity<List<ActivityTrackpoint>> getActivityTrackpointsById(
                        @AuthenticationPrincipal UserPrincipal principal,
                        @Parameter(description = "ID de la actividad", example = "2026-09-05T07:00:00Z") @PathVariable String id,
                        @Parameter(description = "Muestreo de puntos: low (baja densidad), medium (media) o high (todos). Sin valor = todos", schema = @Schema(allowableValues = {
                                        "low", "medium", "high" })) @RequestParam(required = false) String resolution) {
                List<ActivityTrackpoint> trackpoints = activityService.getActivityTrackpointsById(principal.id(), id,
                                resolution);
                if (trackpoints.isEmpty()) {
                        return ResponseEntity.notFound().build();
                }
                return ResponseEntity.ok(trackpoints);
        }

        @DeleteMapping
        @Operation(summary = "Vaciar las actividades del usuario autenticado (asíncrono)", description = "Elimina en segundo plano todas las carreras, trackpoints, ficheros de respaldo y récords personales "
                        + "del usuario, y reinicia sus estadísticas. Responde 202 Accepted con un identificador de "
                        + "vaciado cuyo progreso se consulta en GET /api/v1/activities/delete-status/{deleteId}.")
        @ApiResponses({
                        @ApiResponse(responseCode = "202", description = "Vaciado aceptado y en proceso"),
                        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
        })
        public ResponseEntity<ActivityDeletionStatusDto> deleteAllActivities(
                        @AuthenticationPrincipal UserPrincipal principal) {
                ActivityDeletionStatusDto status = deletionStatusTracker.create(principal.id());
                activityService.deleteAllActivities(principal.id(), status.getDeleteId());
                return ResponseEntity.accepted().body(status);
        }

        @GetMapping("/delete-status/{deleteId}")
        @Operation(summary = "Consultar el estado de un vaciado de actividades", description = "Estados: PENDING (en cola), PROCESSING (eliminando), COMPLETED (terminado) y FAILED (error). "
                        + "Solo se conserva el último vaciado de cada usuario.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Estado del vaciado"),
                        @ApiResponse(responseCode = "404", description = "El vaciado no existe o fue reemplazado por uno más reciente", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
        })
        public ResponseEntity<ActivityDeletionStatusDto> getDeletionStatus(
                        @Parameter(description = "ID del vaciado devuelto por DELETE /api/v1/activities", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890") @PathVariable String deleteId) {
                ActivityDeletionStatusDto status = deletionStatusTracker.getStatus(deleteId);
                if (status == null) {
                        return ResponseEntity.notFound().build();
                }
                return ResponseEntity.ok(status);
        }

        @PostMapping("/recalculate-metrics")
        @Operation(summary = "Recalcular las métricas derivadas de todas las carreras", description = "Recalcula en bloque el training load (TRIMP/HRSS), el VO2Max y la VAM de todas las actividades "
                        + "del usuario, usando como referencia la FC máxima del atleta. Útil tras corregir la lógica de cálculo "
                        + "o tras importar un historial antiguo. El proceso es síncrono y puede tardar más con historiales grandes.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Métricas recalculadas"),
                        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
        })
        public ResponseEntity<MetricsRecalculationResponseDto> recalculateMetrics(
                        @AuthenticationPrincipal UserPrincipal principal) {
                return ResponseEntity.ok(metricsRecalculationService.recalculateAll(principal.id()));
        }

        @DeleteMapping("/{id}")
        @Operation(summary = "Eliminar una carrera por ID")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Carrera eliminada"),
                        @ApiResponse(responseCode = "404", description = "La actividad no existe o no pertenece al usuario", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
        })
        public ResponseEntity<Map<String, Object>> deleteActivityById(
                        @AuthenticationPrincipal UserPrincipal principal,
                        @Parameter(description = "ID de la actividad a eliminar", example = "2026-09-05T07:00:00Z") @PathVariable String id) {
                boolean deleted = activityService.deleteActivityById(principal.id(), id);
                if (!deleted) {
                        return ResponseEntity.notFound().build();
                }
                return ResponseEntity.ok(Map.of(
                                "message", "Carrera eliminada con éxito",
                                "activityId", id));
        }

        @PostMapping("/{id}/analyze")
        @Operation(summary = "Analizar una carrera con IA",
                        description = "Crea un job, firma un JWT efímero con aud=mcp y lo envía al webhook de n8n "
                                        + "para que analice la actividad. Responde 202; el estado se consulta en "
                                        + "GET /api/v1/activities/{id}/analysis/jobs/{requestId}. El análisis solo se "
                                        + "guarda en la actividad cuando n8n llama a save_activity_analysis.")
        @ApiResponses({
                        @ApiResponse(responseCode = "202", description = "Análisis encolado"),
                        @ApiResponse(responseCode = "404", description = "La actividad no existe o no pertenece al usuario", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
                        @ApiResponse(responseCode = "409", description = "Ya hay un análisis o un job en curso para esa actividad", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
                        @ApiResponse(responseCode = "503", description = "El webhook de n8n no está configurado", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
        })
        public ResponseEntity<AnalyzeActivityResponseDto> analyzeActivity(
                        @AuthenticationPrincipal UserPrincipal principal,
                        @Parameter(description = "ID de la actividad a analizar", example = "2026-09-05T07:00:00Z") @PathVariable String id,
                        @RequestBody(required = false) AnalyzeActivityRequestDto request) {
                boolean replace = request != null && Boolean.TRUE.equals(request.replace());
                AnalyzeActivityResponseDto response = analysisGenerationService.analyzeNow(
                                principal.id(), id, replace);
                return ResponseEntity.accepted().body(response);
        }

        @GetMapping("/{id}/analysis")
        @Operation(summary = "Obtener el análisis guardado de una carrera")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Actividad con su análisis"),
                        @ApiResponse(responseCode = "404", description = "La actividad no existe o no pertenece al usuario", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
        })
        public ResponseEntity<Activity> getActivityAnalysis(
                        @AuthenticationPrincipal UserPrincipal principal,
                        @Parameter(description = "ID de la actividad", example = "2026-09-05T07:00:00Z") @PathVariable String id) {
                return activityService.getActivityById(principal.id(), id)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
        }

        @GetMapping("/{id}/analysis/jobs/{requestId}")
        @Operation(summary = "Consultar el estado de un job de análisis de una carrera")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Estado del job"),
                        @ApiResponse(responseCode = "404", description = "El job no existe o no pertenece al usuario", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
        })
        public ResponseEntity<ActivityAnalysisJobResponseDto> getAnalysisJob(
                        @AuthenticationPrincipal UserPrincipal principal,
                        @Parameter(description = "ID de la actividad", example = "2026-09-05T07:00:00Z") @PathVariable String id,
                        @Parameter(description = "requestId del job", example = "8f3c2a1e-...") @PathVariable String requestId) {
                ActivityAnalysisJobResponseDto job = analysisGenerationService.getJob(
                                principal.id(), requestId);
                if (!id.equals(job.activityId())) {
                        return ResponseEntity.notFound().build();
                }
                return ResponseEntity.ok(job);
        }
}
