package com.coresync.hrms.backend.entity;

import com.coresync.hrms.backend.enums.LeaveTransactionType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_balance_audit", indexes = {
    @Index(name = "idx_audit_emp_year",      columnList = "employee_id, year"),
    @Index(name = "idx_audit_emp_type_year", columnList = "employee_id, leave_type_id, year"),
    @Index(name = "idx_audit_created",       columnList = "created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveBalanceAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "year", nullable = false)
    private int year;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private LeaveTransactionType transactionType;

    /** Positive for credits (+1.5), negative for debits (-2.0) */
    @Column(nullable = false)
    private double amount;

    /** Snapshot of the balance AFTER this transaction */
    @Column(name = "balance_after", nullable = false)
    private double balanceAfter;

    @Column(nullable = false, length = 500)
    private String reason;

    /** Nullable FK to the LeaveRequest that triggered this transaction */
    @Column(name = "reference_leave_id")
    private Integer referenceLeaveId;

    /** User who triggered this: system (null) or HR admin ID */
    @Column(name = "performed_by_user_id")
    private Integer performedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}
