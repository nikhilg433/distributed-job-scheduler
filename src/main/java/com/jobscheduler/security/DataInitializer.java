package com.jobscheduler.security;

import com.jobscheduler.entity.User;
import com.jobscheduler.enums.UserRole;
import com.jobscheduler.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds default users into MySQL on first startup.
 *
 * Runs once after Spring context is ready (CommandLineRunner).
 * Uses existsByUsername() check so it's safe to run on every restart
 * — it won't create duplicates.
 *
 * Default credentials:
 *   admin / admin123  → ADMIN role (full access)
 *   user  / user123   → USER role  (read-only)
 *
 * IMPORTANT: Change these credentials in production via environment variables.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        createUserIfNotExists("admin", "admin123", UserRole.ADMIN);
        createUserIfNotExists("user",  "user123",  UserRole.USER);
        log.info("[DATA-INIT] Default users ready — admin/admin123 (ADMIN), user/user123 (USER)");
    }

    private void createUserIfNotExists(String username, String rawPassword, UserRole role) {
        if (!userRepository.existsByUsername(username)) {
            User user = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(rawPassword)) // BCrypt hash
                    .role(role)
                    .enabled(true)
                    .build();
            userRepository.save(user);
            log.info("[DATA-INIT] Created user: {} role: {}", username, role);
        }
    }
}
