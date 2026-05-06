// src/main/java/com/coresync/hrms/backend/dto/LoginResponse.java
package com.coresync.hrms.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresInMs;
    private String employeeCode;
    private String fullName;
    private String role;

    // --- Error fields (populated only on 401) ---
    private String error;
    private String message;
}