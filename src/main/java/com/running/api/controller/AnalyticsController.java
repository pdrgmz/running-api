package com.running.api.controller;

import com.running.api.dto.*;
import com.running.api.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analítica Deportiva", description = "Métricas fisiológicas avanzadas, splits y desacople aeróbico")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/{id}/splits")
    @Operation(summary = "Desglose de splits por kilómetro")
    public ResponseEntity<List<SplitDto>> getSplits(@PathVariable String id) {
        return ResponseEntity.ok(analyticsService.calculateSplits(id));
    }

    @GetMapping("/{id}/hr-zones")
    @Operation(summary = "Distribución del tiempo en las 5 zonas de frecuencia cardíaca")
    public ResponseEntity<HrZonesResponseDto> getHrZones(@PathVariable String id) {
        return ResponseEntity.ok(analyticsService.calculateHrZones(id));
    }

    @GetMapping("/{id}/cardiac-drift")
    @Operation(summary = "Análisis de desacople aeróbico (Cardiac Drift)")
    public ResponseEntity<CardiacDriftResponseDto> getCardiacDrift(@PathVariable String id) {
        return ResponseEntity.ok(analyticsService.calculateCardiacDrift(id));
    }

    @GetMapping("/{id}/training-load")
    @Operation(summary = "Carga de entrenamiento de la sesión (TRIMP / HRSS / Esfuerzo)")
    public ResponseEntity<TrainingLoadResponseDto> getTrainingLoad(@PathVariable String id) {
        return ResponseEntity.ok(analyticsService.calculateTrainingLoad(id));
    }

    @GetMapping("/{id}/vo2max-vam")
    @Operation(summary = "Estimación del consumo de oxígeno (VO2Max) y Velocidad Aeróbica Máxima (VAM)")
    public ResponseEntity<Vo2MaxVamResponseDto> getVo2MaxAndVam(@PathVariable String id) {
        return ResponseEntity.ok(analyticsService.calculateVo2MaxAndVam(id));
    }
}
