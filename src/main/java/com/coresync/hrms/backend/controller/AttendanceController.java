// src/main/java/com/coresync/hrms/backend/controller/AttendanceController.java
package com.coresync.hrms.backend.controller;

import com.coresync.hrms.backend.dto.AttendanceLogResponse;
import com.coresync.hrms.backend.dto.CorrectionRequest;
import com.coresync.hrms.backend.dto.PunchInRequest;
import com.coresync.hrms.backend.dto.PunchOutRequest;
import com.coresync.hrms.backend.entity.AttendanceLog;
import com.coresync.hrms.backend.entity.Employee;
import com.coresync.hrms.backend.repository.EmployeeRepository;
import com.coresync.hrms.backend.service.AttendanceService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {
    private final AttendanceService attendanceService;
    private final EmployeeRepository employeeRepository;

    @PostMapping("/punch-in")
    public ResponseEntity<AttendanceLogResponse> punchIn(@Valid @RequestBody PunchInRequest request) {
        Integer employeeId = resolveEmployeeIdFromJwt();
        AttendanceLog log = attendanceService.punchIn(employeeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(log));
    }

    @PostMapping("/punch-out")
    public ResponseEntity<AttendanceLogResponse> punchOut(@Valid @RequestBody PunchOutRequest request) {
        Integer employeeId = resolveEmployeeIdFromJwt();
        AttendanceLog log = attendanceService.punchOut(employeeId, request.getLatitude(), request.getLongitude());
        return ResponseEntity.ok(toResponse(log));
    }

    @GetMapping("/my-logs")
    public ResponseEntity<List<AttendanceLogResponse>> getMyLogs(Authentication authentication) {
        String employeeCode = authentication.getName();
        List<AttendanceLog> logs = attendanceService.getMyLogs(employeeCode);
        
        List<AttendanceLogResponse> responseBody = logs.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
                
        return ResponseEntity.ok(responseBody);
    }

    @GetMapping("/employee/{employeeCode}/logs")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN', 'DEPARTMENT_MANAGER')")
    public ResponseEntity<List<AttendanceLogResponse>> getEmployeeLogs(@PathVariable String employeeCode) {
        // We can reuse getMyLogs logic which just queries by employee code sorted by date!
        List<AttendanceLog> logs = attendanceService.getMyLogs(employeeCode);
        
        List<AttendanceLogResponse> responseBody = logs.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
                
        return ResponseEntity.ok(responseBody);
    }

    @GetMapping("/daily")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN', 'DEPARTMENT_MANAGER')")
    public ResponseEntity<List<AttendanceLogResponse>> getDailyLogs(@RequestParam("date") String date) {
        java.time.LocalDate targetDate = java.time.LocalDate.parse(date);
        List<AttendanceLog> logs = attendanceService.getDailyLogs(targetDate);
        
        List<AttendanceLogResponse> responseBody = logs.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
                
        return ResponseEntity.ok(responseBody);
    }

    private Integer resolveEmployeeIdFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String employeeCode = auth.getName();
        return employeeRepository.findByEmployeeCode(employeeCode)
            .map(Employee::getId)
            .orElseThrow(() -> new EntityNotFoundException("Authenticated employee not found: " + employeeCode));
    }

    private AttendanceLogResponse toResponse(AttendanceLog entity) {
        return AttendanceLogResponse.builder()
            .id(entity.getId())
            .employeeId(entity.getEmployee().getId())
            .employeeCode(entity.getEmployee().getEmployeeCode())
            .fullName(entity.getEmployee().getFullName())
            .workDate(entity.getWorkDate())
            .punchInTime(entity.getPunchInTime())
            .punchOutTime(entity.getPunchOutTime())
            .locationVerifiedIn(entity.isLocationVerifiedIn())
            .locationVerifiedOut(entity.getIsLocationVerifiedOut())
            .calculatedPayableMinutes(entity.getCalculatedPayableMinutes())
            .overtime(entity.isOvertime())
            .overtimeMinutes(entity.getOvertimeMinutes())
            .attendanceStatus(entity.getAttendanceStatus())
            .manuallyCorrected(entity.isManuallyCorrected())
            .correctionReason(entity.getCorrectionReason())
            .correctionStatus(entity.getCorrectionStatus())
            .requestedPunchInTime(entity.getRequestedPunchInTime())
            .requestedPunchOutTime(entity.getRequestedPunchOutTime())
            .isOvertimeApproved(entity.getIsOvertimeApproved())
            .shiftStartTime(entity.getShift().getStartTime())
            .shiftEndTime(entity.getShift().getEndTime())
            .build();
    }
    
    // --- FEATURE 1: CORRECTIONS ---
    @PostMapping("/corrections/{logId}")
    public ResponseEntity<AttendanceLogResponse> requestCorrection(
            @PathVariable Long logId, @Valid @RequestBody CorrectionRequest request) {
        return ResponseEntity.ok(toResponse(attendanceService.requestCorrection(logId, request)));
    }

    @PutMapping("/corrections/{logId}/approve")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AttendanceLogResponse> approveCorrection(@PathVariable Long logId) {
        return ResponseEntity.ok(toResponse(attendanceService.approveCorrection(logId)));
    }

    @PutMapping("/corrections/{logId}/reject")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AttendanceLogResponse> rejectCorrection(
            @PathVariable Long logId, @RequestBody String reason) {
        return ResponseEntity.ok(toResponse(attendanceService.rejectCorrection(logId, reason)));
    }

    // --- FEATURE 2: OVERTIME APPROVAL ---
    @PutMapping("/{logId}/approve-overtime")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AttendanceLogResponse> approveOvertime(@PathVariable Long logId) {
        return ResponseEntity.ok(toResponse(attendanceService.approveOvertime(logId)));
    }
}