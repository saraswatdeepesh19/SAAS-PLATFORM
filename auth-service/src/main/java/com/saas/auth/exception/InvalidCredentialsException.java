package com.saas.auth.exception;

/** Signals rejected credentials using a generic message that does not reveal which value was wrong. */
public class InvalidCredentialsException extends RuntimeException {
    /** Uses one generic response for unknown, disabled, or password-mismatched accounts. */
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}