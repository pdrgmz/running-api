package com.running.api.exception;

/** El email ya está registrado (HTTP 409). */
public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String email) {
        super("Ya existe un usuario registrado con el email: " + email);
    }
}
