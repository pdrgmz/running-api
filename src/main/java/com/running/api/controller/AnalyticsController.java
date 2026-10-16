package com.running.api.controller;

import com.running.api.dto.AcwrStatusDto;
import com.running.api.dto.CardiacDriftResponseDto;
import com.running.api.dto.EddingtonStatsDto;
import com.running.api.dto.ErrorResponseDto;
import com.running.api.dto.HrZonesResponseDto;
import com.running.api.dto.PmcPointDto;
import com.running.api.dto.SplitDto;
import com.running.api.dto.TrainingLoadResponseDto;
import com.running.api.dto.Vo2MaxVamResponseDto;
import com.running.api.security.UserPrincipal;
import com.running.api.service.AnalyticsService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analítica Deportiva", description = "Métricas fisiológicas avanzadas, splits, zonas de FC y desacople aeróbico del usuario autenticado")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/{id}/splits")
    @Operation(summary = "Desglose de splits por kilómetro",
               description = "Calcula el ritmo, la frecuencia cardíaca media y el desnivel de cada kilómetro de la actividad.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Splits por kilómetro"),
            @ApiResponse(responseCode = "404", description = "La actividad no existe o no pertenece al usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<List<SplitDto>> getSplits(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID de la actividad", example = "2026-09-05T07:00:00Z")
            @PathVariable String id) {
        return ResponseEntity.ok(analyticsService.calculateSplits(principal.id(), id));
    }

    @GetMapping("/{id}/hr-zones")
    @Operation(summary = "Distribución del tiempo en las 5 zonas de frecuencia cardíaca")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Distribución por zonas de FC"),
            @ApiResponse(responseCode = "404", description = "La actividad no existe o no pertenece al usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<HrZonesResponseDto> getHrZones(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID de la actividad", example = "2026-09-05T07:00:00Z")
            @PathVariable String id) {
        return ResponseEntity.ok(analyticsService.calculateHrZones(principal.id(), id));
    }

    @GetMapping("/{id}/cardiac-drift")
    @Operation(summary = "Análisis de desacople aeróbico (Cardiac Drift)",
               description = "Compara la eficiencia (velocidad/FC) de la primera y la segunda mitad para estimar la deriva cardíaca.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado del análisis de desacople"),
            @ApiResponse(responseCode = "404", description = "La actividad no existe o no pertenece al usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<CardiacDriftResponseDto> getCardiacDrift(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID de la actividad", example = "2026-09-05T07:00:00Z")
            @PathVariable String id) {
        return ResponseEntity.ok(analyticsService.calculateCardiacDrift(principal.id(), id));
    }

    @GetMapping("/{id}/training-load")
    @Operation(summary = "Carga de entrenamiento de la sesión (TRIMP / HRSS / Esfuerzo)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Carga de entrenamiento calculada"),
            @ApiResponse(responseCode = "404", description = "La actividad no existe o no pertenece al usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<TrainingLoadResponseDto> getTrainingLoad(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID de la actividad", example = "2026-09-05T07:00:00Z")
            @PathVariable String id) {
        return ResponseEntity.ok(analyticsService.calculateTrainingLoad(principal.id(), id));
    }

    @GetMapping("/{id}/vo2max-vam")
    @Operation(summary = "Estimación del consumo de oxígeno (VO2Max) y Velocidad Aeróbica Máxima (VAM)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estimaciones de VO2Max y VAM"),
            @ApiResponse(responseCode = "404", description = "La actividad no existe o no pertenece al usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Vo2MaxVamResponseDto> getVo2MaxAndVam(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID de la actividad", example = "2026-09-05T07:00:00Z")
            @PathVariable String id) {
        return ResponseEntity.ok(analyticsService.calculateVo2MaxAndVam(principal.id(), id));
    }

    @GetMapping("/pmc")
    @Operation(summary = "Obtener histórico PMC (CTL, ATL, TSB, ACWR)",
               description = "Serie diaria del Performance Management Chart. Por defecto devuelve los últimos 90 días.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Serie diaria de puntos PMC"))
    public ResponseEntity<List<PmcPointDto>> getPmcHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Fecha inicial (ISO, yyyy-MM-dd). Por defecto: fin - 90 días", example = "2026-07-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Fecha final (ISO, yyyy-MM-dd). Por defecto: hoy", example = "2026-10-15")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        LocalDate effectiveEndDate = (endDate != null) ? endDate : LocalDate.now();
        LocalDate effectiveStartDate = (startDate != null) ? startDate : effectiveEndDate.minusDays(90);

        return ResponseEntity.ok(analyticsService.calculatePmcHistory(principal.id(), effectiveStartDate, effectiveEndDate));
    }

    @GetMapping("/eddington")
    @Operation(summary = "Calcular el Número de Eddington del corredor",
               description = "Métrica de consistencia: mayor E tal que se han corrido al menos E kilómetros en E días distintos.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Número de Eddington y progreso hacia el siguiente nivel"))
    public ResponseEntity<EddingtonStatsDto> getEddington(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(analyticsService.calculateEddingtonNumber(principal.id()));
    }

    @GetMapping("/acwr/current")
    @Operation(summary = "Obtener estado actual del Acute:Chronic Workload Ratio (ACWR)",
               description = "Evalúa la relación de carga de los últimos 7 días contra los 42 días históricos y determina el nivel de riesgo de lesión.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Estado ACWR actual y recomendación"))
    public ResponseEntity<AcwrStatusDto> getCurrentAcwrStatus(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(analyticsService.calculateCurrentAcwr(principal.id()));
    }
}
