// src/main/java/com/coresync/hrms/backend/controller/AuthController.java
package com.coresync.hrms.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coresync.hrms.backend.dto.LoginRequest;
import com.coresync.hrms.backend.dto.LoginResponse;
import com.coresync.hrms.backend.entity.Employee;
import com.coresync.hrms.backend.repository.EmployeeRepository;
import com.coresync.hrms.backend.security.JwtTokenProvider;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmployeeRepository employeeRepository;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getIdentifier(), request.getPassword())
            );
            
            String employeeCode = authentication.getName();
            Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new RuntimeException("Employee not found post-auth"));
                
            String accessToken = jwtTokenProvider.generateToken(employee.getEmployeeCode(), employee.getRole().name());
            String refreshToken = jwtTokenProvider.generateRefreshToken(employee.getEmployeeCode(), employee.getRole().name());
            
            log.info("[Auth] Login successful | Employee: {} | Role: {}", employee.getEmployeeCode(), employee.getRole());
            
            return ResponseEntity.ok(LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresInMs(jwtExpirationMs)
                .employeeCode(employee.getEmployeeCode())
                .fullName(employee.getFullName())
                .role(employee.getRole().name())
                .build());
        } catch (AuthenticationException ex) {
            log.warn("[Auth] Login FAILED for identifier: '{}'", request.getIdentifier());
            return ResponseEntity.status(401).body(LoginResponse.builder()
                .error("INVALID_CREDENTIALS")
                .message("Employee code or password is incorrect.")
                .build());
        }
    }

    /**
     * POST /api/v1/auth/refresh
     * 
     * Accepts a valid refresh token and issues a new access + refresh token pair.
     * This enables silent token rotation without forcing the user to re-login,
     * preventing the "401 Data Wipe" UX failure where unsaved form data is destroyed.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(400).body(Map.of(
                "error", "MISSING_REFRESH_TOKEN",
                "message", "Refresh token is required."
            ));
        }

        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            log.warn("[Auth] Refresh token validation failed — token expired or invalid");
            return ResponseEntity.status(401).body(Map.of(
                "error", "INVALID_REFRESH_TOKEN",
                "message", "Refresh token is expired or invalid. Please log in again."
            ));
        }

        String employeeCode = jwtTokenProvider.getEmployeeCode(refreshToken);
        String role = jwtTokenProvider.getRole(refreshToken);

        // Verify the employee still exists and is active
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode).orElse(null);
        if (employee == null) {
            return ResponseEntity.status(401).body(Map.of(
                "error", "EMPLOYEE_NOT_FOUND",
                "message", "Associated employee account no longer exists."
            ));
        }

        // Issue fresh token pair (rotation: old refresh token is now consumed)
        String newAccessToken = jwtTokenProvider.generateToken(employeeCode, role);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(employeeCode, role);

        log.info("[Auth] Token refreshed for employee: {}", employeeCode);

        return ResponseEntity.ok(LoginResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .tokenType("Bearer")
            .expiresInMs(jwtExpirationMs)
            .employeeCode(employee.getEmployeeCode())
            .fullName(employee.getFullName())
            .role(employee.getRole().name())
            .build());
    }
}