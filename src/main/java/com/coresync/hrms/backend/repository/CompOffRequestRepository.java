package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.CompOffRequest;
import com.coresync.hrms.backend.enums.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CompOffRequestRepository extends JpaRepository<CompOffRequest, Integer> {
    List<CompOffRequest> findByEmployeeIdOrderByCreatedAtDesc(Integer employeeId);
    List<CompOffRequest> findByStatus(LeaveStatus status);
    Optional<CompOffRequest> findByAttendanceLogId(Long attendanceLogId);
}
