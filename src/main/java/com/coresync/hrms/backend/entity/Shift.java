// src/main/java/com/coresync/hrms/backend/entity/Shift.java
package com.coresync.hrms.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "shifts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Shift {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "shift_name", nullable = false, unique = true, length = 100)
    private String shiftName;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "unpaid_break_minutes", nullable = false)
    @Builder.Default
    private short unpaidBreakMinutes = 0;

    @Column(name = "is_overnight", nullable = false)
    @Builder.Default
    private boolean isOvernight = false;

    @Column(name = "standard_hours", nullable = false, precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal standardHours = new BigDecimal("8.00");

    @Column(name = "grace_period_minutes", nullable = false)
    @Builder.Default
    private short gracePeriodMinutes = 0;

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