package com.coresync.hrms.backend.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveOverrideRequest {

    @NotNull(message = "Employee ID is required")
    private Integer employeeId;

    @NotNull(message = "Leave type ID is required")
    private Integer leaveTypeId;

    /** Can be positive (credit) or negative (deduction) */
    @NotNull(message = "Adjustment amount is required")
    private Double amount;

    @NotBlank(message = "Reason is required for override audits")
    @Size(max = 500)
    private String reason;
}
