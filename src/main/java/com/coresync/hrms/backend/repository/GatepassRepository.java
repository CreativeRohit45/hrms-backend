// src/main/java/com/coresync/hrms/backend/repository/GatepassRepository.java
package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.Gatepass;
import com.coresync.hrms.backend.enums.GatepassStatus;
import com.coresync.hrms.backend.enums.GatepassType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GatepassRepository extends JpaRepository<Gatepass, Integer> {

    List<Gatepass> findByEmployeeIdOrderByCreatedAtDesc(Integer employeeId);

    List<Gatepass> findByStatusOrderByCreatedAtDesc(GatepassStatus status);

    @Query("SELECT g FROM Gatepass g WHERE g.employee.id = :empId " +
           "AND g.status IN ('PENDING', 'APPROVED') " +
           "AND (" +
           "(g.requestedOutTime <= :end AND g.requestedInTime >= :start)" +
           ")")
    List<Gatepass> findOverlappingRequests(
        @Param("empId") Integer empId, 
        @Param("start") LocalDateTime start, 
        @Param("end")   LocalDateTime end
    );

    @Query("SELECT g FROM Gatepass g WHERE g.attendanceLog.id = :logId " +
           "AND g.gatepassType = :type " +
           "AND g.actualOutTime IS NOT NULL " +
           "AND g.actualInTime IS NOT NULL")
    List<Gatepass> findCompletedByLogAndType(
        @Param("logId") Long logId,
        @Param("type")  GatepassType type
    );

    @Query("SELECT g FROM Gatepass g WHERE g.employee.id = :empId " +
           "AND g.status = 'APPROVED' " +
           "AND g.actualInTime IS NULL " +
           "ORDER BY g.requestedOutTime ASC")
    List<Gatepass> findActiveApprovedByEmployee(@Param("empId") Integer empId);

    @Query("SELECT g FROM Gatepass g WHERE g.status = :status AND g.employee.department.id = :deptId")
    List<Gatepass> findByStatusAndEmployeeDepartmentId(
        @Param("status") GatepassStatus status,
        @Param("deptId") Integer deptId
    );
}