package com.running.api.controller;

import com.running.api.dto.ApiKeyCreatedResponseDto;
import com.running.api.dto.ApiKeyResponseDto;
import com.running.api.dto.CreateApiKeyRequestDto;
import com.running.api.dto.ErrorResponseDto;
import com.running.api.security.UserPrincipal;
import com.running.api.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth/api-keys")
@RequiredArgsConstructor
@Tag(name = "Claves de API", description = "Credenciales de máquina (cabecera X-Api-Key) para automatizaciones como n8n, scripts o cron")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una clave de API",
               description = "Genera una clave con prefijo 'rk_' para usarla en la cabecera 'X-Api-Key' como alternativa al JWT. "
                       + "El valor completo solo se muestra en esta respuesta: guárdalo de forma segura. "
                       + "La clave tiene acceso completo a los datos del usuario que la crea.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Clave creada. El campo 'key' solo se muestra una vez"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (nombre vacío o vigencia fuera de rango)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado o token inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ApiKeyCreatedResponseDto create(
            @AuthenticationPrincipal UserPrincipal principal,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Nombre y vigencia opcional de la clave", required = true)
            @Valid @RequestBody CreateApiKeyRequestDto request) {
        return apiKeyService.create(principal.id(), request);
    }

    @GetMapping
    @Operation(summary = "Listar las claves de API del usuario",
               description = "Devuelve los metadatos de las claves (nombre, prefijo, fechas) sin su valor secreto.")
    @ApiResponse(responseCode = "200", description = "Listado de claves del usuario autenticado")
    public List<ApiKeyResponseDto> list(@AuthenticationPrincipal UserPrincipal principal) {
        return apiKeyService.list(principal.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revocar una clave de API",
               description = "Elimina la clave indicada. Las peticiones que la usen dejarán de autenticarse inmediatamente. "
                       + "Solo es posible revocar claves propias.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Clave revocada"),
            @ApiResponse(responseCode = "404", description = "No existe una clave con ese ID para el usuario autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public void delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID de la clave a revocar", example = "1")
            @PathVariable Long id) {
        apiKeyService.delete(principal.id(), id);
    }
}
