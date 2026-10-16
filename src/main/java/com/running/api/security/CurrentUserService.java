package com.running.api.security;

import com.running.api.exception.InvalidCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Acceso a la identidad del usuario autenticado en el hilo actual.
 * Pensado para puntos de entrada sin parámetros de controlador (herramientas MCP).
 */
@Component
public class CurrentUserService {

    public UserPrincipal requirePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new InvalidCredentialsException("No hay un usuario autenticado en la petición");
        }
        return principal;
    }

    public Long requireUserId() {
        return requirePrincipal().id();
    }
}
