package com.jobscheduler.security;

import com.jobscheduler.entity.User;
import com.jobscheduler.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads user from MySQL by username for Spring Security authentication.
 *
 * Spring Security calls loadUserByUsername() during:
 *   1. Login — to verify credentials
 *   2. JWT validation — to load user details for authorization
 *
 * We map our UserRole enum to Spring's GrantedAuthority with "ROLE_" prefix:
 *   ADMIN → ROLE_ADMIN
 *   USER  → ROLE_USER
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("[AUTH] User not found: {}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });

        log.debug("[AUTH] Loaded user: {} role: {}", username, user.getRole());

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())                          // already BCrypt hashed
                .authorities(List.of(new SimpleGrantedAuthority(
                        "ROLE_" + user.getRole().name())))             // ROLE_ADMIN or ROLE_USER
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!user.isEnabled())
                .build();
    }
}
