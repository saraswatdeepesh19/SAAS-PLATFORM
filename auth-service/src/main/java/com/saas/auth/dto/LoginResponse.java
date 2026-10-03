package com.saas.auth.dto;

/** Immutable bearer-token response including its type and lifetime for API clients. */
public record LoginResponse(String accessToken, String tokenType, long expiresInSec) {
}