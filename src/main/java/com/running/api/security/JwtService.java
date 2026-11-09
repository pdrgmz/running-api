package com.running.api.security;

import com.running.api.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Emisión y validación de tokens JWT firmados con HMAC-SHA256.
 * <p>
 * Se emiten tres tipos de token, distinguidos por el claim {@code type}:
 * <ul>
 *   <li>{@code ACCESS}: token de acceso de corta duración, usado en
 *       {@code Authorization: Bearer <token>} para llamar a la API.</li>
 *   <li>{@code REFRESH}: token de larga duración, usado únicamente para obtener
 *       un nuevo token de acceso en {@code POST /api/v1/auth/refresh}.</li>
 *   <li>{@code PLAN_GENERATION}: token efímero y restringido ({@code aud=mcp},
 *       scopes {@code training:read training:write}) que la API entrega a n8n
 *       para leer el contexto y guardar un plan semanal. Solo es válido en
 *       {@code /mcp}.</li>
 * </ul>
 * Un refresh token no sirve como token de acceso ni viceversa.
 */
@Service
public class JwtService {

    public enum TokenType {
        ACCESS,
        REFRESH,
        PLAN_GENERATION
    }

    private static final String TYPE_CLAIM = "type";
    private static final String SCOPE_CLAIM = "scope";
    private static final String REQUEST_ID_CLAIM = "requestId";
    private static final String MCP_AUDIENCE = "mcp";

    /** Scopes del token efímero que usa n8n para generar un plan. */
    public static final String SCOPE_TRAINING_READ = "training:read";
    public static final String SCOPE_TRAINING_WRITE = "training:write";
    public static final String PLAN_GENERATION_SCOPES =
            SCOPE_TRAINING_READ + " " + SCOPE_TRAINING_WRITE;

    private final SecretKey key;
    private final long accessExpirationMillis;
    private final long refreshExpirationMillis;

    public JwtService(@Value("${app.auth.jwt-secret}") String secret,
                      @Value("${app.auth.jwt-expiration-minutes:1440}") long accessExpirationMinutes,
                      @Value("${app.auth.refresh-expiration-minutes:10080}") long refreshExpirationMinutes) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // HS256 exige una clave de al menos 256 bits: derivamos una con SHA-256
            // para no fallar si el secreto configurado es corto.
            keyBytes = sha256(keyBytes);
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpirationMillis = accessExpirationMinutes * 60_000L;
        this.refreshExpirationMillis = refreshExpirationMinutes * 60_000L;
    }

    /** Token de acceso (corta duración). */
    public String generateToken(User user) {
        return generate(user, TokenType.ACCESS, accessExpirationMillis);
    }

    /** Token de refresco (larga duración). */
    public String generateRefreshToken(User user) {
        return generate(user, TokenType.REFRESH, refreshExpirationMillis);
    }

    /**
     * Token efímero para que n8n genere y guarde un plan de entrenamiento.
     * <p>
     * La identidad sigue siendo el {@code sub} del usuario: n8n no necesita
     * conocer ni enviar el {@code userId}. El token solo es aceptado en
     * {@code /mcp} y lleva el {@code requestId} del job para correlacionar el
     * guardado.
     */
    public String generatePlanGenerationToken(User user, String requestId, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .claim(TYPE_CLAIM, TokenType.PLAN_GENERATION.name())
                .claim(SCOPE_CLAIM, PLAN_GENERATION_SCOPES)
                .claim(REQUEST_ID_CLAIM, requestId)
                .audience().add(MCP_AUDIENCE).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    private String generate(User user, TokenType type, long ttlMillis) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .claim(TYPE_CLAIM, type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMillis)))
                .signWith(key)
                .compact();
    }

    /**
     * Valida un token utilizable como credencial de acceso (firma, expiración
     * y tipo). Se aceptan tokens {@code ACCESS} y {@code PLAN_GENERATION}.
     *
     * @throws JwtException si el token es inválido, está manipulado, ha expirado
     *                      o es un refresh token.
     */
    public UserPrincipal parseToken(String token) {
        Claims claims = parseClaims(token);
        TokenType type = resolveType(claims);
        if (type == TokenType.REFRESH) {
            throw new JwtException("El token no es un token de acceso válido");
        }
        return toPrincipal(claims);
    }

    /**
     * Valida un refresh token (firma, expiración y tipo).
     *
     * @throws JwtException si el token es inválido, ha expirado o no es un refresh token.
     */
    public UserPrincipal parseRefreshToken(String token) {
        Claims claims = parseClaims(token);
        TokenType type = resolveType(claims);
        if (type != TokenType.REFRESH) {
            throw new JwtException("El token no es un refresh token válido");
        }
        return toPrincipal(claims);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private TokenType resolveType(Claims claims) {
        String type = claims.get(TYPE_CLAIM, String.class);
        if (type == null || type.isBlank()) {
            // Tokens previos (sin claim de tipo) solo se aceptan como acceso.
            return TokenType.ACCESS;
        }
        try {
            return TokenType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new JwtException("Tipo de token no válido: " + type);
        }
    }

    private UserPrincipal toPrincipal(Claims claims) {
        Set<String> scopes = parseScopes(claims.get(SCOPE_CLAIM));
        String audience = claims.getAudience() == null || claims.getAudience().isEmpty()
                ? null
                : claims.getAudience().stream().findFirst().orElse(null);
        return new UserPrincipal(
                Long.valueOf(claims.getSubject()),
                claims.get("email", String.class),
                claims.get("role", String.class),
                scopes,
                audience,
                claims.get(REQUEST_ID_CLAIM, String.class));
    }

    private static Set<String> parseScopes(Object raw) {
        if (raw == null) {
            return Set.of();
        }
        if (raw instanceof String value) {
            return Arrays.stream(value.trim().split("\\s+"))
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
        }
        if (raw instanceof Iterable<?> values) {
            return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                    .map(String::valueOf)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }

    public long getExpirationSeconds() {
        return accessExpirationMillis / 1000;
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpirationMillis / 1000;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
