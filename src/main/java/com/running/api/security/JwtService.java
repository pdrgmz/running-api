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
import java.time.Instant;
import java.util.Date;

/**
 * Emisión y validación de tokens JWT firmados con HMAC-SHA256.
 * <p>
 * Se emiten dos tipos de token, distinguidos por el claim {@code type}:
 * <ul>
 *   <li>{@code ACCESS}: token de acceso de corta duración, usado en
 *       {@code Authorization: Bearer <token>} para llamar a la API.</li>
 *   <li>{@code REFRESH}: token de larga duración, usado únicamente para obtener
 *       un nuevo token de acceso en {@code POST /api/v1/auth/refresh}.</li>
 * </ul>
 * Un refresh token no sirve como token de acceso ni viceversa.
 */
@Service
public class JwtService {

    public enum TokenType {
        ACCESS,
        REFRESH
    }

    private static final String TYPE_CLAIM = "type";

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
     * Valida un token de acceso (firma, expiración y tipo).
     *
     * @throws JwtException si el token es inválido, está manipulado, ha expirado
     *                      o no es un token de acceso.
     */
    public UserPrincipal parseToken(String token) {
        return parse(token, TokenType.ACCESS);
    }

    /**
     * Valida un refresh token (firma, expiración y tipo).
     *
     * @throws JwtException si el token es inválido, ha expirado o no es un refresh token.
     */
    public UserPrincipal parseRefreshToken(String token) {
        return parse(token, TokenType.REFRESH);
    }

    private UserPrincipal parse(String token, TokenType expected) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String type = claims.get(TYPE_CLAIM, String.class);
        if (type == null) {
            // Tokens previos (sin claim de tipo) solo se aceptan como acceso.
            if (expected == TokenType.REFRESH) {
                throw new JwtException("El token no es un refresh token válido");
            }
        } else if (!type.equalsIgnoreCase(expected.name())) {
            throw new JwtException("Tipo de token no válido: se esperaba " + expected);
        }

        return new UserPrincipal(
                Long.valueOf(claims.getSubject()),
                claims.get("email", String.class),
                claims.get("role", String.class));
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
