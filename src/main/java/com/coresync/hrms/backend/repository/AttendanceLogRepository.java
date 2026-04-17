package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    List<AttendanceLog> findByEmployeeDepartmentIdAndWorkDate(
        @Param("deptId") Integer deptId,
        @Param("date")   LocalDate date
    );
}