package com.running.api.exception;

/**
 * Operación autenticada pero no autorizada por los scopes del token.
 */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
