package com.running.api.controller;

import com.running.api.dto.ErrorResponseDto;
import com.running.api.dto.RunnerZonesResponseDto;
import com.running.api.dto.UpdateRunnerZonesRequestDto;
import com.running.api.security.UserPrincipal;
import com.running.api.service.RunnerZonesService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/zones")
@RequiredArgsConstructor
@Tag(name = "Zonas del corredor",
        description = "Perfil de zonas de frecuencia cardíaca y de ritmo del usuario autenticado")
public class ZonesController {

    private final RunnerZonesService runnerZonesService;

    @GetMapping
    @Operation(summary = "Obtener las zonas del corredor",
            description = "Devuelve el perfil guardado o, si aún no existe, un perfil por defecto calculado "
                    + "desde el historial (FC máxima robusta y marcas personales) sin persistirlo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil de zonas del corredor"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<RunnerZonesResponseDto> getZones(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(runnerZonesService.getZones(principal.id()));
    }

    @PostMapping("/calculate")
    @Operation(summary = "Calcular y guardar las zonas del corredor",
            description = "Recalcula las zonas de FC con la FC máxima robusta del historial y las zonas de ritmo "
                    + "con las marcas personales (o la actividad elegible más rápida). Sobrescribe el perfil "
                    + "anterior y lo deja marcado con source=CALCULATED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zonas recalculadas y guardadas"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<RunnerZonesResponseDto> calculateZones(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(runnerZonesService.calculateAndSave(principal.id()));
    }

    @PutMapping
    @Operation(summary = "Actualizar manualmente las zonas del corredor",
            description = "Permite editar la FC máxima, la FC de reposo, el ritmo umbral y los cinco rangos de cada "
                    + "tipo de zona. Las secciones omitidas conservan su valor; si se envían zones, el método pasa "
                    + "a CUSTOM y se validan los rangos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zonas actualizadas"),
            @ApiResponse(responseCode = "400", description = "Parámetros fisiológicos o rangos no válidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<RunnerZonesResponseDto> updateZones(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody UpdateRunnerZonesRequestDto request) {
        return ResponseEntity.ok(runnerZonesService.updateZones(principal.id(), request));
    }
}
