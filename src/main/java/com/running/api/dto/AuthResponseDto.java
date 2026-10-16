package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta de autenticación: tokens JWT y datos del usuario")
public class AuthResponseDto {

    @Schema(description = "Token de acceso JWT. Úsalo en la cabecera 'Authorization: Bearer <token>'",
            example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abc123")
    private String token;

    @Schema(description = "Tipo de token (siempre 'Bearer')", example = "Bearer")
    private String tokenType;

    @Schema(description = "Vigencia del token de acceso en segundos", example = "86400")
    private long expiresInSeconds;

    @Schema(description = "Refresh token JWT para renovar el token de acceso sin volver a iniciar sesión",
            example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidHlwZSI6IlJFRlJFU0gifQ.def456")
    private String refreshToken;

    @Schema(description = "Vigencia del refresh token en segundos", example = "604800")
    private long refreshExpiresInSeconds;

    @Schema(description = "Datos del usuario autenticado")
    private UserResponseDto user;
}
