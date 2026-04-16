package com.coresync.hrms.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_types", indexes = {
    @Index(name = "idx_leave_type_code", columnList = "code", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(name = "is_paid", nullable = false)
    @Builder.Default
    private boolean isPaid = true;

    // --- Attachment Rules ---

    @Column(name = "requires_attachment", nullable = false)
    @Builder.Default
    private boolean requiresAttachment = false;

    @Column(name = "attachment_threshold_days", nullable = false)
    @Builder.Default
    private int attachmentThresholdDays = 0;

    // --- Advanced Guards ---

    /** Comma-separated: "MALE", "FEMALE", or null for all genders */
    @Column(name = "allowed_genders", length = 50)
    private String allowedGenders;

    /** Maximum continuous days per single request, null = unlimited */
    @Column(name = "max_days_per_request")
    private Integer maxDaysPerRequest;

    /** If true, employee must have completed probation period to apply */
    @Column(name = "requires_probation_completion", nullable = false)
    @Builder.Default
    private boolean requiresProbationCompletion = false;

    /** If true, balance can go negative (borrowing against future accruals) */
    @Column(name = "allow_negative_balance", nullable = false)
    @Builder.Default
    private boolean allowNegativeBalance = false;

    // --- Accrual & Carry-Forward ---

    @Column(name = "default_annual_quota", nullable = false)
    @Builder.Default
    private double defaultAnnualQuota = 12.0;

    /** Monthly accrual credit; 0 = lump sum at year start (or manual grant) */
    @Column(name = "monthly_accrual_rate", nullable = false)
    @Builder.Default
    private double monthlyAccrualRate = 1.0;

    @Column(name = "is_carry_forward_allowed", nullable = false)
    @Builder.Default
    private boolean isCarryForwardAllowed = false;

    @Column(name = "max_carry_forward_days", nullable = false)
    @Builder.Default
    private int maxCarryForwardDays = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
