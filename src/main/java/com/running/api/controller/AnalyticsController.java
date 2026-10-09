package com.running.api.controller;

import com.running.api.dto.*;
import com.running.api.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

// Import de los DTOs si están en el paquete dto
import com.running.api.dto.PmcPointDto;
import com.running.api.dto.EddingtonStatsDto;
import com.running.api.dto.StreakStatsDto;
import com.running.api.dto.WeekdayStatsDto;

import com.running.api.dto.DaytimeStatsDto;
import com.running.api.dto.EddingtonStatsDto;
import com.running.api.dto.PmcPointDto;
import com.running.api.dto.StreakStatsDto;
import com.running.api.dto.WeekdayStatsDto;
import com.running.api.service.AnalyticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

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

    @GetMapping("/pmc")
    @Operation(summary = "Obtener histórico PMC (CTL, ATL, TSB, ACWR)")
    public ResponseEntity<List<PmcPointDto>> getPmcHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        LocalDate effectiveEndDate = (endDate != null) ? endDate : LocalDate.now();
        LocalDate effectiveStartDate = (startDate != null) ? startDate : effectiveEndDate.minusDays(90);

        return ResponseEntity.ok(analyticsService.calculatePmcHistory(effectiveStartDate, effectiveEndDate));
    }

    @GetMapping("/eddington")
    @Operation(summary = "Calcular el Número de Eddington del corredor")
    public ResponseEntity<EddingtonStatsDto> getEddington() {
        return ResponseEntity.ok(analyticsService.calculateEddingtonNumber());
    }

    @GetMapping("/acwr/current")
    @Operation(
        summary = "Obtener estado actual del Acute:Chronic Workload Ratio (ACWR)",
        description = "Evalúa la relación de carga de los últimos 7 días contra los 42 días históricos y determina el nivel de riesgo de lesión."
    )
    public ResponseEntity<AcwrStatusDto> getCurrentAcwrStatus() {
        return ResponseEntity.ok(analyticsService.calculateCurrentAcwr());
    }
}
