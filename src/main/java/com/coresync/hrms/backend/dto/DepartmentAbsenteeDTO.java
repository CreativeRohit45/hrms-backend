package com.coresync.hrms.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DepartmentAbsenteeDTO {
    private Integer employeeId;
    private String fullName;
    private String employeeCode;
    private String departmentName;
    private String initials;
    private String status; // ABSENT or ON_LEAVE
    private String leaveTypeCode; // null if ABSENT
}
