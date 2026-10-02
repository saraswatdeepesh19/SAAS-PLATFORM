package com.saas.auth.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresInSec) {
}