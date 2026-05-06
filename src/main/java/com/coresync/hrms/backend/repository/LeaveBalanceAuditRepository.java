package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.LeaveBalanceAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveBalanceAuditRepository extends JpaRepository<LeaveBalanceAudit, Long> {

    Page<LeaveBalanceAudit> findByEmployeeIdAndYearOrderByCreatedAtDesc(Integer empId, int year, Pageable pageable);

    Page<LeaveBalanceAudit> findByEmployeeIdAndLeaveTypeIdAndYearOrderByCreatedAtDesc(
        Integer empId, Integer typeId, int year, Pageable pageable
    );

    List<LeaveBalanceAudit> findByReferenceLeaveIdOrderByCreatedAtAsc(Integer leaveRequestId);

    List<LeaveBalanceAudit> findByEmployeeIdAndLeaveTypeIdOrderByCreatedAtAsc(Integer empId, Integer typeId);
}
