// src/main/java/com/coresync/hrms/backend/dto/EmployeeResponse.java
package com.coresync.hrms.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class EmployeeResponse {
    private Integer id;
    private String employeeCode;
    private String fullName;
    private String phone;
    private String email;
    private String photoUrl;
    private LocalDate dateOfBirth;
    private LocalDate dateOfJoining;

    private Integer departmentId;
    private String departmentName;
    private String designation;
    private Integer shiftId;
    private String shiftName;
    private java.time.LocalTime shiftStartTime;
    private java.time.LocalTime shiftEndTime;
    private Integer locationId;
    private String locationName;

    private String paymentType;
    private BigDecimal hourlyRate;
    private BigDecimal overtimeRateMultiplier;
    private String status;
    private String role;

    private BigDecimal baseSalary;
    private BigDecimal hraPercentage;
    private BigDecimal pfPercentage;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}