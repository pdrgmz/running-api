package com.running.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Usuario de la aplicación y propietario de sus actividades")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID del usuario", example = "1")
    private Long id;

    @Column(nullable = false, unique = true)
    @Schema(description = "Email único del usuario (identificador de login)", example = "runner@example.com")
    private String email;

    /** Hash BCrypt; nunca se serializa. */
    @JsonIgnore
    @Column(nullable = false)
    @Schema(description = "Hash BCrypt de la contraseña (nunca se expone)", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    @Column(nullable = false)
    @Builder.Default
    @Schema(description = "Rol del usuario", example = "USER", allowableValues = {"USER", "ADMIN"})
    private String role = "USER";

    @Column(name = "created_at")
    @Schema(description = "Fecha y hora de alta del usuario", example = "2026-10-15T09:00:00")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
