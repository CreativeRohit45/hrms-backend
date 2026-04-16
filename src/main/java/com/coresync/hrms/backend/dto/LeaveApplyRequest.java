package com.coresync.hrms.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class LeaveApplyRequest {

    @NotNull(message = "Leave type ID is required")
    private Integer leaveTypeId;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @Size(max = 500)
    private String reason;

    private boolean halfDay;

    /** FIRST_HALF or SECOND_HALF — required when halfDay is true */
    private String halfDaySession;

    @Size(max = 500)
    private String attachmentUrl;
}