// src/main/java/com/coresync/hrms/backend/entity/PayrollRecord.java
package com.coresync.hrms.backend.entity;

import com.coresync.hrms.backend.enums.PayrollStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payroll_records", indexes = {
    @Index(name = "idx_payroll_period_status", columnList = "payroll_year, payroll_month, status"),
    @Index(name = "idx_payroll_employee_period", columnList = "employee_id, payroll_year, payroll_month")
})
@DynamicUpdate
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayrollRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "payroll_month", nullable = false)
    private int payrollMonth;

    @Column(name = "payroll_year", nullable = false)
    private int payrollYear;

    @Column(name = "total_payable_minutes", nullable = false)
    private int totalPayableMinutes;

    @Column(name = "total_overtime_minutes", nullable = false)
    private int totalOvertimeMinutes;

    @Column(name = "hourly_rate_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyRateSnapshot;

    @Column(name = "overtime_rate_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal overtimeRateSnapshot;

    @Column(name = "gross_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossPay;

    @Column(name = "overtime_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal overtimePay;

    @Column(name = "deduction_pf", nullable = false, precision = 10, scale = 2)
    private BigDecimal deductionPf;

    @Column(name = "deduction_esi", nullable = false, precision = 10, scale = 2)
    private BigDecimal deductionEsi;

    @Column(name = "deduction_tds", nullable = false, precision = 10, scale = 2)
    private BigDecimal deductionTds;

    @Column(name = "deduction_advance", nullable = false, precision = 10, scale = 2)
    private BigDecimal deductionAdvance;

    @Column(name = "deduction_other", nullable = false, precision = 10, scale = 2)
    private BigDecimal deductionOther;

    @Column(name = "total_deductions", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDeductions;

    @Column(name = "net_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal netPay;

    @Column(name = "present_days", nullable = false)
    private int presentDays;

    @Column(name = "absent_days", nullable = false)
    private int absentDays;

    @Column(name = "late_days", nullable = false)
    private int lateDays;

    @Column(name = "leave_days", nullable = false)
    private int leaveDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayrollStatus status;

    @Column(name = "payslip_sent_whatsapp", nullable = false)
    private boolean payslipSentWhatsapp;

    @Column(name = "payslip_sent_email", nullable = false)
    private boolean payslipSentEmail;

    @Column(name = "payslip_generated_at")
    private LocalDateTime payslipGeneratedAt;

    @Column(name = "processed_by_user_id")
    private Integer processedByUserId;

    @Column(name = "approved_by_user_id")
    private Integer approvedByUserId;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}