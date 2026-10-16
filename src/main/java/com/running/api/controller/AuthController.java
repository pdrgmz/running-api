package com.running.api.controller;

import com.running.api.dto.AuthResponseDto;
import com.running.api.dto.ErrorResponseDto;
import com.running.api.dto.LoginRequestDto;
import com.running.api.dto.RefreshTokenRequestDto;
import com.running.api.dto.RegisterRequestDto;
import com.running.api.dto.UserResponseDto;
import com.running.api.security.UserPrincipal;
import com.running.api.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Registro, inicio de sesión, renovación de token y datos del usuario autenticado")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Registrar un nuevo usuario y devolver sus tokens JWT",
               description = "Crea un usuario con la contraseña cifrada con BCrypt y devuelve el token de acceso y el refresh token. Endpoint público.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario registrado y tokens emitidos"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (email mal formado o contraseña de menos de 8 caracteres)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe un usuario con ese email",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public AuthResponseDto register(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Credenciales del nuevo usuario", required = true)
            @Valid @RequestBody RegisterRequestDto request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Iniciar sesión y obtener los tokens JWT",
               description = "Valida las credenciales y devuelve el token de acceso y el refresh token. Endpoint público.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticación correcta y tokens emitidos"),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public AuthResponseDto login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Email y contraseña del usuario", required = true)
            @Valid @RequestBody LoginRequestDto request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Renovar el token de acceso usando un refresh token",
               description = "Recibe un refresh token válido y devuelve un nuevo par de tokens (acceso + refresco). No requiere cabecera Authorization: se autentica con el propio refresh token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nuevos tokens emitidos"),
            @ApiResponse(responseCode = "400", description = "Falta el refresh token en el cuerpo",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token inválido o expirado",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public AuthResponseDto refresh(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Refresh token obtenido en login/registro", required = true)
            @Valid @RequestBody RefreshTokenRequestDto request) {
        return authService.refresh(request.getRefreshToken());
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Obtener los datos del usuario autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Datos del usuario autenticado"),
            @ApiResponse(responseCode = "401", description = "No autenticado o token inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public UserResponseDto me(@AuthenticationPrincipal UserPrincipal principal) {
        return UserResponseDto.builder()
                .id(principal.id())
                .email(principal.email())
                .role(principal.role())
                .build();
    }
}
