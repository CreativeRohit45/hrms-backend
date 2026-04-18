package com.coresync.hrms.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Response DTO for bulk payroll operations.
 * Reports partial success/failure so the admin knows exactly
 * which employees failed and why.
 */
@Data
@Builder
public class BulkPayrollResponse {
    private int totalEmployees;
    private int successCount;
    private int failureCount;
    private List<FailureDetail> failures;

    @Data
    @Builder
    public static class FailureDetail {
        private String employeeCode;
        private String fullName;
        private String errorMessage;
    }
}
