// src/main/java/com/coresync/hrms/backend/repository/DepartmentRepository.java
package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Integer> { 
	
}