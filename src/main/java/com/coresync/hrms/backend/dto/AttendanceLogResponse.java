// src/main/java/com/coresync/hrms/backend/dto/AttendanceLogResponse.java
package com.coresync.hrms.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.coresync.hrms.backend.enums.AttendanceStatus;
import com.coresync.hrms.backend.enums.CorrectionStatus;

@Data
@Builder
public class AttendanceLogResponse {
    private Long id;
    private Integer employeeId;
    private String employeeCode;
    private String fullName;
    private LocalDate workDate;
    private LocalDateTime punchInTime;
    private LocalDateTime punchOutTime;
    private boolean locationVerifiedIn;
    private Boolean locationVerifiedOut;
    private Integer calculatedPayableMinutes;
    private boolean overtime;
    private int overtimeMinutes;
    private AttendanceStatus attendanceStatus;
    private boolean manuallyCorrected;
    private String correctionReason;
    private CorrectionStatus correctionStatus;
    private LocalDateTime requestedPunchInTime;
    private LocalDateTime requestedPunchOutTime;
    private Boolean isOvertimeApproved;
    private LocalTime shiftStartTime;
    private LocalTime shiftEndTime;
}