// src/main/java/com/coresync/hrms/backend/repository/LeaveRequestRepository.java
package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.LeaveRequest;
import com.coresync.hrms.backend.enums.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Integer> {

    @Query("SELECT l FROM LeaveRequest l WHERE l.employee.id = :employeeId " +
           "AND l.status IN :activeStatuses AND l.startDate <= :endDate AND l.endDate >= :startDate")
    List<LeaveRequest> findOverlapping(
        @Param("employeeId")     Integer employeeId,
        @Param("startDate")      LocalDate startDate,
        @Param("endDate")        LocalDate endDate,
        @Param("activeStatuses") List<LeaveStatus> activeStatuses
    );

    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Integer employeeId);

    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(LeaveStatus status);

    @Query("SELECT l FROM LeaveRequest l WHERE l.employee.id = :employeeId " +
           "AND l.leaveType.id = :leaveTypeId AND l.status IN :statuses " +
           "AND l.startDate >= :periodStart AND l.endDate <= :periodEnd")
    List<LeaveRequest> findByEmployeeAndTypeInPeriod(
        @Param("employeeId")  Integer employeeId,
        @Param("leaveTypeId") Integer leaveTypeId,
        @Param("statuses")    List<LeaveStatus> statuses,
        @Param("periodStart") LocalDate periodStart,
        @Param("periodEnd")   LocalDate periodEnd
    );

    /**
     * Find all APPROVED leaves for employees in a given department that overlap with
     * the specified date range. Used for the "Team Time Off (This Week)" widget.
     */
    @Query("SELECT l FROM LeaveRequest l WHERE l.employee.department.id = :departmentId " +
           "AND l.employee.id <> :excludeEmployeeId " +
           "AND l.status = com.coresync.hrms.backend.enums.LeaveStatus.APPROVED " +
           "AND l.startDate <= :weekEnd AND l.endDate >= :weekStart " +
           "ORDER BY l.startDate ASC")
    List<LeaveRequest> findApprovedLeavesForDepartmentInRange(
        @Param("departmentId")      Integer departmentId,
        @Param("excludeEmployeeId") Integer excludeEmployeeId,
        @Param("weekStart")         LocalDate weekStart,
        @Param("weekEnd")           LocalDate weekEnd
    );

    @Query("SELECT COUNT(l) > 0 FROM LeaveRequest l WHERE l.employee.id = :employeeId " +
           "AND l.status = com.coresync.hrms.backend.enums.LeaveStatus.APPROVED " +
           "AND l.startDate <= :date AND l.endDate >= :date")
    boolean existsApprovedLeaveOnDate(
        @Param("employeeId") Integer employeeId,
        @Param("date")       LocalDate date
    );

    @Query("SELECT l FROM LeaveRequest l WHERE l.status = :status AND l.employee.department.id = :deptId")
    List<LeaveRequest> findByStatusAndEmployeeDepartmentId(
        @Param("status") LeaveStatus status,
        @Param("deptId") Integer deptId
    );

    @Query("SELECT l FROM LeaveRequest l WHERE l.status = :status AND l.employee.id <> :excludeEmployeeId ORDER BY l.createdAt DESC")
    List<LeaveRequest> findByStatusAndEmployeeIdNot(
        @Param("status") LeaveStatus status,
        @Param("excludeEmployeeId") Integer excludeEmployeeId
    );

    @Query("SELECT l FROM LeaveRequest l WHERE l.status = :status AND l.employee.department.id = :deptId AND l.employee.id <> :excludeEmployeeId ORDER BY l.createdAt DESC")
    List<LeaveRequest> findByStatusAndEmployeeDepartmentIdAndEmployeeIdNot(
        @Param("status") LeaveStatus status,
        @Param("deptId") Integer deptId,
        @Param("excludeEmployeeId") Integer excludeEmployeeId
    );
}