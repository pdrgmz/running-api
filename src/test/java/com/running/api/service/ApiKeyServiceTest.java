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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private UserRepository userRepository;

    private ApiKeyService apiKeyService;

    private final User user = User.builder()
            .id(1L)
            .email("runner@example.com")
            .role("USER")
            .build();

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(apiKeyRepository, userRepository);
    }

    // ------------------------------------------------------------------ create

    @Test
    void createGeneratesRandomKeyStoredOnlyAsHashAndReturnsItOnce() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        AtomicReference<ApiKey> saved = captureSavedKey();

        ApiKeyCreatedResponseDto response = apiKeyService.create(1L,
                CreateApiKeyRequestDto.builder().name("n8n").build());

        ApiKey entity = saved.get();
        assertThat(response.getKey()).startsWith("rk_");
        assertThat(response.getKeyPrefix()).isEqualTo(response.getKey().substring(0, 11));
        assertThat(entity.getUserId()).isEqualTo(1L);
        assertThat(entity.getName()).isEqualTo("n8n");
        assertThat(entity.getKeyHash()).hasSize(64).isNotEqualTo(response.getKey());
        assertThat(entity.getExpiresAt()).isNull();

        // La clave devuelta autentica contra el hash almacenado
        when(apiKeyRepository.findByKeyHash(entity.getKeyHash())).thenReturn(Optional.of(entity));
        assertThat(apiKeyService.authenticate(response.getKey()))
                .contains(new UserPrincipal(1L, "runner@example.com", "USER"));
    }

    @Test
    void createGeneratesDifferentKeysOnEachCall() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String first = apiKeyService.create(1L, CreateApiKeyRequestDto.builder().name("a").build()).getKey();
        String second = apiKeyService.create(1L, CreateApiKeyRequestDto.builder().name("b").build()).getKey();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void createAppliesExpirationWhenRequested() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        AtomicReference<ApiKey> saved = captureSavedKey();

        apiKeyService.create(1L, CreateApiKeyRequestDto.builder().name("n8n").expiresInDays(30).build());

        assertThat(saved.get().getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
    }

    @Test
    void createTrimsName() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        AtomicReference<ApiKey> saved = captureSavedKey();

        ApiKeyCreatedResponseDto response = apiKeyService.create(1L,
                CreateApiKeyRequestDto.builder().name("  n8n  ").build());

        assertThat(response.getName()).isEqualTo("n8n");
        assertThat(saved.get().getName()).isEqualTo("n8n");
    }

    @Test
    void createThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.create(99L, CreateApiKeyRequestDto.builder().name("n8n").build()))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(apiKeyRepository);
    }

    // ------------------------------------------------------------ authenticate

    @Test
    void authenticateAcceptsValidKeyAndUpdatesLastUsed() {
        ApiKey stored = ApiKey.builder()
                .id(5L).userId(1L).name("n8n").keyHash("hash").keyPrefix("rk_abc12345")
                .build();
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(stored));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Optional<UserPrincipal> principal = apiKeyService.authenticate("rk_clave");

        assertThat(principal).contains(new UserPrincipal(1L, "runner@example.com", "USER"));
        assertThat(stored.getLastUsedAt()).isNotNull();
        verify(apiKeyRepository).save(stored);
    }

    @Test
    void authenticateRejectsUnknownKey() {
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.empty());

        assertThat(apiKeyService.authenticate("rk_desconocida")).isEmpty();
        verify(userRepository, never()).findById(any());
    }

    @Test
    void authenticateRejectsValuesWithoutKeyPrefixWithoutHittingRepository() {
        assertThat(apiKeyService.authenticate("eyJhbGciOiJIUzI1NiJ9.token.jwt")).isEmpty();
        assertThat(apiKeyService.authenticate(null)).isEmpty();
        verifyNoInteractions(apiKeyRepository, userRepository);
    }

    @Test
    void authenticateRejectsExpiredKey() {
        ApiKey stored = ApiKey.builder()
                .id(5L).userId(1L).keyHash("hash").expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(stored));

        assertThat(apiKeyService.authenticate("rk_caducada")).isEmpty();
        verify(userRepository, never()).findById(any());
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void authenticateRejectsKeyWhenUserNoLongerExists() {
        ApiKey stored = ApiKey.builder().id(5L).userId(77L).keyHash("hash").build();
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(stored));
        when(userRepository.findById(77L)).thenReturn(Optional.empty());

        assertThat(apiKeyService.authenticate("rk_huerfana")).isEmpty();
    }

    @Test
    void authenticateDoesNotRewriteRecentLastUsed() {
        ApiKey stored = ApiKey.builder()
                .id(5L).userId(1L).keyHash("hash").lastUsedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(stored));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(apiKeyService.authenticate("rk_reciente")).isPresent();
        verify(apiKeyRepository, never()).save(any());
    }

    // -------------------------------------------------------------------- list

    @Test
    void listReturnsOwnKeys() {
        ApiKey stored = ApiKey.builder()
                .id(5L).userId(1L).name("n8n").keyPrefix("rk_abc12345")
                .createdAt(LocalDateTime.now().minusDays(1)).lastUsedAt(LocalDateTime.now())
                .build();
        when(apiKeyRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(stored));

        List<ApiKeyResponseDto> keys = apiKeyService.list(1L);

        assertThat(keys).hasSize(1);
        assertThat(keys.get(0).getId()).isEqualTo(5L);
        assertThat(keys.get(0).getName()).isEqualTo("n8n");
        assertThat(keys.get(0).getKeyPrefix()).isEqualTo("rk_abc12345");
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deleteRemovesOwnKey() {
        ApiKey stored = ApiKey.builder().id(5L).userId(1L).build();
        when(apiKeyRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(stored));

        apiKeyService.delete(1L, 5L);

        verify(apiKeyRepository).delete(stored);
    }

    @Test
    void deleteThrowsWhenKeyIsNotOwnedByUser() {
        when(apiKeyRepository.findByIdAndUserId(5L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.delete(2L, 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private AtomicReference<ApiKey> captureSavedKey() {
        AtomicReference<ApiKey> saved = new AtomicReference<>();
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        return saved;
    }
}
