package com.running.api.security;

import com.running.api.model.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-long-enough-for-hs256-0123456789abcdef";

    private final JwtService jwtService = new JwtService(SECRET, 60, 10080);

    private User user() {
        return User.builder().id(42L).email("runner@example.com").role("USER").build();
    }

    @Test
    void generatesAndParsesAccessToken() {
        String token = jwtService.generateToken(user());

        UserPrincipal principal = jwtService.parseToken(token);

        assertThat(principal.id()).isEqualTo(42L);
        assertThat(principal.email()).isEqualTo("runner@example.com");
        assertThat(principal.role()).isEqualTo("USER");
    }

    @Test
    void generatesAndParsesRefreshToken() {
        String refreshToken = jwtService.generateRefreshToken(user());

        UserPrincipal principal = jwtService.parseRefreshToken(refreshToken);

        assertThat(principal.id()).isEqualTo(42L);
    }

    @Test
    void accessTokenCannotBeUsedAsRefreshToken() {
        String accessToken = jwtService.generateToken(user());

        assertThatThrownBy(() -> jwtService.parseRefreshToken(accessToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        String refreshToken = jwtService.generateRefreshToken(user());

        assertThatThrownBy(() -> jwtService.parseToken(refreshToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateToken(user());
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThatThrownBy(() -> jwtService.parseToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService expired = new JwtService(SECRET, -1, -1);
        String token = expired.generateToken(user());

        assertThatThrownBy(() -> jwtService.parseToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void acceptsShortSecretByDerivingA256BitKey() {
        JwtService shortSecret = new JwtService("corto", 60, 10080);

        String token = shortSecret.generateToken(user());

        assertThat(shortSecret.parseToken(token).id()).isEqualTo(42L);
    }
}
