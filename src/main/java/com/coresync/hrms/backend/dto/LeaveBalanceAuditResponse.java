package com.coresync.hrms.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class LeaveBalanceAuditResponse {
    private Long id;
    private String leaveTypeName;
    private String leaveTypeCode;
    private String transactionType;
    private double amount;
    private double balanceAfter;
    private String reason;
    private Integer referenceLeaveId;
    private Integer performedByUserId;
    private String performedByName;
    private LocalDateTime createdAt;
}
