package com.saas.auth.exception;

import java.time.Instant;

/** Immutable API error payload shared by authentication exception handlers. */
public record ErrorResponse(Instant timestamp, int status, String code, String message, String path) {
}