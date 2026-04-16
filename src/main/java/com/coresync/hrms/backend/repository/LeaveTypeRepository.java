package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Integer> {

    Optional<LeaveType> findByCode(String code);

    List<LeaveType> findByIsActiveTrue();

    boolean existsByCode(String code);
}
