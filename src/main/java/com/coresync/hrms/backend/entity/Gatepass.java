// src/main/java/com/coresync/hrms/backend/entity/Gatepass.java
package com.coresync.hrms.backend.entity;

import com.coresync.hrms.backend.enums.GatepassStatus;
import com.coresync.hrms.backend.enums.GatepassType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "gatepasses", indexes = {
    @Index(name = "idx_gatepass_emp_date",   columnList = "employee_id, request_date"),
    @Index(name = "idx_gatepass_status",     columnList = "status"),
    @Index(name = "idx_gatepass_attendance", columnList = "attendance_log_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Gatepass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_log_id")
    private AttendanceLog attendanceLog;

    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate;

    @Column(name = "requested_out_time", nullable = false)
    private LocalDateTime requestedOutTime;

    @Column(name = "requested_in_time", nullable = false)
    private LocalDateTime requestedInTime;

    @Column(name = "actual_out_time")
    private LocalDateTime actualOutTime;

    @Column(name = "actual_in_time")
    private LocalDateTime actualInTime;

    @Column(name = "expected_in_time", nullable = false)
    private LocalDateTime expectedInTime;

    @Column(name = "out_time", nullable = false)
    private LocalDateTime outTime;

    @Column(name = "emergency_notified_at")
    private LocalDateTime emergencyNotifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "gatepass_type", nullable = false, columnDefinition = "VARCHAR(20)")
    private GatepassType gatepassType;

    @Column(nullable = false, length = 500)
    private String reason;

    @Builder.Default
    @Column(name = "is_emergency", nullable = false)
    private Boolean isEmergency = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private GatepassStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Employee approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist 
    protected void onCreate() { 
        createdAt = LocalDateTime.now(); 
        updatedAt = LocalDateTime.now(); 
        if (requestDate == null) requestDate = LocalDate.now();
    }
    
    @PreUpdate 
    protected void onUpdate() { 
        updatedAt = LocalDateTime.now(); 
    }
}