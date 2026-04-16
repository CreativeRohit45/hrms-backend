// src/main/java/com/coresync/hrms/backend/dto/PayrollResult.java
package com.coresync.hrms.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class PayrollResult {
    private Integer employeeId;
    private int payrollMonth;
    private int payrollYear;

    private int totalPayableMinutes;
    private int totalOvertimeMinutes;
    private int totalGatepassDeductedMinutes;

    private BigDecimal hourlyRateSnapshot;
    private BigDecimal overtimeRateSnapshot;
    private BigDecimal regularPay;
    private BigDecimal overtimePay;
    private BigDecimal grossPay;
    private BigDecimal deductionPf;
    private BigDecimal allowanceHra;
    private BigDecimal deductionLwp;
    private BigDecimal netPay;

    private int presentDays;
    private int absentDays;
    private int lateDays;
    private int leaveDays;
}