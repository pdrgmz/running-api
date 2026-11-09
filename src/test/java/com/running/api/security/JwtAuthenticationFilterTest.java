package com.running.api.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesWithBearerHeaderOnRestApi() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/activities");
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.parseToken("access-token"))
                .thenReturn(new UserPrincipal(1L, "runner@example.com", "USER"));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(new UserPrincipal(1L, "runner@example.com", "USER"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void authenticatesWithAccessTokenQueryParamOnMcp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.setQueryString("access_token=plan-token");
        request.addParameter("access_token", "plan-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.parseToken("plan-token"))
                .thenReturn(new UserPrincipal(1L, "runner@example.com", "USER"));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void ignoresAccessTokenQueryParamOutsideMcp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/activities");
        request.setQueryString("access_token=plan-token");
        request.addParameter("access_token", "plan-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).parseToken("plan-token");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rejectsMcpOnlyTokenOnRestApi() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/training/plans");
        request.addHeader("Authorization", "Bearer plan-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.parseToken("plan-token")).thenReturn(new UserPrincipal(
                1L, "runner@example.com", "USER",
                Set.of(JwtService.SCOPE_TRAINING_READ), "mcp", "job-1"));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void leavesAnonymousWhenTokenIsInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/activities");
        request.addHeader("Authorization", "Bearer invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.parseToken("invalid")).thenThrow(new JwtException("inválido"));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
