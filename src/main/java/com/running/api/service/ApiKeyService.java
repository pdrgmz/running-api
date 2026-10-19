package com.running.api.service;

import com.running.api.dto.ApiKeyCreatedResponseDto;
import com.running.api.dto.ApiKeyResponseDto;
import com.running.api.dto.CreateApiKeyRequestDto;
import com.running.api.exception.ResourceNotFoundException;
import com.running.api.model.ApiKey;
import com.running.api.model.User;
import com.running.api.repository.ApiKeyRepository;
import com.running.api.repository.UserRepository;
import com.running.api.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Gestión y validación de claves de API para automatizaciones (n8n, scripts, cron).
 * <p>
 * El valor en claro de la clave solo se devuelve al crearla; en la base de datos
 * se almacena únicamente su hash SHA-256. Las claves actúan con la identidad y
 * los permisos del usuario propietario.
 */
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    /** Prefijo de todas las claves generadas. */
    public static final String KEY_PREFIX = "rk_";

    /** Número de bytes aleatorios de cada clave (256 bits de entropía). */
    private static final int KEY_RANDOM_BYTES = 32;

    /** Caracteres visibles de la clave para poder identificarla en listados. */
    private static final int DISPLAY_PREFIX_LENGTH = KEY_PREFIX.length() + 8;

    /** Frecuencia mínima con la que se persiste {@code lastUsedAt}. */
    private static final Duration LAST_USED_THROTTLE = Duration.ofMinutes(5);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;

    /**
     * Crea una clave para el usuario y devuelve su valor en claro, que no se
     * volverá a mostrar.
     */
    @Transactional
    public ApiKeyCreatedResponseDto create(Long userId, CreateApiKeyRequestDto request) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        String rawKey = generateRawKey();
        ApiKey apiKey = ApiKey.builder()
                .userId(userId)
                .name(request.getName().trim())
                .keyHash(sha256Hex(rawKey))
                .keyPrefix(rawKey.substring(0, DISPLAY_PREFIX_LENGTH))
                .expiresAt(request.getExpiresInDays() == null
                        ? null
                        : LocalDateTime.now().plusDays(request.getExpiresInDays()))
                .build();
        apiKeyRepository.save(apiKey);

        return ApiKeyCreatedResponseDto.builder()
                .id(apiKey.getId())
                .name(apiKey.getName())
                .key(rawKey)
                .keyPrefix(apiKey.getKeyPrefix())
                .createdAt(apiKey.getCreatedAt())
                .expiresAt(apiKey.getExpiresAt())
                .build();
    }

    /** Lista los metadatos de las claves del usuario, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<ApiKeyResponseDto> list(Long userId) {
        return apiKeyRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    /** Revoca (elimina) una clave del usuario. */
    @Transactional
    public void delete(Long userId, Long keyId) {
        ApiKey apiKey = apiKeyRepository.findByIdAndUserId(keyId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Clave de API no encontrada"));
        apiKeyRepository.delete(apiKey);
    }

    /**
     * Valida una clave recibida por cabecera y, si es correcta y no ha caducado,
     * devuelve la identidad del usuario propietario.
     */
    @Transactional
    public Optional<UserPrincipal> authenticate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        Optional<ApiKey> found = apiKeyRepository.findByKeyHash(sha256Hex(rawKey));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        ApiKey apiKey = found.get();
        if (apiKey.getExpiresAt() != null && !apiKey.getExpiresAt().isAfter(LocalDateTime.now())) {
            return Optional.empty();
        }

        Optional<User> user = userRepository.findById(apiKey.getUserId());
        if (user.isEmpty()) {
            return Optional.empty();
        }

        touchLastUsed(apiKey);
        return Optional.of(new UserPrincipal(user.get().getId(), user.get().getEmail(), user.get().getRole()));
    }

    private void touchLastUsed(ApiKey apiKey) {
        LocalDateTime now = LocalDateTime.now();
        if (apiKey.getLastUsedAt() == null || apiKey.getLastUsedAt().isBefore(now.minus(LAST_USED_THROTTLE))) {
            apiKey.setLastUsedAt(now);
            apiKeyRepository.save(apiKey);
        }
    }

    private ApiKeyResponseDto toDto(ApiKey apiKey) {
        return ApiKeyResponseDto.builder()
                .id(apiKey.getId())
                .name(apiKey.getName())
                .keyPrefix(apiKey.getKeyPrefix())
                .createdAt(apiKey.getCreatedAt())
                .expiresAt(apiKey.getExpiresAt())
                .lastUsedAt(apiKey.getLastUsedAt())
                .build();
    }

    private static String generateRawKey() {
        byte[] random = new byte[KEY_RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(random);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
