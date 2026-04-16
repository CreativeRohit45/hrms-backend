// src/main/java/com/coresync/hrms/backend/repository/CompanyLocationRepository.java
package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.CompanyLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyLocationRepository extends JpaRepository<CompanyLocation, Integer> {

    Optional<CompanyLocation> findFirstByIsActiveTrueOrderByIdAsc();
}
