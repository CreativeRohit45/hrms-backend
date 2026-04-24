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
                
            String token = jwtTokenProvider.generateToken(employee.getEmployeeCode(), employee.getRole().name());
            
            log.info("[Auth] Login successful | Employee: {} | Role: {}", employee.getEmployeeCode(), employee.getRole());
            
            return ResponseEntity.ok(LoginResponse.builder()
                .accessToken(token)
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
}