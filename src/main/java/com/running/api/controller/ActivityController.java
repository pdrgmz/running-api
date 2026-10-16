package com.running.api.controller;

import com.running.api.dto.ErrorResponseDto;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.security.UserPrincipal;
import com.running.api.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
@Tag(name = "Actividades", description = "Ingesta, consulta, eliminación y exportación de las carreras del usuario autenticado")
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping
    @Operation(summary = "Listar las carreras del usuario autenticado",
               description = "Devuelve una página de actividades ordenada por fecha de inicio descendente. Soporta paginación y ordenación mediante los parámetros page, size y sort.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de actividades del usuario"),
            @ApiResponse(responseCode = "401", description = "No autenticado o token inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
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
            @ApiResponse(responseCode = "404", description = "La actividad no existe o no pertenece al usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Activity> getActivityById(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID de la actividad (ISO Timestamp o UUID)", example = "2026-09-05T07:00:00Z")
            @PathVariable String id) {
        return activityService.getActivityById(principal.id(), id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/trackpoints")
    @Operation(summary = "Obtener los trackpoints de una carrera",
               description = "Devuelve la telemetría segundo a segundo. Admite un muestreo opcional para reducir el tamaño de la respuesta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de trackpoints"),
            @ApiResponse(responseCode = "404", description = "La actividad no existe o no tiene trackpoints",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<List<ActivityTrackpoint>> getActivityTrackpointsById(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID de la actividad", example = "2026-09-05T07:00:00Z")
            @PathVariable String id,
            @Parameter(description = "Muestreo de puntos: low (baja densidad), medium (media) o high (todos). Sin valor = todos",
                       schema = @Schema(allowableValues = {"low", "medium", "high"}))
            @RequestParam(required = false) String resolution) {
        List<ActivityTrackpoint> trackpoints = activityService.getActivityTrackpointsById(principal.id(), id, resolution);
        if (trackpoints.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(trackpoints);
    }

    @DeleteMapping
    @Operation(summary = "Vaciar las actividades del usuario autenticado",
               description = "Elimina todas las carreras, trackpoints y récords personales del usuario y reinicia sus estadísticas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Actividades y estadísticas vaciadas"),
            @ApiResponse(responseCode = "401", description = "No autenticado o token inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Map<String, Object>> deleteAllActivities(@AuthenticationPrincipal UserPrincipal principal) {
        int count = activityService.deleteAllActivities(principal.id());
        return ResponseEntity.ok(Map.of(
                "message", "Actividades y estadísticas del usuario vaciadas con éxito",
                "deletedActivities", count
        ));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una carrera por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Carrera eliminada"),
            @ApiResponse(responseCode = "404", description = "La actividad no existe o no pertenece al usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Map<String, Object>> deleteActivityById(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID de la actividad a eliminar", example = "2026-09-05T07:00:00Z")
            @PathVariable String id) {
        boolean deleted = activityService.deleteActivityById(principal.id(), id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "message", "Carrera eliminada con éxito",
                "activityId", id
        ));
    }
}
