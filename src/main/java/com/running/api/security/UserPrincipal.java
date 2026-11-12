package com.running.api.security;

import java.util.Set;

/**
 * Identidad autenticada extraída del JWT. Se guarda como principal en el
 * {@link org.springframework.security.core.context.SecurityContextHolder}.
 * <p>
 * Los tokens de acceso normales y las claves de API no llevan scopes ni
 * audiencia: se consideran acceso completo del usuario. Los tokens efímeros
 * usados para generar recomendaciones diarias llevan {@code aud=mcp}, un
 * {@code requestId} y los scopes {@code training:read}/{@code training:write}.
 */
public record UserPrincipal(Long id, String email, String role,
                            Set<String> scopes, String audience, String requestId) {

    public UserPrincipal {
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    /** Constructor compatible con tokens y claves de API sin scopes. */
    public UserPrincipal(Long id, String email, String role) {
        this(id, email, role, Set.of(), null, null);
    }

    /**
     * Indica si el principal puede ejecutar una acción. Un principal sin scopes
     * (JWT de acceso o clave de API) conserva el acceso completo histórico.
     */
    public boolean hasScope(String scope) {
        return scopes.isEmpty() || scopes.contains(scope);
    }

    /** Indica si el token está restringido mediante scopes. */
    public boolean isScoped() {
        return !scopes.isEmpty();
    }

    /** Tokens con {@code aud=mcp} solo pueden usarse contra {@code /mcp}. */
    public boolean isMcpOnly() {
        return audience != null && !audience.isBlank()
                && audience.toLowerCase().contains("mcp");
    }
}
