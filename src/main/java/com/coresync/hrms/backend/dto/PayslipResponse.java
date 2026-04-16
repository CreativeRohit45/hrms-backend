// src/main/java/com/coresync/hrms/backend/dto/PayslipResponse.java
package com.coresync.hrms.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class PayslipResponse {
    private Integer recordId;
    private String employeeCode;
    private String fullName;
    private String departmentName;
    private String designation;
    private String period;
    
    private int presentDays;
    private int absentDays;
    private int leaveDays;
    
    private BigDecimal grossPay;
    private BigDecimal deductionPf;
    private BigDecimal deductionEsi;
    private BigDecimal deductionTds;
    private BigDecimal totalDeductions;
    private BigDecimal netPay;
    
    private String status;
}