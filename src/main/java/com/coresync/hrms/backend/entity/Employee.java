// src/main/java/com/coresync/hrms/backend/entity/Employee.java
package com.coresync.hrms.backend.entity;

import com.coresync.hrms.backend.enums.EmployeeRole;
import com.coresync.hrms.backend.enums.EmployeeStatus;
import com.coresync.hrms.backend.enums.PaymentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employees", indexes = {
    @Index(name = "idx_employees_dept_status", columnList = "department_id, status"),
    @Index(name = "idx_employees_shift",       columnList = "shift_id"),
    @Index(name = "idx_employees_role",        columnList = "role")
})
@DynamicUpdate
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_code", nullable = false, unique = true, length = 20)
    private String employeeCode;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(length = 15)
    private String phone;

    @Column(unique = true, length = 150)
    private String email;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "date_of_joining", nullable = false)
    private LocalDate dateOfJoining;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false, length = 100)
    private String designation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private CompanyLocation location;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private PaymentType paymentType;

    @Column(name = "hourly_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "overtime_rate_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal overtimeRateMultiplier;

    @Column(name = "base_salary", precision = 12, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "hra_percentage", precision = 5, scale = 2)
    private BigDecimal hraPercentage;

    @Column(name = "pf_percentage", precision = 5, scale = 2)
    private BigDecimal pfPercentage;

    /** Gender for leave type eligibility checks (MALE, FEMALE, OTHER) */
    @Column(length = 10)
    private String gender;

    /** Probation end date; null means probation not applicable or already completed */
    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private EmployeeRole role;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}