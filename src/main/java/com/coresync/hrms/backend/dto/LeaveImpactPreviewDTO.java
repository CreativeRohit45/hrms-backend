package com.coresync.hrms.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveImpactPreviewDTO {
    private Integer leaveRequestId;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate worstCaseDate;
    private Integer shiftId;
    private String shiftName;
    private Integer scheduledCount;
    private Double alreadyApprovedOffCount;
    private Double projectedAvailableCount;
    private Integer minimumHeadcount;
    private boolean configured;
    private String severity;
    private String message;
}
