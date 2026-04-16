package com.coresync.hrms.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "holidays")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Holiday {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(length = 255)
    private String description;

    /** If null, holiday applies globally; if set, only for this location */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private CompanyLocation location;
}
