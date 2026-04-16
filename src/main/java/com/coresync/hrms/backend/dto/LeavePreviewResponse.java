package com.coresync.hrms.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class LeavePreviewResponse {
    private double appliedDays;
    private String leaveTypeName;
    private String leaveTypeCode;
    private double currentBalance;
    private double balanceAfterDeduction;
    private boolean requiresAttachment;
    private boolean isPaid;
    private List<String> warnings;
}
