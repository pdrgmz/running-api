package com.running.api.controller;

import com.running.api.dto.DailyGenerationJobResponseDto;
import com.running.api.dto.DailyRecommendationResponseDto;
import com.running.api.dto.DailyRecommendationSummaryDto;
import com.running.api.dto.DeletedJobsResponseDto;
import com.running.api.dto.ErrorResponseDto;
import com.running.api.dto.GenerateDailyRecommendationRequestDto;
import com.running.api.dto.GenerateDailyRecommendationResponseDto;
import com.running.api.dto.TrainingPreferencesDto;
import com.running.api.dto.UpdateTrainingPreferencesRequestDto;
import com.running.api.model.TrainingJobStatus;
import com.running.api.security.UserPrincipal;
import com.running.api.service.DailyRecommendationGenerationService;
import com.running.api.service.DailyRecommendationService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/training")
@RequiredArgsConstructor
@Tag(name = "Entrenamientos",
        description = "Recomendación diaria generada por IA, estado de generación y preferencias del atleta")
public class TrainingController {

    private final DailyRecommendationService recommendationService;
    private final DailyRecommendationGenerationService generationService;

    @GetMapping("/recommendations/today")
    @Operation(summary = "Obtener la recomendación de hoy",
            description = "Devuelve la recomendación del día actual en la zona horaria del atleta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recomendación encontrada"),
            @ApiResponse(responseCode = "204", description = "Todavía no hay recomendación para hoy"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<DailyRecommendationResponseDto> getToday(
            @AuthenticationPrincipal UserPrincipal principal) {
        return recommendationService.getToday(principal.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/recommendations")
    @Operation(summary = "Listar recomendaciones diarias",
            description = "Lista el histórico, opcionalmente filtrado por un rango de fechas (from/to inclusivos). "
                    + "Sin filtros devuelve las últimas 30.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Listado de recomendaciones"))
    public ResponseEntity<List<DailyRecommendationSummaryDto>> listRecommendations(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Fecha inicial del rango", example = "2026-10-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Fecha final del rango", example = "2026-12-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(recommendationService.listRecommendations(principal.id(), from, to));
    }

    @GetMapping("/recommendations/{id}")
    @Operation(summary = "Obtener una recomendación por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recomendación encontrada"),
            @ApiResponse(responseCode = "404", description = "No existe o no pertenece al usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<DailyRecommendationResponseDto> getRecommendation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id) {
        return recommendationService.getRecommendationById(principal.id(), id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/recommendations/date/{date}")
    @Operation(summary = "Obtener la recomendación de un día")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recomendación encontrada"),
            @ApiResponse(responseCode = "404", description = "No hay recomendación para esa fecha",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<DailyRecommendationResponseDto> getRecommendationByDate(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Fecha de la recomendación", example = "2026-11-11")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return recommendationService.getRecommendation(principal.id(), date)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/recommendations/{id}")
    @Operation(summary = "Eliminar una recomendación",
            description = "Elimina la recomendación y los jobs que la referencian.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Recomendación eliminada"),
            @ApiResponse(responseCode = "404", description = "No existe o no pertenece al usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> deleteRecommendation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID de la recomendación", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            @PathVariable String id) {
        recommendationService.deleteRecommendation(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/recommendations/date/{date}")
    @Operation(summary = "Eliminar la recomendación de un día",
            description = "Elimina la recomendación de esa fecha y los jobs que la referencian.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Recomendación eliminada"),
            @ApiResponse(responseCode = "404", description = "No hay recomendación para esa fecha",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> deleteRecommendationByDate(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Fecha de la recomendación", example = "2026-11-11")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        recommendationService.deleteRecommendationByDate(principal.id(), date);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recommendations/generate")
    @Operation(summary = "Generar la recomendación de un día con IA",
            description = "Crea un job, firma un JWT efímero con aud=mcp y lo envía al webhook genérico de n8n. "
                    + "Responde 202; el estado se consulta en /generation-jobs/{requestId}. "
                    + "La recomendación solo existe cuando n8n llama a save_daily_recommendation y la API la persiste.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Generación encolada"),
            @ApiResponse(responseCode = "409", description = "Ya existe una recomendación o un job en curso para ese día",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "503", description = "El webhook de n8n no está configurado",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<GenerateDailyRecommendationResponseDto> generateRecommendation(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) GenerateDailyRecommendationRequestDto request) {
        boolean replace = request != null && Boolean.TRUE.equals(request.replace());
        LocalDate date = request == null ? null : request.date();
        GenerateDailyRecommendationResponseDto response = generationService.generateNow(
                principal.id(), date, replace);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/generation-jobs/{requestId}")
    @Operation(summary = "Consultar el estado de un job de generación")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado del job"),
            @ApiResponse(responseCode = "404", description = "El job no existe o no pertenece al usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<DailyGenerationJobResponseDto> getJob(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String requestId) {
        return ResponseEntity.ok(generationService.getJob(principal.id(), requestId));
    }

    @GetMapping("/generation-jobs")
    @Operation(summary = "Listar los últimos jobs de generación del usuario")
    public ResponseEntity<List<DailyGenerationJobResponseDto>> listJobs(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(recommendationService.listRecentJobs(principal.id()));
    }

    @DeleteMapping("/generation-jobs/{requestId}")
    @Operation(summary = "Eliminar un job de generación",
            description = "Borra el registro del job. No elimina la recomendación ya guardada. "
                    + "Los jobs en estado RUNNING requieren force=true porque n8n podría seguir trabajando.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Job eliminado"),
            @ApiResponse(responseCode = "404", description = "El job no existe o no pertenece al usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "El job está RUNNING y no se envió force=true",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> deleteJob(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "requestId del job", example = "8f3c2a1e-...")
            @PathVariable String requestId,
            @Parameter(description = "Permite eliminar también jobs en RUNNING", example = "false")
            @RequestParam(defaultValue = "false") boolean force) {
        recommendationService.deleteJob(principal.id(), requestId, force);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/generation-jobs")
    @Operation(summary = "Eliminar jobs de generación por estado",
            description = "Borra todos los jobs del usuario con el estado indicado. "
                    + "Útil para limpiar jobs atascados en PENDING antes de volver a generar. "
                    + "Los jobs RUNNING requieren force=true.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jobs eliminados"),
            @ApiResponse(responseCode = "400", description = "Estado no válido",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "Hay jobs RUNNING y no se envió force=true",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<DeletedJobsResponseDto> deleteJobs(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Estado a eliminar", example = "PENDING")
            @RequestParam TrainingJobStatus status,
            @Parameter(description = "Permite eliminar también jobs en RUNNING", example = "false")
            @RequestParam(defaultValue = "false") boolean force) {
        int deleted = recommendationService.deleteJobs(principal.id(), status, force);
        return ResponseEntity.ok(new DeletedJobsResponseDto(deleted));
    }

    @GetMapping("/preferences")
    @Operation(summary = "Obtener las preferencias de generación diaria",
            description = "Si el usuario no las ha configurado, devuelve configured=false y enabled=false.")
    public ResponseEntity<TrainingPreferencesDto> getPreferences(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(recommendationService.getPreferences(principal.id()));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Actualizar las preferencias de generación diaria",
            description = "Activa o desactiva la generación automática, define la zona horaria y la hora local "
                    + "de generación. Las notas se envían al LLM en get_training_context.")
    public ResponseEntity<TrainingPreferencesDto> updatePreferences(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody UpdateTrainingPreferencesRequestDto request) {
        return ResponseEntity.ok(recommendationService.updatePreferences(principal.id(), request));
    }
}
