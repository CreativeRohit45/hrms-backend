package com.coresync.hrms.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class TeamAvailabilityDTO {
    private String employeeCode;
    private String fullName;
    private String designation;
    private String leaveTypeName;
    private String leaveTypeCode;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean halfDay;
    private String halfDaySession;
}
