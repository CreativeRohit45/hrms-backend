package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.LeaveBalanceAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveBalanceAuditRepository extends JpaRepository<LeaveBalanceAudit, Long> {

    @org.springframework.data.jpa.repository.Query("SELECT a FROM LeaveBalanceAudit a WHERE a.employee.id = :empId AND a.year = :year AND a.leaveType.code <> 'LWP' ORDER BY a.createdAt DESC")
    Page<LeaveBalanceAudit> findByEmployeeIdAndYearOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("empId") Integer empId, @org.springframework.data.repository.query.Param("year") int year, Pageable pageable);

    Page<LeaveBalanceAudit> findByEmployeeIdAndLeaveTypeIdAndYearOrderByCreatedAtDesc(
        Integer empId, Integer typeId, int year, Pageable pageable
    );

    List<LeaveBalanceAudit> findByReferenceLeaveIdOrderByCreatedAtAsc(Integer leaveRequestId);

    List<LeaveBalanceAudit> findByEmployeeIdAndLeaveTypeIdOrderByCreatedAtAsc(Integer empId, Integer typeId);
}
