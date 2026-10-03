package com.saas.auth.exception;

/** Signals a tenant or account uniqueness conflict for translation to HTTP 409. */
public class DuplicateResourceException extends RuntimeException {
    /** Retains the conflict detail for service and API error handling. */
    public DuplicateResourceException(String message) {
        super(message);
    }
}