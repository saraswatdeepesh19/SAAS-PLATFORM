package com.saas.auth.repository;

import com.saas.auth.entity.TenantEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {
    boolean existsByNameIgnoreCase(String name);
}