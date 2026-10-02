package com.saas.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDeviceRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "BROWSER|MOBILE|TABLET") String type,
        @Size(max = 50) String os) {
}