package com.coresync.hrms.backend.entity;

import com.coresync.hrms.backend.enums.LeaveStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_requests", indexes = {
    @Index(name = "idx_leave_emp_dates",  columnList = "employee_id, start_date, end_date"),
    @Index(name = "idx_leave_status",     columnList = "status"),
    @Index(name = "idx_leave_type",       columnList = "leave_type_id"),
    @Index(name = "idx_leave_action_by",  columnList = "action_by_user_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Calculated working days after excluding weekends & holidays */
    @Column(name = "applied_days", nullable = false)
    private double appliedDays;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveStatus status;

    /** ID of the admin/manager who approved/rejected/revoked */
    @Column(name = "action_by_user_id")
    private Integer actionByUserId;

    @Column(name = "action_at")
    private LocalDateTime actionAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @Column(name = "is_half_day", nullable = false)
    @Builder.Default
    private boolean isHalfDay = false;

    /** FIRST_HALF or SECOND_HALF */
    @Column(name = "half_day_session", length = 20)
    private String halfDaySession;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}