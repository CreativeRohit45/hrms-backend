package com.coresync.hrms.backend.controller;

import com.coresync.hrms.backend.dto.CorrectionRequest;
import com.coresync.hrms.backend.dto.PunchInRequest;
import com.coresync.hrms.backend.entity.AttendanceLog;
import com.coresync.hrms.backend.entity.Employee;
import com.coresync.hrms.backend.service.AttendanceService;
import com.coresync.hrms.backend.dto.AttendanceLogResponse;
import com.coresync.hrms.backend.repository.EmployeeRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final EmployeeRepository employeeRepository;

    @PostMapping("/punch-in")
    public ResponseEntity<AttendanceLogResponse> punchIn(
            @RequestBody @Valid PunchInRequest request, 
            Authentication authentication) {
        Integer employeeId = resolveId(authentication);
        return ResponseEntity.ok(toResponse(attendanceService.punchIn(employeeId, request)));
    }

    @PostMapping("/punch-out")
    public ResponseEntity<AttendanceLogResponse> punchOut(
            @RequestBody @Valid PunchInRequest request, 
            Authentication authentication) {
        Integer employeeId = resolveId(authentication);
        return ResponseEntity.ok(toResponse(attendanceService.punchOut(employeeId, request.getLatitude(), request.getLongitude())));
    }

    @GetMapping("/my-logs")
    public ResponseEntity<List<AttendanceLogResponse>> getMyLogs(
            @RequestParam(required = false) Integer employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {
        
        Integer targetId = (employeeId != null) ? employeeId : resolveId(authentication);
        
        // Defaults for date range if not provided
        LocalDate start = (startDate != null) ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = (endDate != null) ? endDate : LocalDate.now();

        return ResponseEntity.ok(attendanceService.getLogs(targetId, start, end).stream()
                .map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/employee/{employeeCode}/logs")
    @PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER', 'HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<AttendanceLogResponse>> getEmployeeLogs(
            @PathVariable String employeeCode) {
        return ResponseEntity.ok(attendanceService.getMyLogs(employeeCode).stream()
                .map(this::toResponse).collect(Collectors.toList()));
    }

    @PostMapping("/corrections/{logId}")
    public ResponseEntity<AttendanceLogResponse> applyCorrection(
            @PathVariable Long logId,
            @RequestBody @Valid CorrectionRequest request) {
        return ResponseEntity.ok(toResponse(attendanceService.requestCorrection(logId, request)));
    }

    // --- FEATURE 1: UNIFIED INBOX (Corrections) ---
    @GetMapping({"/pending-corrections", "/corrections/pending"})
    @PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER', 'HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<AttendanceLogResponse>> getPendingCorrections(Authentication authentication) {
        Integer managerId = resolveId(authentication);
        return ResponseEntity.ok(attendanceService.getPendingCorrections(managerId).stream()
                .map(this::toResponse).collect(Collectors.toList()));
    }

    @PutMapping("/corrections/{logId}/approve")
    @PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER', 'HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AttendanceLogResponse> approveCorrection(@PathVariable Long logId) {
        return ResponseEntity.ok(toResponse(attendanceService.approveCorrection(logId)));
    }

    @PutMapping("/corrections/{logId}/reject")
    @PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER', 'HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AttendanceLogResponse> rejectCorrection(
            @PathVariable Long logId, @RequestBody String reason) {
        return ResponseEntity.ok(toResponse(attendanceService.rejectCorrection(logId, reason)));
    }

    // --- FEATURE 2: OVERTIME ---
    @PutMapping("/{logId}/approve-overtime")
    @PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER', 'HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AttendanceLogResponse> approveOvertime(@PathVariable Long logId) {
        return ResponseEntity.ok(toResponse(attendanceService.approveOvertime(logId)));
    }

    // --- FEATURE 3: SCOPED ROSTER ---
    @GetMapping("/roster")
    @PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER', 'HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<AttendanceLogResponse>> getDailyRoster(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        Integer managerId = resolveId(authentication);
        return ResponseEntity.ok(attendanceService.getDailyRoster(managerId, date).stream()
                .map(this::toResponse).collect(Collectors.toList()));
    }

    private Integer resolveId(Authentication authentication) {
        String identifier = authentication.getName();
        return employeeRepository.findByEmployeeCode(identifier)
            .or(() -> employeeRepository.findByEmail(identifier))
            .map(Employee::getId)
            .orElseThrow(() -> new EntityNotFoundException("Employee profile not found: " + identifier));
    }

    private AttendanceLogResponse toResponse(AttendanceLog log) {
        return AttendanceLogResponse.builder()
            .id(log.getId())
            .employeeId(log.getEmployee().getId())
            .fullName(log.getEmployee().getFullName())
            .employeeCode(log.getEmployee().getEmployeeCode())
            .workDate(log.getWorkDate())
            .punchInTime(log.getPunchInTime())
            .punchOutTime(log.getPunchOutTime())
            .locationVerifiedIn(log.isLocationVerifiedIn())
            .locationVerifiedOut(log.getIsLocationVerifiedOut())
            .calculatedPayableMinutes(log.getCalculatedPayableMinutes())
            .overtime(log.isOvertime())
            .overtimeMinutes(log.getOvertimeMinutes())
            .attendanceStatus(log.getAttendanceStatus())
            .manuallyCorrected(log.isManuallyCorrected())
            .correctionReason(log.getCorrectionReason())
            .correctionStatus(log.getCorrectionStatus())
            .requestedPunchInTime(log.getRequestedPunchInTime())
            .requestedPunchOutTime(log.getRequestedPunchOutTime())
            .isOvertimeApproved(log.getIsOvertimeApproved())
            .shiftStartTime(log.getShift() != null ? log.getShift().getStartTime() : null)
            .shiftEndTime(log.getShift() != null ? log.getShift().getEndTime() : null)
            .build();
    }
}