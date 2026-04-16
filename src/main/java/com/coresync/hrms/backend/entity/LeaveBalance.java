package com.coresync.hrms.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_balances",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_leave_balance_emp_type_year",
        columnNames = {"employee_id", "leave_type_id", "year"}
    ),
    indexes = {
        @Index(name = "idx_leave_bal_emp_year", columnList = "employee_id, year")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "year", nullable = false)
    private int year;

    /** Total allocated days (accruals + carry-forward + manual grants) */
    @Column(nullable = false)
    @Builder.Default
    private double allocated = 0.0;

    /** Days consumed via escrow deduction on leave application */
    @Column(nullable = false)
    @Builder.Default
    private double used = 0.0;

    /** Effective balance = allocated - used */
    @Column(nullable = false)
    @Builder.Default
    private double balance = 0.0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    // --- Convenience methods ---

    public void deduct(double days) {
        this.used += days;
        this.balance = this.allocated - this.used;
    }

    public void refund(double days) {
        this.used = Math.max(0, this.used - days);
        this.balance = this.allocated - this.used;
    }

    public void credit(double days) {
        this.allocated += days;
        this.balance = this.allocated - this.used;
    }
}
