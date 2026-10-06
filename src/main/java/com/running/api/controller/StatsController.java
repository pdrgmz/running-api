package com.running.api.controller;

import com.running.api.dto.RecordResponseDto;
import com.running.api.dto.SummaryStatsDto;
import com.running.api.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
@Tag(name = "Estadísticas y Récords", description = "Resumen global acumulado y cálculo de marcas personales (PB)")
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/summary")
    @Operation(summary = "Obtener resumen global acumulado", description = "Distancia total, tiempo total, desnivel acumulado y promedios.")
    public ResponseEntity<SummaryStatsDto> getSummary() {
        return ResponseEntity.ok(statsService.getGlobalSummary());
    }

    @GetMapping("/records")
    @Operation(summary = "Obtener mejores marcas personales (Personal Bests)", description = "Récords para 1k, 5k, 10k, 15k, 21k y 42k calculados con ventana móvil de telemetría.")
    public ResponseEntity<List<RecordResponseDto>> getRecords() {
        return ResponseEntity.ok(statsService.getPersonalRecords());
    }
}
