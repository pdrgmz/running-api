package com.running.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.running.api.dto.ElevationCoverageDto;
import com.running.api.dto.ErrorResponseDto;
import com.running.api.dto.TrackpointMetricsBatchCompletionResultDto;
import com.running.api.security.UserPrincipal;
import com.running.api.service.GlobalActivityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
@Tag(name = "Control global de Actividades", description = "Operaciones globales sobre las carreras del usuario autenticado")
public class GlobalActivityController {

        private final GlobalActivityService globalActivityService;

        @GetMapping("/elevation-coverage")
        @Operation(summary = "Obtener la cobertura de altitudes de los trackpoints", description = "Devuelve cuántos trackpoints tienen altitud y cuántos la tienen pendiente, "
                        + "con sus porcentajes. Una altitud nula o 0 se considera pendiente, igual que en "
                        + "el cron de completado. También se consideran pendientes los trackpoints que "
                        + "comparten una altitud constante sospechosa (el mismo valor exacto en al menos "
                        + "el 90% de los puntos del dispositivo), que se informan aparte.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Cobertura calculada"),
                        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
        })
        public ResponseEntity<ElevationCoverageDto> getElevationCoverage(
                        @AuthenticationPrincipal UserPrincipal principal) {
                return ResponseEntity.ok(globalActivityService.getElevationCoverage(principal.id()));
        }

        @PostMapping("/complete-metrics")
        @Operation(summary = "Completar FC, cadencia, velocidad y distancia en todas las carreras", description = "Recorre todas las actividades del usuario autenticado y aplica el mismo "
                        + "completado que el endpoint individual: solo rellena los valores nulos y "
                        + "los claramente atípicos (muy alejados de la mediana de sus vecinos o "
                        + "imposibles), interpola FC, cadencia y velocidad, y ajusta la distancia "
                        + "acumulada. No modifica coordenadas ni altitud y no consulta proveedores "
                        + "externos. Cada actividad se procesa en su propia transacción; si una falla, "
                        + "las demás continúan. Al final se recalculan estadísticas y récords.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Proceso masivo completado"),
                        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
        })
        public ResponseEntity<TrackpointMetricsBatchCompletionResultDto> completeAllMetrics(
                        @AuthenticationPrincipal UserPrincipal principal) {
                return ResponseEntity.ok(globalActivityService.completeAllActivityMetrics(principal.id()));
        }

}
