package com.coresync.hrms.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class LeaveResponse {
    private Integer id;
    private Integer employeeId;
    private String employeeCode;
    private String fullName;

    private Integer leaveTypeId;
    private String leaveTypeName;
    private String leaveTypeCode;

    private LocalDate startDate;
    private LocalDate endDate;
    private double appliedDays;

    private String reason;
    private String status;

    private boolean halfDay;
    private String halfDaySession;

    private String attachmentUrl;

    private Integer actionByUserId;
    private String actionByName;
    private String pendingApproverName;
    private LocalDateTime actionAt;
    private String rejectionReason;

    private LocalDateTime createdAt;
}