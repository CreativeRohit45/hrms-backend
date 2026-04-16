package com.coresync.hrms.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LeaveBalanceResponse {
    private Integer leaveTypeId;
    private String leaveTypeName;
    private String leaveTypeCode;
    private double allocated;
    private double used;
    private double balance;
    private int year;
}
