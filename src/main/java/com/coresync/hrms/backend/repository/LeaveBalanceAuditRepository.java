package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.LeaveBalanceAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveBalanceAuditRepository extends JpaRepository<LeaveBalanceAudit, Long> {

    List<LeaveBalanceAudit> findByEmployeeIdAndYearOrderByCreatedAtDesc(Integer empId, int year);

    List<LeaveBalanceAudit> findByEmployeeIdAndLeaveTypeIdAndYearOrderByCreatedAtDesc(
        Integer empId, Integer typeId, int year
    );

    List<LeaveBalanceAudit> findByReferenceLeaveIdOrderByCreatedAtAsc(Integer leaveRequestId);
}
