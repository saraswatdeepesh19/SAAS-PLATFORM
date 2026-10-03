package com.saas.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Validated credentials accepted by the authentication endpoint. */
public record LoginRequest(
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank String password) {
}