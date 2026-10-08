package com.running.api.controller;

import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
@Tag(name = "Actividades", description = "Endpoints para la ingesta, consulta, eliminación y exportación de carreras")
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping
    @Operation(summary = "Listar todas las carreras registradas")
    public ResponseEntity<List<Activity>> getAllActivities() {
        return ResponseEntity.ok(activityService.getAllActivities());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener el resumen de una carrera por ID")
    public ResponseEntity<Activity> getActivityById(@PathVariable String id) {
        return activityService.getActivityById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/trackpoints")
    @Operation(summary = "Obtener los trackpoints de una carrera por ID")
    public ResponseEntity<List<ActivityTrackpoint>> getActivityTrackpointsById(@PathVariable String id) {
        List<ActivityTrackpoint> trackpoints = activityService.getActivityTrackpointsById(id);
        if (trackpoints.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(trackpoints);
    }

    @DeleteMapping
    @Operation(summary = "Vaciar toda la base de datos", description = "Elimina todas las carreras, trackpoints, récords personales y reinicia las estadísticas globales.")
    public ResponseEntity<Map<String, Object>> deleteAllActivities() {
        int count = activityService.deleteAllActivities();
        return ResponseEntity.ok(Map.of(
                "message", "Base de datos y estadísticas vaciadas con éxito",
                "deletedActivities", count
        ));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una carrera por ID")
    public ResponseEntity<Map<String, Object>> deleteActivityById(@PathVariable String id) {
        boolean deleted = activityService.deleteActivityById(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "message", "Carrera eliminada con éxito",
                "activityId", id
        ));
    }
}
