package com.saas.auth.dto;

import com.saas.common.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Validated input for creating a tenant member without exposing persistence fields. */
public record CreateUserRequest(
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull Role role) {
}