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
public class LeaveGrantRequest {

    @NotNull(message = "Employee ID is required")
    private Integer employeeId;

    @NotNull(message = "Leave type ID is required")
    private Integer leaveTypeId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.5", message = "Minimum grant amount is 0.5 days")
    private Double amount;

    @NotBlank(message = "Reason is required for manual adjustments")
    @Size(max = 500)
    private String reason;
}
