package com.running.api.security;

import com.running.api.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lee la cabecera {@code X-Api-Key}, valida la clave de API y, si es correcta,
 * publica la identidad del usuario propietario en el contexto de seguridad.
 * <p>
 * Es una alternativa al token JWT para automatizaciones (n8n, scripts, cron):
 * no caduca a corto plazo ni requiere refresco. Si la petición ya está
 * autenticada (por ejemplo con un JWT válido), el filtro no hace nada; si la
 * clave falta o es inválida, la petición continúa como anónima y los endpoints
 * protegidos devuelven 401.
 * <p>
 * No se anota como {@code @Component} a propósito: se instancia dentro de la
 * cadena de Spring Security ({@link SecurityConfig}) para evitar que Spring Boot
 * lo registre además como filtro de servlet.
 */
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    /** Cabecera donde se envía la clave de API. */
    public static final String API_KEY_HEADER = "X-Api-Key";

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            apiKeyService.authenticate(apiKey.trim())
                    .ifPresent(principal -> {
                        var authentication = new UsernamePasswordAuthenticationToken(
                                principal, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }
        filterChain.doFilter(request, response);
    }
}
