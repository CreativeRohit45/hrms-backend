package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.coresync.hrms.backend.projection.UnifiedInboxProjection;
import org.springframework.data.jpa.repository.EntityGraph;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {

    @Query("SELECT a FROM AttendanceLog a WHERE a.employee.id = :employeeId AND a.punchOutTime IS NULL ORDER BY a.workDate DESC, a.id DESC")
    List<AttendanceLog> findOpenSession(@Param("employeeId") Integer employeeId);

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

    @EntityGraph(attributePaths = {"employee", "shift", "location"})
    List<AttendanceLog> findByWorkDateOrderByPunchInTimeDesc(LocalDate workDate);

    @EntityGraph(attributePaths = {"employee", "shift", "location"})
    Page<AttendanceLog> findByWorkDateOrderByPunchInTimeDesc(LocalDate workDate, org.springframework.data.domain.Pageable pageable);

    boolean existsByEmployeeIdAndWorkDateAndPunchOutTimeIsNotNull(Integer employeeId, LocalDate workDate);

    Optional<AttendanceLog> findFirstByEmployeeIdAndWorkDateOrderByIdDesc(Integer employeeId, LocalDate workDate);

    List<AttendanceLog> findByEmployeeEmployeeCodeOrderByWorkDateDesc(String employeeCode);

    @EntityGraph(attributePaths = {"employee", "shift", "location"})
    Page<AttendanceLog> findByWorkDateAndShiftIdOrderByPunchInTimeDesc(LocalDate workDate, Integer shiftId, Pageable pageable);

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

    @Query("SELECT a FROM AttendanceLog a WHERE a.correctionStatus = :status AND a.employee.department.id = :deptId AND a.employee.id <> :excludeEmployeeId")
    List<AttendanceLog> findByCorrectionStatusAndEmployeeDepartmentIdAndEmployeeIdNot(
        @Param("status") com.coresync.hrms.backend.enums.CorrectionStatus status,
        @Param("deptId") Integer deptId,
        @Param("excludeEmployeeId") Integer excludeEmployeeId
    );

    @Query("SELECT a FROM AttendanceLog a WHERE a.correctionStatus = :status AND a.employee.id <> :excludeEmployeeId")
    List<AttendanceLog> findByCorrectionStatusAndEmployeeIdNot(
        @Param("status") com.coresync.hrms.backend.enums.CorrectionStatus status,
        @Param("excludeEmployeeId") Integer excludeEmployeeId
    );

    List<AttendanceLog> findByCorrectionStatus(com.coresync.hrms.backend.enums.CorrectionStatus status);

    @EntityGraph(attributePaths = {"employee", "shift", "location"})
    @Query("SELECT a FROM AttendanceLog a WHERE a.employee.department.id = :deptId AND a.workDate = :date")
    Page<AttendanceLog> findByEmployeeDepartmentIdAndWorkDate(
        @Param("deptId") Integer deptId,
        @Param("date")   LocalDate date,
        org.springframework.data.domain.Pageable pageable
    );

    @EntityGraph(attributePaths = {"employee", "shift", "location"})
    @Query("SELECT a FROM AttendanceLog a WHERE a.employee.department.id = :deptId AND a.workDate = :date AND a.shift.id = :shiftId")
    Page<AttendanceLog> findByEmployeeDepartmentIdAndWorkDateAndShiftId(
        @Param("deptId") Integer deptId,
        @Param("date")   LocalDate date,
        @Param("shiftId") Integer shiftId,
        org.springframework.data.domain.Pageable pageable
    );

    @Query(value = """
        WITH InboxQueue AS (
            SELECT
                'LEAVE' as request_type,
                lr.id as source_id,
                lr.employee_id,
                lr.reason as details,
                lr.created_at,
                lr.start_date as reference_date,
                lr.end_date as reference_end_date,
                lr.status,
                lt.name as leave_type_name,
                lr.applied_days,
                lr.is_half_day as half_day,
                lr.half_day_session,
                NULL as gatepass_type,
                NULL as requested_out_time,
                NULL as requested_in_time,
                NULL as actual_out_time,
                NULL as actual_in_time,
                NULL as emergency,
                NULL as original_punch_in_time,
                NULL as original_punch_out_time,
                NULL as requested_punch_in_time,
                NULL as requested_punch_out_time,
                NULL as overtime_minutes,
                NULL as attendance_status,
                lr.rejection_reason
            FROM leave_requests lr
            INNER JOIN leave_types lt ON lr.leave_type_id = lt.id
            
            UNION ALL
            
            SELECT
                'GATEPASS',
                gp.id,
                gp.employee_id,
                gp.reason,
                gp.created_at,
                gp.request_date,
                gp.request_date,
                gp.status,
                NULL,
                NULL,
                NULL,
                NULL,
                gp.gatepass_type,
                gp.requested_out_time,
                gp.requested_in_time,
                gp.actual_out_time,
                gp.actual_in_time,
                gp.is_emergency,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                gp.rejection_reason
            FROM gatepasses gp
            
            UNION ALL
            
            SELECT
                'CORRECTION',
                al.id,
                al.employee_id,
                al.correction_reason,
                al.created_at,
                al.work_date,
                al.work_date,
                al.correction_status,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                al.punch_in_time,
                al.punch_out_time,
                al.requested_punch_in_time,
                al.requested_punch_out_time,
                NULL,
                al.attendance_status,
                NULL
            FROM attendance_logs al
            WHERE al.correction_status != 'NONE'
            
            UNION ALL
            
            SELECT
                'OVERTIME',
                al.id,
                al.employee_id,
                CONCAT('OT Work (', al.overtime_minutes, ' mins) on ', al.work_date),
                al.created_at,
                al.work_date,
                al.work_date,
                CASE 
                    WHEN al.is_overtime_approved IS NULL THEN 'PENDING'
                    WHEN al.is_overtime_approved = true THEN 'APPROVED'
                    ELSE 'REJECTED'
                END as status,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                al.punch_in_time,
                al.punch_out_time,
                NULL,
                NULL,
                al.overtime_minutes,
                al.attendance_status,
                NULL
            FROM attendance_logs al
            WHERE al.is_overtime = true
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
            i.reference_date as referenceDate,
            i.reference_end_date as referenceEndDate,
            e.department_id as departmentId,
            d.name as departmentName,
            i.leave_type_name as leaveTypeName,
            i.applied_days as appliedDays,
            i.half_day as halfDayRaw,
            i.half_day_session as halfDaySession,
            i.gatepass_type as gatepassType,
            i.requested_out_time as requestedOutTime,
            i.requested_in_time as requestedInTime,
            i.actual_out_time as actualOutTime,
            i.actual_in_time as actualInTime,
            i.emergency as emergencyRaw,
            i.original_punch_in_time as originalPunchInTime,
            i.original_punch_out_time as originalPunchOutTime,
            i.requested_punch_in_time as requestedPunchInTime,
            i.requested_punch_out_time as requestedPunchOutTime,
            i.overtime_minutes as overtimeMinutes,
            i.attendance_status as attendanceStatus,
            i.rejection_reason as rejectionReason
        FROM InboxQueue i
        INNER JOIN employees e ON i.employee_id = e.id
        INNER JOIN departments d ON e.department_id = d.id
        WHERE (:deptId IS NULL OR e.department_id = :deptId)
        AND (:status IS NULL OR i.status = :status)
        AND (:requestType IS NULL OR i.request_type = :requestType)
        AND (:requesterRole IS NULL OR e.role = :requesterRole)
        AND (:excludeEmpId IS NULL OR e.id != :excludeEmpId)
        ORDER BY i.created_at DESC
        """, 
        countQuery = """
        WITH InboxQueue AS (
            SELECT 'LEAVE' as request_type, employee_id, status FROM leave_requests
            UNION ALL
            SELECT 'GATEPASS', employee_id, status FROM gatepasses
            UNION ALL
            SELECT 'CORRECTION', employee_id, correction_status as status FROM attendance_logs WHERE correction_status != 'NONE'
            UNION ALL
            SELECT 'OVERTIME', employee_id, 
                CASE 
                    WHEN is_overtime_approved IS NULL THEN 'PENDING'
                    WHEN is_overtime_approved = true THEN 'APPROVED'
                    ELSE 'REJECTED'
                END as status
            FROM attendance_logs 
            WHERE is_overtime = true
        )
        SELECT count(*) FROM InboxQueue i
        INNER JOIN employees e ON i.employee_id = e.id
        WHERE (:deptId IS NULL OR e.department_id = :deptId)
        AND (:status IS NULL OR i.status = :status)
        AND (:requestType IS NULL OR i.request_type = :requestType)
        AND (:requesterRole IS NULL OR e.role = :requesterRole)
        AND (:excludeEmpId IS NULL OR e.id != :excludeEmpId)
        """, 
        nativeQuery = true)
    Page<UnifiedInboxProjection> getUnifiedInbox(
        @Param("deptId") Integer deptId, 
        @Param("status") String status, 
        @Param("requestType") String requestType,
        @Param("requesterRole") String requesterRole,
        @Param("excludeEmpId") Integer excludeEmpId,
        Pageable pageable);
}
