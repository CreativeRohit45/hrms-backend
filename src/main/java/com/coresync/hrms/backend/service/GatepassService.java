// src/main/java/com/coresync/hrms/backend/service/GatepassService.java
package com.coresync.hrms.backend.service;

import com.coresync.hrms.backend.dto.GatepassActionRequest;
import com.coresync.hrms.backend.dto.GatepassApplyRequest;
import com.coresync.hrms.backend.dto.GatepassResponse;
import com.coresync.hrms.backend.entity.AttendanceLog;
import com.coresync.hrms.backend.entity.Employee;
import com.coresync.hrms.backend.entity.Gatepass;
import com.coresync.hrms.backend.enums.GatepassStatus;
import com.coresync.hrms.backend.repository.AttendanceLogRepository;
import com.coresync.hrms.backend.repository.EmployeeRepository;
import com.coresync.hrms.backend.repository.GatepassRepository;
import jakarta.persistence.EntityNotFoundException;
import com.coresync.hrms.backend.enums.EmployeeRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GatepassService {

    private final GatepassRepository gatepassRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public GatepassResponse applyGatepass(Integer employeeId, GatepassApplyRequest request) {
        Employee employee = findEmployee(employeeId);
        
        // --- NIGHT SHIFT AWARE VALIDATION ---
        java.time.LocalDateTime effectiveInTime = request.getRequestedInTime();
        java.time.LocalDateTime effectiveOutTime = request.getRequestedOutTime();
        
        com.coresync.hrms.backend.entity.Shift shift = employee.getShift();
        
        // If return time is numerically before out time on the same date (IN LOCAL TIME),
        // it implies crossing midnight for an overnight shift.
        if (effectiveInTime.toLocalTime().isBefore(effectiveOutTime.toLocalTime())) {
            if (shift.isOvernight() || shift.getEndTime().isBefore(shift.getStartTime())) {
                // Return time is likely on the next day
                effectiveInTime = effectiveInTime.plusDays(1);
            }
        }

        if (!effectiveOutTime.isBefore(effectiveInTime)) {
            throw new IllegalArgumentException("Expected return time must be after out time.");
        }

        // --- OVERLAP GUARD ---
        List<Gatepass> overlaps = gatepassRepository.findOverlappingRequests(
            employeeId, effectiveOutTime, effectiveInTime);
        if (!overlaps.isEmpty()) {
            throw new IllegalStateException("You already have an active or pending gatepass request that overlaps with this time slot.");
        }
        
        Gatepass gatepass = Gatepass.builder()
            .employee(employee)
            .requestDate(LocalDate.now())
            .requestedOutTime(effectiveOutTime)
            .outTime(effectiveOutTime)
            .requestedInTime(effectiveInTime)
            .expectedInTime(effectiveInTime)
            .gatepassType(request.getGatepassType())
            .reason(request.getReason())
            .status(GatepassStatus.PENDING)
            .build();

        Gatepass saved = gatepassRepository.save(gatepass);
        log.info("[GatepassService] Gatepass applied | Employee: {} | Type: {} | Slot: {} - {}", 
            employee.getEmployeeCode(), request.getGatepassType(), request.getRequestedOutTime(), request.getRequestedInTime());
        return toResponse(saved);
    }

    @Transactional
    public GatepassResponse approveGatepass(Integer gatepassId, Integer approverId) {
        Gatepass gatepass = findGatepass(gatepassId);
        if (gatepass.getStatus() != GatepassStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be approved.");
        }

        Employee approver = findEmployee(approverId);
        gatepass.setStatus(GatepassStatus.APPROVED);
        gatepass.setApprovedBy(approver);
        gatepass.setApprovedAt(LocalDateTime.now());
        
        Gatepass saved = gatepassRepository.save(gatepass);
        log.info("[GatepassService] Gatepass APPROVED | ID: {} | By: {}", gatepassId, approver.getEmployeeCode());
        return toResponse(saved);
    }

    @Transactional
    public GatepassResponse rejectGatepass(Integer gatepassId, Integer approverId, GatepassActionRequest actionRequest) {
        if (actionRequest.getRejectionReason() == null || actionRequest.getRejectionReason().isBlank()) {
            throw new IllegalArgumentException("A rejection reason is required.");
        }
        
        Gatepass gatepass = findGatepass(gatepassId);
        if (gatepass.getStatus() != GatepassStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be rejected.");
        }

        Employee approver = findEmployee(approverId);
        gatepass.setStatus(GatepassStatus.REJECTED);
        gatepass.setApprovedBy(approver);
        gatepass.setApprovedAt(LocalDateTime.now());
        gatepass.setRejectionReason(actionRequest.getRejectionReason());
        
        Gatepass saved = gatepassRepository.save(gatepass);
        log.info("[GatepassService] Gatepass REJECTED | ID: {} | By: {} | Reason: {}", 
            gatepassId, approver.getEmployeeCode(), actionRequest.getRejectionReason());
        return toResponse(saved);
    }

    @Transactional
    public GatepassResponse cancelGatepass(Integer gatepassId, Integer requesterId) {
        Gatepass gatepass = findGatepass(gatepassId);
        if (!gatepass.getEmployee().getId().equals(requesterId)) {
            throw new SecurityException("Unauthorized to cancel this gatepass.");
        }

        // --- CANCELLATION GUARD ---
        if (gatepass.getStatus() == GatepassStatus.REJECTED || gatepass.getStatus() == GatepassStatus.CANCELLED) {
            throw new IllegalStateException("Gatepass is already " + gatepass.getStatus());
        }
        if (gatepass.getActualOutTime() != null) {
            throw new IllegalStateException("Cannot cancel a gatepass once you have marked your exit.");
        }

        gatepass.setStatus(GatepassStatus.CANCELLED);
        Gatepass saved = gatepassRepository.save(gatepass);
        log.info("[GatepassService] Gatepass CANCELLED by employee | ID: {}", gatepassId);
        return toResponse(saved);
    }

    @Transactional
    public GatepassResponse markExit(Integer gatepassId, Integer employeeId) {
        Gatepass gatepass = findGatepass(gatepassId);
        if (!gatepass.getEmployee().getId().equals(employeeId)) {
            throw new SecurityException("Not your gatepass.");
        }
        if (gatepass.getStatus() != GatepassStatus.APPROVED) {
            throw new IllegalStateException("Gatepass must be APPROVED to mark exit.");
        }

        // Capture current log
        AttendanceLog activeLog = attendanceLogRepository.findOpenSession(employeeId)
            .orElseThrow(() -> new IllegalStateException("You must be clocked-in for an active shift to mark a gatepass exit."));

        if (gatepass.getActualOutTime() != null) {
            throw new IllegalStateException("Exit time already recorded for this gatepass.");
        }

        gatepass.setActualOutTime(LocalDateTime.now());
        gatepass.setAttendanceLog(activeLog);
        
        return toResponse(gatepassRepository.save(gatepass));
    }

    @Transactional
    public GatepassResponse markEntry(Integer gatepassId, Integer employeeId) {
        Gatepass gatepass = findGatepass(gatepassId);
        if (!gatepass.getEmployee().getId().equals(employeeId)) {
            throw new SecurityException("Not your gatepass.");
        }
        if (gatepass.getActualOutTime() == null) {
            throw new IllegalStateException("You must mark exit before marking entry.");
        }
        if (gatepass.getActualInTime() != null) {
            throw new IllegalStateException("Entry already recorded.");
        }

        gatepass.setActualInTime(LocalDateTime.now());
        return toResponse(gatepassRepository.save(gatepass));
    }

    @Transactional(readOnly = true)
    public List<GatepassResponse> getMyGatepasses(Integer employeeId) {
        return gatepassRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GatepassResponse> getPendingGatepasses(Integer managerId) {
        Employee manager = employeeRepository.findById(managerId)
            .orElseThrow(() -> new EntityNotFoundException("Manager not found"));

        if (manager.getRole() == EmployeeRole.DEPARTMENT_MANAGER) {
            return gatepassRepository.findByStatusAndEmployeeDepartmentId(
                GatepassStatus.PENDING, manager.getDepartment().getId()).stream().map(this::toResponse).collect(Collectors.toList());
        }
        return gatepassRepository.findByStatusOrderByCreatedAtDesc(GatepassStatus.PENDING).stream().map(this::toResponse).collect(Collectors.toList());
    }

    private Gatepass findGatepass(Integer id) {
        return gatepassRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Gatepass not found: ID " + id));
    }

    private Employee findEmployee(Integer id) {
        return employeeRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Employee not found: ID " + id));
    }

    private GatepassResponse toResponse(Gatepass g) {
        if (g == null) return null;
        
        Long attendanceLogId = null;
        try {
            attendanceLogId = g.getAttendanceLog() != null ? g.getAttendanceLog().getId() : null;
        } catch (EntityNotFoundException | org.hibernate.ObjectNotFoundException e) {
            log.warn("[GatepassService] AttendanceLog proxy resolution failed for Gatepass ID: {}", g.getId());
        }

        return GatepassResponse.builder()
            .id(g.getId())
            .employeeId(g.getEmployee() != null ? g.getEmployee().getId() : null)
            .employeeCode(g.getEmployee() != null ? g.getEmployee().getEmployeeCode() : "N/A")
            .fullName(g.getEmployee() != null ? g.getEmployee().getFullName() : "N/A")
            .attendanceLogId(attendanceLogId)
            .requestDate(g.getRequestDate())
            .requestedOutTime(g.getRequestedOutTime())
            .requestedInTime(g.getRequestedInTime())
            .actualOutTime(g.getActualOutTime())
            .actualInTime(g.getActualInTime())
            .gatepassType(g.getGatepassType() != null ? g.getGatepassType().name() : null)
            .status(g.getStatus() != null ? g.getStatus().name() : null)
            .reason(g.getReason())
            .approvedById(g.getApprovedBy() != null ? g.getApprovedBy().getId() : null)
            .approvedByName(g.getApprovedBy() != null ? g.getApprovedBy().getFullName() : null)
            .approvedAt(g.getApprovedAt())
            .rejectionReason(g.getRejectionReason())
            .isEmergency(g.getIsEmergency())
            .createdAt(g.getCreatedAt())
            .build();
    }

}