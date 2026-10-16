package com.running.api.exception;

/** Credenciales inválidas o ausencia de autenticación (HTTP 401). */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
