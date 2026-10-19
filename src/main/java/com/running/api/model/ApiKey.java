package com.running.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Credencial de máquina asociada a un usuario. Permite autenticar peticiones
 * con la cabecera {@code X-Api-Key} sin gestionar tokens JWT.
 * <p>
 * El valor en claro de la clave solo se muestra al crearla: en la base de datos
 * se almacena únicamente su hash SHA-256 ({@code key_hash}).
 */
@Entity
@Table(name = "api_keys",
        uniqueConstraints = @UniqueConstraint(name = "uk_api_keys_hash", columnNames = "key_hash"),
        indexes = @Index(name = "idx_api_keys_user_id", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Clave de API para automatizaciones (n8n, scripts, cron)")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID de la clave", example = "1")
    private Long id;

    @Column(name = "user_id", nullable = false)
    @Schema(description = "ID del usuario propietario de la clave")
    private Long userId;

    @Column(nullable = false, length = 100)
    @Schema(description = "Nombre descriptivo de la clave", example = "n8n")
    private String name;

    /** Hash SHA-256 en hexadecimal; nunca se serializa. */
    @JsonIgnore
    @Column(name = "key_hash", nullable = false, length = 64)
    @Schema(description = "Hash SHA-256 de la clave (nunca se expone)", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 16)
    @Schema(description = "Primeros caracteres de la clave, para identificarla en listados", example = "rk_Xk3pQ8aB")
    private String keyPrefix;

    @Column(name = "created_at")
    @Schema(description = "Fecha y hora de creación de la clave", example = "2026-10-18T09:00:00")
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    @Schema(description = "Fecha y hora de caducidad (null si no caduca)", example = "2027-10-18T09:00:00")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    @Schema(description = "Última vez que la clave autenticó una petición", example = "2026-10-18T10:30:00")
    private LocalDateTime lastUsedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
