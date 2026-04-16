// src/main/java/com/coresync/hrms/backend/dto/LoginRequest.java
package com.coresync.hrms.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Employee code or email is required")
    private String identifier;
    
    @NotBlank(message = "Password is required")
    private String password;
}