package com.running.api.security;

import com.running.api.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private FilterChain filterChain;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesRequestWithValidApiKey() throws Exception {
        when(apiKeyService.authenticate("rk_test"))
                .thenReturn(Optional.of(new UserPrincipal(1L, "runner@example.com", "USER")));
        request.addHeader(ApiKeyAuthenticationFilter.API_KEY_HEADER, "rk_test");

        new ApiKeyAuthenticationFilter(apiKeyService).doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new UserPrincipal(1L, "runner@example.com", "USER"));
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void ignoresRequestWithoutApiKeyHeader() throws Exception {
        new ApiKeyAuthenticationFilter(apiKeyService).doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(apiKeyService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void ignoresBlankApiKeyHeader() throws Exception {
        request.addHeader(ApiKeyAuthenticationFilter.API_KEY_HEADER, "   ");

        new ApiKeyAuthenticationFilter(apiKeyService).doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(apiKeyService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void ignoresInvalidApiKey() throws Exception {
        when(apiKeyService.authenticate("rk_invalida")).thenReturn(Optional.empty());
        request.addHeader(ApiKeyAuthenticationFilter.API_KEY_HEADER, "rk_invalida");

        new ApiKeyAuthenticationFilter(apiKeyService).doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doesNotOverrideAuthenticationSetByJwtFilter() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("principal-jwt", null, List.of()));
        request.addHeader(ApiKeyAuthenticationFilter.API_KEY_HEADER, "rk_test");

        new ApiKeyAuthenticationFilter(apiKeyService).doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("principal-jwt");
        verifyNoInteractions(apiKeyService);
        verify(filterChain).doFilter(request, response);
    }
}
