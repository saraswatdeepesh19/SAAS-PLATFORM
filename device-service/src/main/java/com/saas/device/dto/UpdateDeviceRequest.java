package com.saas.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Validated mutable device fields; identity, tenant, and lifecycle state remain service-controlled. */
public record UpdateDeviceRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "BROWSER|MOBILE|TABLET") String type,
        @Size(max = 50) String os) {
}