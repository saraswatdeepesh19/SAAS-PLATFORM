package com.saas.auth.repository;

import com.saas.auth.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    /** Looks up the account used for email-based authentication. */
    Optional<UserAccount> findByEmail(String email);

    /** Checks email uniqueness before account creation to return a domain-level conflict. */
    boolean existsByEmail(String email);
}