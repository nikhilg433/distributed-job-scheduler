package com.jobscheduler.entity;

import com.jobscheduler.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents an authenticated user of the Job Scheduler API.
 *
 * Two roles:
 *   ADMIN — full access: create, cancel, pause, resume, view all jobs
 *   USER  — read-only:   view job status, list jobs, get history, view stats
 *
 * Default users are seeded by DataInitializer on first startup:
 *   admin / admin123  (ADMIN role)
 *   user  / user123   (USER role)
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_username", columnList = "username", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt hashed password — never store plain text */
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
