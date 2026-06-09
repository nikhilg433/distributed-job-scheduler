package com.jobscheduler.repository;

import com.jobscheduler.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for the users table.
 * Used by Spring Security UserDetailsService to load users during authentication.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find a user by username for authentication.
     * Called by UserDetailsServiceImpl during JWT validation.
     */
    Optional<User> findByUsername(String username);

    /** Check if a username already exists (used by DataInitializer). */
    boolean existsByUsername(String username);
}
