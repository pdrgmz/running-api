package com.running.api.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lee el header {@code Authorization: Bearer <token>}, lo valida y, si es
 * correcto, publica la identidad en el contexto de seguridad de la petición.
 * Si el token falta o es inválido, la petición continúa como anónima y serán
 * los endpoints protegidos quienes devuelvan 401.
 * <p>
 * En {@code /mcp} también se acepta el token efímero de generación de planes
 * como query param {@code access_token}. Es necesario porque el nodo
 * <em>MCP Client Tool</em> de n8n solo admite credenciales estáticas y no puede
 * inyectar el JWT de cada ejecución en una cabecera. El token es de corta
 * duración, lleva {@code aud=mcp} y solo se acepta en este endpoint.
 * <p>
 * No se anota como {@code @Component} a propósito: se instancia dentro de la
 * cadena de Spring Security ({@link SecurityConfig}) para evitar que Spring Boot
 * lo registre además como filtro de servlet.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String QUERY_TOKEN_PARAM = "access_token";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserPrincipal principal = jwtService.parseToken(token);
                // Los tokens efímeros de generación de planes llevan aud=mcp y solo
                // deben poder usarse contra el servidor MCP, nunca contra la API REST.
                if (principal.isMcpOnly() && !isMcpRequest(request)) {
                    throw new JwtException("El token está restringido al servidor MCP");
                }
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        if (isMcpRequest(request)) {
            String queryToken = request.getParameter(QUERY_TOKEN_PARAM);
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken.trim();
            }
        }
        return null;
    }

    private static boolean isMcpRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path.equals("/mcp") || path.startsWith("/mcp/");
    }
}
