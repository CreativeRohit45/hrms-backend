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
}
