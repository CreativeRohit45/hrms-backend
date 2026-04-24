package com.coresync.hrms.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class BulkGrantRequest {
    @NotEmpty(message = "Employee IDs cannot be empty")
    private List<Integer> employeeIds;

    @NotNull(message = "Leave Type ID is required")
    private Integer leaveTypeId;

    @NotNull(message = "Amount is required")
    private Double amount;
    
    private String reason;
}
