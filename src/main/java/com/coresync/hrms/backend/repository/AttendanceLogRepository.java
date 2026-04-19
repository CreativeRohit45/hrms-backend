package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.coresync.hrms.backend.projection.UnifiedInboxProjection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {

    @Query("SELECT a FROM AttendanceLog a WHERE a.employee.id = :employeeId AND a.punchOutTime IS NULL")
    Optional<AttendanceLog> findOpenSession(@Param("employeeId") Integer employeeId);

    @Query("SELECT a FROM AttendanceLog a WHERE a.employee.id = :employeeId " +
           "AND a.workDate BETWEEN :startDate AND :endDate " +
           "AND (a.punchOutTime IS NOT NULL OR a.attendanceStatus NOT IN (com.coresync.hrms.backend.enums.AttendanceStatus.PRESENT, com.coresync.hrms.backend.enums.AttendanceStatus.LATE))")
    List<AttendanceLog> findClosedSessionsForPeriod(
        @Param("employeeId") Integer employeeId,
        @Param("startDate")  LocalDate startDate,
        @Param("endDate")    LocalDate endDate
    );

    List<AttendanceLog> findByEmployeeIdOrderByWorkDateDesc(Integer employeeId);

    Optional<AttendanceLog> findByEmployeeIdAndPunchOutTimeIsNull(Integer employeeId);

    Page<AttendanceLog> findByWorkDateOrderByPunchInTimeDesc(LocalDate workDate, org.springframework.data.domain.Pageable pageable);

    boolean existsByEmployeeIdAndWorkDateAndPunchOutTimeIsNotNull(Integer employeeId, LocalDate workDate);

    Optional<AttendanceLog> findFirstByEmployeeIdAndWorkDateOrderByIdDesc(Integer employeeId, LocalDate workDate);

    List<AttendanceLog> findByEmployeeEmployeeCodeOrderByWorkDateDesc(String employeeCode);

    // Cron Job: Finds open sessions to auto-close
    @Query("SELECT a FROM AttendanceLog a WHERE a.workDate = :date AND a.punchOutTime IS NULL")
    List<AttendanceLog> findOpenSessionsByWorkDate(@Param("date") LocalDate date);

    @Query("SELECT a.employee.id FROM AttendanceLog a WHERE a.workDate = :date")
    Set<Integer> findEmployeeIdsWithLogOnDate(@Param("date") LocalDate date);

    @Query("SELECT a.employee.id FROM AttendanceLog a " +
           "WHERE a.employee.department.id = :deptId " +
           "AND a.workDate = :date " +
           "AND a.attendanceStatus IN (:statuses)")
    List<Integer> findEmployeeIdsWithStatusOnDate(
        @Param("deptId") Integer deptId,
        @Param("date")   LocalDate date,
        @Param("statuses") List<com.coresync.hrms.backend.enums.AttendanceStatus> statuses
    );

    @Query("SELECT a FROM AttendanceLog a WHERE a.correctionStatus = :status AND a.employee.department.id = :deptId")
    List<AttendanceLog> findByCorrectionStatusAndEmployeeDepartmentId(
        @Param("status") com.coresync.hrms.backend.enums.CorrectionStatus status,
        @Param("deptId") Integer deptId
    );

    List<AttendanceLog> findByCorrectionStatus(com.coresync.hrms.backend.enums.CorrectionStatus status);

    @Query("SELECT a FROM AttendanceLog a WHERE a.employee.department.id = :deptId AND a.workDate = :date")
    Page<AttendanceLog> findByEmployeeDepartmentIdAndWorkDate(
        @Param("deptId") Integer deptId,
        @Param("date")   LocalDate date,
        org.springframework.data.domain.Pageable pageable
    );

    @Query(value = 
        "WITH InboxQueue AS (" +
        "  SELECT CONCAT('LEAVE-', CAST(lr.id AS CHAR)) as id, 'LEAVE' as requestType, lr.employee_id, lr.reason as details, lr.status, lr.created_at as createdAt, lr.start_date as referenceDate FROM leave_requests lr WHERE lr.status = 'PENDING' " +
        "  UNION ALL " +
        "  SELECT CONCAT('GATEPASS-', CAST(g.id AS CHAR)) as id, 'GATEPASS' as requestType, g.employee_id, g.reason as details, g.status, g.created_at as createdAt, g.request_date as referenceDate FROM gatepasses g WHERE g.status = 'PENDING' " +
        "  UNION ALL " +
        "  SELECT CONCAT('CORRECTION-', CAST(al.id AS CHAR)) as id, 'CORRECTION' as requestType, al.employee_id, al.correction_reason as details, al.correction_status as status, al.created_at as createdAt, al.work_date as referenceDate FROM attendance_logs al WHERE al.correction_status = 'PENDING'" +
        ") " +
        "SELECT i.id, i.requestType, i.employee_id as employeeId, e.full_name as employeeName, e.employee_code as employeeCode, i.details, CAST(i.status AS CHAR) as status, i.createdAt, i.referenceDate " +
        "FROM InboxQueue i " +
        "JOIN employees e ON i.employee_id = e.id " +
        "WHERE (:deptId IS NULL OR e.department_id = :deptId) " +
        "ORDER BY i.createdAt DESC",
        countQuery = 
        "SELECT COUNT(*) FROM (" +
        "  SELECT lr.employee_id FROM leave_requests lr WHERE lr.status = 'PENDING' " +
        "  UNION ALL " +
        "  SELECT g.employee_id FROM gatepasses g WHERE g.status = 'PENDING' " +
        "  UNION ALL " +
        "  SELECT al.employee_id FROM attendance_logs al WHERE al.correction_status = 'PENDING' " +
        ") as i " +
        "JOIN employees e ON i.employee_id = e.id " +
        "WHERE (:deptId IS NULL OR e.department_id = :deptId)",
        nativeQuery = true)
    Page<UnifiedInboxProjection> findUnifiedInbox(@Param("deptId") Integer deptId, Pageable pageable);
}