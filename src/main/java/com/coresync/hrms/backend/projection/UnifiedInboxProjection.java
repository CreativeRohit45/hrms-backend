package com.coresync.hrms.backend.projection;

import java.time.LocalDateTime;

/**
 * Spring Data Projection for the Unified Inbox UNION ALL query.
 * Adheres to the Systems Architect's directive to avoid ConverterNotFoundException
 * by using an interface instead of a concrete class for native SQL mapping.
 */
public interface UnifiedInboxProjection {
    String getId();
    String getRequestType();
    Integer getEmployeeId();
    String getEmployeeName();
    String getEmployeeCode();
    String getDetails();
    String getStatus();
    LocalDateTime getCreatedAt();
    java.time.LocalDate getReferenceDate();
    java.time.LocalDate getReferenceEndDate();
    Integer getDepartmentId();
    String getDepartmentName();
    String getLeaveTypeName();
    Double getAppliedDays();
    @com.fasterxml.jackson.annotation.JsonIgnore
    Object getHalfDayRaw();

    default Boolean getHalfDay() {
        Object raw = getHalfDayRaw();
        if (raw == null) return false;
        if (raw instanceof Boolean) return (Boolean) raw;
        if (raw instanceof Number) return ((Number) raw).intValue() == 1;
        return "true".equalsIgnoreCase(raw.toString()) || "1".equals(raw.toString());
    }

    String getHalfDaySession();
    String getGatepassType();
    java.time.LocalDateTime getRequestedOutTime();
    java.time.LocalDateTime getRequestedInTime();
    java.time.LocalDateTime getActualOutTime();
    java.time.LocalDateTime getActualInTime();
    
    @com.fasterxml.jackson.annotation.JsonIgnore
    Object getEmergencyRaw();

    default Boolean getEmergency() {
        Object raw = getEmergencyRaw();
        if (raw == null) return false;
        if (raw instanceof Boolean) return (Boolean) raw;
        if (raw instanceof Number) return ((Number) raw).intValue() == 1;
        return "true".equalsIgnoreCase(raw.toString()) || "1".equals(raw.toString());
    }
    java.time.LocalDateTime getOriginalPunchInTime();
    java.time.LocalDateTime getOriginalPunchOutTime();
    java.time.LocalDateTime getRequestedPunchInTime();
    java.time.LocalDateTime getRequestedPunchOutTime();
    Integer getOvertimeMinutes();
    String getAttendanceStatus();
    String getRejectionReason();
}
