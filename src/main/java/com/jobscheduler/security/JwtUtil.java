package com.jobscheduler.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT utility — handles token generation, validation, and claim extraction.
 *
 * HOW JWT WORKS:
 * ─────────────
 * A JWT has 3 parts separated by dots: header.payload.signature
 *
 * Header:    {"alg":"HS256","typ":"JWT"}         → base64 encoded
 * Payload:   {"sub":"admin","role":"ADMIN",...}  → base64 encoded
 * Signature: HMACSHA256(header + "." + payload, secret)
 *
 * The signature is what makes it tamper-proof. If someone changes the payload,
 * the signature won't match anymore. The server rejects it.
 *
 * Why JWT over sessions?
 * Sessions require server-side storage — every server needs to know about every
 * active session. In a distributed system with multiple instances, you'd need
 * shared session storage (Redis). JWT is stateless — the token itself contains
 * all the info, any instance can validate it without calling a database.
 *
 * Algorithm: HS256 (HMAC + SHA-256)
 * The secret key must be at least 256 bits (32 bytes) for HS256.
 * We derive the key from the configured secret using Keys.hmacShaKeyFor().
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    // ─────────────────────────────────────────────────────────────────────
    // Token Generation
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Generate a JWT token for an authenticated user.
     * Claims included: username (subject), role, issued-at, expiration.
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        // Store role in token so we don't need a DB lookup on every request
        extraClaims.put("role", userDetails.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("USER"));
        return buildToken(extraClaims, userDetails);
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())       // "sub" claim = username
                .issuedAt(new Date())                     // "iat" claim
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs)) // "exp"
                .signWith(getSigningKey())                // sign with HS256
                .compact();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Token Validation
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Validate token: signature valid + not expired + username matches.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException e) {
            log.warn("[JWT] Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Claim Extraction
    // ─────────────────────────────────────────────────────────────────────

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Signing Key
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Derive a secure HMAC-SHA256 key from the configured base64-encoded secret.
     * The secret in application.yml is hex-encoded. We decode it to bytes first.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public long getExpirationMs() {
        return jwtExpirationMs;
    }
}
