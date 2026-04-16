// src/main/java/com/coresync/hrms/backend/dto/GatepassResponse.java
package com.coresync.hrms.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatepassResponse {
    private Integer id;
    private Integer employeeId;
    private String employeeCode;
    private String fullName;
    private Long attendanceLogId;
    private LocalDate requestDate;
    private LocalDateTime requestedOutTime;
    private LocalDateTime requestedInTime;
    private LocalDateTime actualOutTime;
    private LocalDateTime actualInTime;
    private String gatepassType;
    private String status;
    private String reason;
    private Integer approvedById;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private String rejectionReason;
    private Boolean isEmergency;
    private LocalDateTime createdAt;
}