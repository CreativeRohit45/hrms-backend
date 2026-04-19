package com.coresync.hrms.backend.entity;

import com.coresync.hrms.backend.enums.AttendanceStatus;
import com.coresync.hrms.backend.enums.CorrectionStatus;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_logs", indexes = {
    @Index(name = "idx_attendance_emp_date",    columnList = "employee_id, work_date"),
    @Index(name = "idx_attendance_date_status", columnList = "work_date, attendance_status"),
    @Index(name = "idx_attendance_location",    columnList = "location_id")
})
@DynamicUpdate
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private CompanyLocation location;

    @Column(name = "punch_in_time", nullable = true)
    private LocalDateTime punchInTime;

    @Column(name = "punch_out_time")
    private LocalDateTime punchOutTime;

    @Column(name = "is_location_verified_in", nullable = false)
    private boolean isLocationVerifiedIn;

    @Column(name = "is_location_verified_out")
    private Boolean isLocationVerifiedOut;

    @Column(name = "calculated_payable_minutes")
    private Integer calculatedPayableMinutes;

    @Column(name = "is_overtime", nullable = false)
    @Builder.Default
    private boolean isOvertime = false;

    @Column(name = "overtime_minutes", nullable = false)
    @Builder.Default
    private int overtimeMinutes = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false, length = 40)
    private AttendanceStatus attendanceStatus;

    @Column(name = "is_manually_corrected", nullable = false)
    @Builder.Default
    private boolean isManuallyCorrected = false;

    @Column(name = "correction_reason", length = 500)
    private String correctionReason;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "correction_status", nullable = false, length = 20)
    @Builder.Default
    private CorrectionStatus correctionStatus = CorrectionStatus.NONE;

    @Column(name = "requested_punch_in_time")
    private LocalDateTime requestedPunchInTime;

    @Column(name = "requested_punch_out_time")
    private LocalDateTime requestedPunchOutTime;

    @Column(name = "is_overtime_approved")
    private Boolean isOvertimeApproved;

    @Column(name = "corrected_by_user_id")
    private Integer correctedByUserId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}