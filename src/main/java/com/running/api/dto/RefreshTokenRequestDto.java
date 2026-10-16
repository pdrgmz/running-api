package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de renovación del token de acceso")
public class RefreshTokenRequestDto {

    @Schema(description = "Refresh token obtenido en el registro o login",
            example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidHlwZSI6IlJFRlJFU0gifQ.def456",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El refresh token es obligatorio")
    private String refreshToken;
}
