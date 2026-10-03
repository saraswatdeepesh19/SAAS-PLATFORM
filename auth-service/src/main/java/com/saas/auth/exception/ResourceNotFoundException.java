package com.saas.auth.exception;

/** Signals that a requested tenant-owned resource is unavailable. */
public class ResourceNotFoundException extends RuntimeException {
    /** Retains the lookup context for consistent HTTP 404 handling. */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}