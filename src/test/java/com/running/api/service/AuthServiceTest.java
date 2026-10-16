package com.running.api.service;

import com.running.api.dto.AuthResponseDto;
import com.running.api.dto.LoginRequestDto;
import com.running.api.dto.RegisterRequestDto;
import com.running.api.exception.DuplicateEmailException;
import com.running.api.exception.InvalidCredentialsException;
import com.running.api.model.User;
import com.running.api.repository.UserRepository;
import com.running.api.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = new JwtService("test-secret-long-enough-for-hs256-0123456789abcdef", 60, 10080);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerHashesPasswordNormalizesEmailAndReturnsToken() {
        when(userRepository.existsByEmailIgnoreCase("runner@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        AuthResponseDto response = authService.register(new RegisterRequestDto("  Runner@Example.com ", "password123"));

        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getUser().getEmail()).isEqualTo("runner@example.com");
        verify(userRepository).save(argThat(user ->
                user.getEmail().equals("runner@example.com")
                        && !user.getPassword().equals("password123")
                        && passwordEncoder.matches("password123", user.getPassword())));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("runner@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequestDto("runner@example.com", "password123")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void loginSucceedsWithCorrectPassword() {
        User stored = User.builder()
                .id(1L)
                .email("runner@example.com")
                .password(passwordEncoder.encode("password123"))
                .role("USER")
                .build();
        when(userRepository.findByEmailIgnoreCase("runner@example.com")).thenReturn(Optional.of(stored));

        AuthResponseDto response = authService.login(new LoginRequestDto("runner@example.com", "password123"));

        assertThat(response.getToken()).isNotBlank();
        assertThat(jwtService.parseToken(response.getToken()).id()).isEqualTo(1L);
    }

    @Test
    void loginFailsWithWrongPassword() {
        User stored = User.builder()
                .id(1L)
                .email("runner@example.com")
                .password(passwordEncoder.encode("password123"))
                .role("USER")
                .build();
        when(userRepository.findByEmailIgnoreCase("runner@example.com")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.login(new LoginRequestDto("runner@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginFailsWhenUserDoesNotExist() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequestDto("nobody@example.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshIssuesNewTokensFromValidRefreshToken() {
        User stored = User.builder().id(1L).email("runner@example.com").role("USER").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(stored));
        String refreshToken = jwtService.generateRefreshToken(stored);

        AuthResponseDto response = authService.refresh(refreshToken);

        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(jwtService.parseToken(response.getToken()).id()).isEqualTo(1L);
    }

    @Test
    void refreshFailsWithAccessToken() {
        User stored = User.builder().id(1L).email("runner@example.com").role("USER").build();
        String accessToken = jwtService.generateToken(stored);

        assertThatThrownBy(() -> authService.refresh(accessToken))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshFailsWithGarbageToken() {
        assertThatThrownBy(() -> authService.refresh("no-es-un-token"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshFailsWhenUserNoLongerExists() {
        User stored = User.builder().id(99L).email("gone@example.com").role("USER").build();
        String refreshToken = jwtService.generateRefreshToken(stored);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
