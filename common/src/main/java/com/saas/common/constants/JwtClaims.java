package com.saas.common.constants;

/** Centralizes JWT claim names so token issuers and resource servers use the same contract. */
public final class JwtClaims {
    public static final String TENANT_ID = "tenantId";
    public static final String ROLES = "roles";

    /** Prevents instantiation because this type only exposes constants. */
    private JwtClaims() {
    }
}