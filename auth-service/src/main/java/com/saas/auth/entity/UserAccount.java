package com.saas.auth.entity;

import com.saas.common.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private TenantEntity tenant;

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserAccount() {
    }

    /** Creates an enabled account associated with one tenant and an already-encoded password. */
    public UserAccount(UUID id, TenantEntity tenant, String email, String passwordHash, Role role, Instant createdAt) {
        this.id = id;
        this.tenant = tenant;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.createdAt = createdAt;
    }

    /** Returns the account identifier used as the JWT subject. */
    public UUID getId() {
        return id;
    }

    /** Returns the owning tenant for authorization and tenant-scoped queries. */
    public TenantEntity getTenant() {
        return tenant;
    }

    /** Returns the normalized login email address. */
    public String getEmail() {
        return email;
    }

    /** Returns the stored hash so authentication can verify a password without storing plaintext. */
    public String getPasswordHash() {
        return passwordHash;
    }

    /** Returns the role copied into tokens for downstream authorization. */
    public Role getRole() {
        return role;
    }

    /** Indicates whether login is allowed for this account. */
    public boolean isEnabled() {
        return enabled;
    }
}