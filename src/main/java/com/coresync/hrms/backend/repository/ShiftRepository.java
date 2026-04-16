// src/main/java/com/coresync/hrms/backend/repository/ShiftRepository.java
package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRepository extends JpaRepository<Shift, Integer> { 
	
}