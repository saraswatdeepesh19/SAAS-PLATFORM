package com.saas.auth.repository;

import com.saas.auth.entity.TenantEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {
    /** Checks tenant names without regard to case so equivalent names cannot be registered twice. */
    boolean existsByNameIgnoreCase(String name);
}