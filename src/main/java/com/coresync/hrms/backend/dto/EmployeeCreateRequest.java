// src/main/java/com/coresync/hrms/backend/dto/EmployeeCreateRequest.java
package com.coresync.hrms.backend.dto;

import com.coresync.hrms.backend.enums.EmployeeRole;
import com.coresync.hrms.backend.enums.PaymentType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class EmployeeCreateRequest {
    @NotBlank(message = "Full name is required")
    @Size(max = 200)
    private String fullName;

    @Size(max = 15, message = "Phone number too long")
    private String phone;

    @Email(message = "Invalid email format")
    @Size(max = 150)
    private String email;

    private LocalDate dateOfBirth;

    @NotNull(message = "Date of joining is required")
    private LocalDate dateOfJoining;

    @NotNull(message = "Department ID is required")
    private Integer departmentId;

    @NotBlank(message = "Designation is required")
    @Size(max = 100)
    private String designation;

    @NotNull(message = "Shift ID is required")
    private Integer shiftId;

    @NotNull(message = "Location ID is required")
    private Integer locationId;

    @NotNull(message = "Payment type is required")
    private PaymentType paymentType;

    @NotNull(message = "Hourly rate is required")
    @DecimalMin(value = "0.01", message = "Hourly rate must be positive")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal hourlyRate;

    @DecimalMin(value = "1.00", message = "Overtime multiplier must be at least 1.0")
    @Digits(integer = 2, fraction = 2)
    private BigDecimal overtimeRateMultiplier;

    @NotNull(message = "Role is required")
    private EmployeeRole role;

    private BigDecimal baseSalary;
    private BigDecimal hraPercentage;
    private BigDecimal pfPercentage;

    private List<InitialLeaveBalanceDTO> initialBalances;
}