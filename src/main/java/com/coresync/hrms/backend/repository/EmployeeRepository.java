// src/main/java/com/coresync/hrms/backend/repository/EmployeeRepository.java
package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.Employee;
import com.coresync.hrms.backend.enums.EmployeeRole;
import com.coresync.hrms.backend.enums.EmployeeStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Integer> {
    
    boolean existsByRole(EmployeeRole role);
    
    Optional<Employee> findByEmployeeCode(String employeeCode);
    
    Optional<Employee> findByEmail(String email);

    @Query("SELECT MAX(e.employeeCode) FROM Employee e WHERE e.employeeCode LIKE CONCAT(:prefix, '%')")
    String findMaxEmployeeCode(@Param("prefix") String prefix);
    
    List<Employee> findByStatus(EmployeeStatus status);

    Page<Employee> findByDepartmentId(Integer departmentId, Pageable pageable);
    
    @Query("SELECT e.fullName FROM Employee e WHERE e.department.id = :deptId AND e.role = 'DEPARTMENT_MANAGER'")
    java.util.Optional<String> findManagerNameByDepartmentId(@Param("deptId") Integer deptId);
}