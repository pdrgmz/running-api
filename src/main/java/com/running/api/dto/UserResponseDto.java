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
@Schema(description = "Datos públicos del usuario autenticado")
public class UserResponseDto {

    @Schema(description = "ID del usuario", example = "1")
    private Long id;

    @Schema(description = "Email del usuario", example = "runner@example.com")
    private String email;

    @Schema(description = "Rol del usuario", example = "USER")
    private String role;
}
