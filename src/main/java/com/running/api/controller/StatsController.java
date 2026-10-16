package com.running.api.controller;

import com.running.api.dto.ErrorResponseDto;
import com.running.api.dto.RecordResponseDto;
import com.running.api.dto.SummaryStatsDto;
import com.running.api.security.UserPrincipal;
import com.running.api.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
@Tag(name = "Estadísticas y Récords", description = "Resumen acumulado del usuario y cálculo de marcas personales (PB)")
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/summary")
    @Operation(summary = "Obtener resumen acumulado o filtrado por rango temporal",
               description = "Sin parámetros devuelve el acumulado total. Puede filtrarse por periodo (THIS_MONTH, THIS_YEAR, LAST_MONTH, LAST_YEAR) o por año concreto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen de estadísticas"),
            @ApiResponse(responseCode = "400", description = "Parámetro period o year inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<SummaryStatsDto> getSummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Periodo predefinido",
                       schema = @Schema(allowableValues = {"THIS_MONTH", "LAST_MONTH", "THIS_YEAR", "LAST_YEAR"}))
            @RequestParam(required = false) String period,
            @Parameter(description = "Año concreto a filtrar (alternativo a period)", example = "2026")
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(statsService.getSummary(principal.id(), period, year));
    }

    @GetMapping("/records")
    @Operation(summary = "Obtener mejores marcas personales (Personal Bests)",
               description = "Récords para 1k, 5k, 10k, 15k, 21k y 42k calculados con ventana móvil de telemetría.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Lista de marcas personales"))
    public ResponseEntity<List<RecordResponseDto>> getRecords(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(statsService.getPersonalRecords(principal.id()));
    }

    @PostMapping("/recalculate")
    @Operation(summary = "Recalcular desde cero las estadísticas y los récords personales del usuario",
               description = "Recalcula todas las estadísticas acumuladas (total de actividades, distancia, tiempo, elevación, calorías) y todos los récords personales (1k, 5k, 10k, 15k, 21k, 42k) a partir de las actividades del usuario.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Recálculo completado"))
    public ResponseEntity<String> recalculate(@AuthenticationPrincipal UserPrincipal principal) {
        statsService.recalculateStatsAndRecords(principal.id());
        return ResponseEntity.ok("Estadísticas y records recalculados exitosamente");
    }
}
