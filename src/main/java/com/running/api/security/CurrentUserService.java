package com.running.api.security;

import com.running.api.exception.ForbiddenOperationException;
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

    /**
     * Exige un scope concreto cuando el token está restringido. Las credenciales
     * históricas (JWT de acceso y claves de API) no llevan scopes y conservan el
     * acceso completo del usuario.
     */
    public void requireScope(String scope) {
        UserPrincipal principal = requirePrincipal();
        if (!principal.hasScope(scope)) {
            throw new ForbiddenOperationException(
                    "El token no tiene el scope requerido: " + scope);
        }
    }
}
