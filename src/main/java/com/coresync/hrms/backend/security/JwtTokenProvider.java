// src/main/java/com/coresync/hrms/backend/security/JwtTokenProvider.java
package com.coresync.hrms.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    /** Refresh tokens live for 7 days */
    private static final long REFRESH_EXPIRATION_MS = 7L * 24 * 60 * 60 * 1000;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateToken(String employeeCode, String role) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
            .subject(employeeCode)
            .claim("role", role)
            .claim("type", "access")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(getSigningKey(), Jwts.SIG.HS512)
            .compact();
    }

    /**
     * Generate a long-lived refresh token.
     * Contains the same identity claims but with a "type":"refresh" marker
     * and a 7-day expiry.
     */
    public String generateRefreshToken(String employeeCode, String role) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + REFRESH_EXPIRATION_MS);

        return Jwts.builder()
            .subject(employeeCode)
            .claim("role", role)
            .claim("type", "refresh")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(getSigningKey(), Jwts.SIG.HS512)
            .compact();
    }

    public String getEmployeeCode(String token) {
        return parseClaims(token).getSubject();
    }

    public String getRole(String token) {
        return (String) parseClaims(token).get("role");
    }

    public String getTokenType(String token) {
        Object type = parseClaims(token).get("type");
        return type != null ? type.toString() : "access";
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Validate that the token is a valid refresh token (not an access token).
     */
    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return "refresh".equals(claims.get("type"));
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid refresh JWT: {}", ex.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}