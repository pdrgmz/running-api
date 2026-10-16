package com.running.api.service;

import com.running.api.dto.AuthResponseDto;
import com.running.api.dto.LoginRequestDto;
import com.running.api.dto.RegisterRequestDto;
import com.running.api.dto.UserResponseDto;
import com.running.api.exception.DuplicateEmailException;
import com.running.api.exception.InvalidCredentialsException;
import com.running.api.model.User;
import com.running.api.repository.UserRepository;
import com.running.api.security.JwtService;
import com.running.api.security.UserPrincipal;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponseDto register(RegisterRequestDto request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException(email);
        }
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .build();
        return buildResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthResponseDto login(LoginRequestDto request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new InvalidCredentialsException("Email o contraseña incorrectos"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Email o contraseña incorrectos");
        }
        return buildResponse(user);
    }

    /**
     * Emite un nuevo par de tokens (acceso + refresco) a partir de un refresh
     * token válido. Si el refresh token ha expirado o es inválido, lanza 401.
     */
    @Transactional(readOnly = true)
    public AuthResponseDto refresh(String refreshToken) {
        UserPrincipal principal;
        try {
            principal = jwtService.parseRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidCredentialsException("Refresh token inválido o expirado");
        }
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new InvalidCredentialsException("Refresh token inválido o expirado"));
        return buildResponse(user);
    }

    private AuthResponseDto buildResponse(User user) {
        return AuthResponseDto.builder()
                .token(jwtService.generateToken(user))
                .tokenType("Bearer")
                .expiresInSeconds(jwtService.getExpirationSeconds())
                .refreshToken(jwtService.generateRefreshToken(user))
                .refreshExpiresInSeconds(jwtService.getRefreshExpirationSeconds())
                .user(toDto(user))
                .build();
    }

    private UserResponseDto toDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
