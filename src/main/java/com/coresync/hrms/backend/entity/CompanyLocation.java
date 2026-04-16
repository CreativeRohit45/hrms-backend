// src/main/java/com/coresync/hrms/backend/entity/CompanyLocation.java
package com.coresync.hrms.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "company_locations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompanyLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "location_name", nullable = false, length = 150)
    private String locationName;

    @Column(length = 500)
    private String address;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "allowed_radius_meters", nullable = false)
    @Builder.Default
    private int allowedRadiusMeters = 3000;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "weekend_days", length = 100)
    @Builder.Default
    private String weekendDays = "Saturday,Sunday";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}