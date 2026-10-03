package com.saas.auth.entity;

import com.saas.common.enums.PlanType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class TenantEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 20)
    private PlanType planType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TenantEntity() {
    }

    /** Initializes a new persisted tenant and marks it active by default. */
    public TenantEntity(UUID id, String name, PlanType planType, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.planType = planType;
        this.status = "ACTIVE";
        this.createdAt = createdAt;
    }

    /** Returns the stable identifier used to scope this tenant's data. */
    public UUID getId() {
        return id;
    }

    /** Returns the display name persisted for this tenant. */
    public String getName() {
        return name;
    }

    /** Returns the plan used by downstream billing behavior. */
    public PlanType getPlanType() {
        return planType;
    }

    /** Returns the tenant lifecycle status for account-level checks. */
    public String getStatus() {
        return status;
    }

    /** Returns the creation timestamp retained for auditing and reporting. */
    public Instant getCreatedAt() {
        return createdAt;
    }
}