package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos necesarios para registrar un nuevo usuario")
public class RegisterRequestDto {

    @Schema(description = "Email del nuevo usuario", example = "runner@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato válido")
    private String email;

    @Schema(description = "Contraseña del usuario (entre 8 y 100 caracteres)", example = "password123",
            requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, maxLength = 100)
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
    private String password;
}
