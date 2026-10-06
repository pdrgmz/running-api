package com.running.api.controller;

import com.running.api.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analítica Deportiva", description = "Métricas fisiológicas avanzadas, splits y desacople aeróbico")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/{id}/splits")
    @Operation(summary = "Desglose de splits por kilómetro")
    public ResponseEntity<List<Map<String, Object>>> getSplits(@PathVariable String id) {
        return ResponseEntity.ok(analyticsService.calculateSplits(id));
    }

    @GetMapping("/{id}/hr-zones")
    @Operation(summary = "Distribución del tiempo en las 5 zonas de frecuencia cardíaca")
    public ResponseEntity<Map<String, Object>> getHrZones(@PathVariable String id) {
        return ResponseEntity.ok(analyticsService.calculateHrZones(id));
    }

    @GetMapping("/{id}/cardiac-drift")
    @Operation(summary = "Análisis de desacople aeróbico (Cardiac Drift)")
    public ResponseEntity<Map<String, Object>> getCardiacDrift(@PathVariable String id) {
        return ResponseEntity.ok(analyticsService.calculateCardiacDrift(id));
    }
}
