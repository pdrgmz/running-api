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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.running.api.dto.ActivityDeletionStatusDto;
import com.running.api.dto.ErrorResponseDto;
import com.running.api.dto.TrackpointCompletionResultDto;
import com.running.api.dto.TrackpointMetricsCompletionResultDto;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.security.UserPrincipal;
import com.running.api.service.ActivityDeletionStatusTracker;
import com.running.api.service.ActivityService;

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

        @PostMapping("/{id}/complete-trackpoints")
        @Operation(summary = "Completar los datos que falten en los trackpoints de una carrera", description = "Rellena latitud, longitud, altitud, frecuencia cardíaca, cadencia y velocidad "
                        + "interpolando con los puntos vecinos, descartando antes los valores de FC, cadencia "
                        + "y velocidad fuera de rango fisiológico. También ajusta la distancia acumulada "
                        + "descartando retrocesos y saltos que implicarían más de 15 m/s. Si toda la altitud "
                        + "es 0 o nula, consulta un modelo digital de elevaciones usando las coordenadas GPS.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Trackpoints completados"),
                        @ApiResponse(responseCode = "404", description = "La actividad no existe o no pertenece al usuario", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
        })
        public ResponseEntity<TrackpointCompletionResultDto> completeTrackpoints(
                        @AuthenticationPrincipal UserPrincipal principal,
                        @Parameter(description = "ID de la actividad a completar", example = "2026-09-05T07:00:00Z") @PathVariable String id) {
                return activityService.completeActivityTrackpoints(principal.id(), id)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
        }

        @PostMapping("/{id}/complete-metrics")
        @Operation(summary = "Completar frecuencia cardíaca, cadencia y velocidad", description = "Solo rellena los valores nulos y los claramente atípicos: los que se alejan "
                        + "mucho de la mediana de sus vecinos o son imposibles (FC o cadencia <= 0, "
                        + "velocidad < 0 o > 15 m/s). El resto de valores se conserva sin cambios. "
                        + "La interpolación es lineal entre los puntos válidos vecinos. También ajusta "
                        + "la distancia acumulada descartando retrocesos y saltos que implicarían más "
                        + "de 15 m/s. No modifica coordenadas ni altitud y no consulta proveedores externos.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Métricas completadas"),
                        @ApiResponse(responseCode = "404", description = "La actividad no existe o no pertenece al usuario", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
        })
        public ResponseEntity<TrackpointMetricsCompletionResultDto> completeMetrics(
                        @AuthenticationPrincipal UserPrincipal principal,
                        @Parameter(description = "ID de la actividad a completar", example = "2026-09-05T07:00:00Z") @PathVariable String id) {
                return activityService.completeActivityMetrics(principal.id(), id)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
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
}
