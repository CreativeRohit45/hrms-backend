// src/main/java/com/coresync/hrms/backend/dto/LoginResponse.java
package com.coresync.hrms.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private String tokenType;
    private long expiresInMs;
    private String employeeCode;
    private String fullName;
    private String role;
}