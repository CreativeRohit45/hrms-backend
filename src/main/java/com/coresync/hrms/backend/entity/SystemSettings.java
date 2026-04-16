package com.coresync.hrms.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 255)
    private String settingValue;

    @Column(name = "description")
    private String description;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
