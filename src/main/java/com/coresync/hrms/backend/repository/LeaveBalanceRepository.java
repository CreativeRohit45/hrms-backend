package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.LeaveBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Integer> {

    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(Integer empId, Integer typeId, int year);

    List<LeaveBalance> findByEmployeeIdAndYear(Integer empId, int year);

    List<LeaveBalance> findByEmployeeIdAndYearAndLeaveTypeIsActiveTrue(Integer empId, int year);

    /**
     * Pessimistic write lock for escrow pattern.
     * Prevents double-spend under concurrent requests.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lb FROM LeaveBalance lb WHERE lb.employee.id = :empId AND lb.leaveType.id = :typeId AND lb.year = :year")
    Optional<LeaveBalance> findForUpdate(
        @Param("empId") Integer empId,
        @Param("typeId") Integer typeId,
        @Param("year") int year
    );
}
