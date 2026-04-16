package com.coresync.hrms.backend.entity;

import com.coresync.hrms.backend.enums.LeaveStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "compoff_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompOffRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_log_id", nullable = false)
    private AttendanceLog attendanceLog;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveStatus status; // PENDING, APPROVED, REJECTED

    @Column(length = 500)
    private String reason;

    @Column(name = "action_by_user_id")
    private Integer actionByUserId;

    @Column(name = "action_at")
    private LocalDateTime actionAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}
