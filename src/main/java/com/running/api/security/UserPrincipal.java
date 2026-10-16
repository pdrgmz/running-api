package com.running.api.security;

/**
 * Identidad autenticada extraída del JWT. Se guarda como principal en el
 * {@link org.springframework.security.core.context.SecurityContextHolder}.
 */
public record UserPrincipal(Long id, String email, String role) {
}
