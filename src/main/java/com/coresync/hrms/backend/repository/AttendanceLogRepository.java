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

    List<AttendanceLog> findByWorkDateOrderByPunchInTimeDesc(LocalDate workDate);
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

    @Query(value = """
        WITH InboxQueue AS (
            SELECT 'LEAVE' as request_type, id as source_id, employee_id, reason as details, created_at, start_date as reference_date, status 
            FROM leave_requests WHERE status = 'PENDING'
            UNION ALL
            SELECT 'GATEPASS', id, employee_id, reason, created_at, request_date, status 
            FROM gatepasses WHERE status = 'PENDING'
            UNION ALL
            SELECT 'CORRECTION', id, employee_id, correction_reason, created_at, work_date, correction_status 
            FROM attendance_logs WHERE correction_status = 'PENDING'
        )
        SELECT 
            CONCAT(i.request_type, '-', i.source_id) as id,
            i.request_type as requestType,
            e.id as employeeId,
            e.full_name as employeeName,
            e.employee_code as employeeCode,
            i.details as details,
            i.status as status,
            i.created_at as createdAt,
            i.reference_date as referenceDate
        FROM InboxQueue i
        INNER JOIN employees e ON i.employee_id = e.id
        WHERE (:deptId IS NULL OR e.department_id = :deptId)
        ORDER BY i.created_at DESC
        """, 
        countQuery = """
        WITH InboxQueue AS (
            SELECT employee_id FROM leave_requests WHERE status = 'PENDING'
            UNION ALL
            SELECT employee_id FROM gatepasses WHERE status = 'PENDING'
            UNION ALL
            SELECT employee_id FROM attendance_logs WHERE correction_status = 'PENDING'
        )
        SELECT count(*) FROM InboxQueue i
        INNER JOIN employees e ON i.employee_id = e.id
        WHERE (:deptId IS NULL OR e.department_id = :deptId)
        """, 
        nativeQuery = true)
    Page<UnifiedInboxProjection> getUnifiedInbox(@Param("deptId") Integer deptId, Pageable pageable);
}