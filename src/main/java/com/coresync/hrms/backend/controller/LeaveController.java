package com.coresync.hrms.backend.controller;

import com.coresync.hrms.backend.dto.*;
import com.coresync.hrms.backend.entity.Employee;
import com.coresync.hrms.backend.entity.LeaveType;
import com.coresync.hrms.backend.repository.EmployeeRepository;
import com.coresync.hrms.backend.service.LeaveService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;
    private final EmployeeRepository employeeRepository;
    private final com.coresync.hrms.backend.service.LeaveAccrualService leaveAccrualService;

    // ═══════════════════════════════════════════════════════════════════
    //  EMPLOYEE ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════

    @PostMapping
    public ResponseEntity<LeaveResponse> applyLeave(
            @Valid @RequestBody LeaveApplyRequest request,
            Authentication authentication) {
        Integer employeeId = resolveId(authentication);
        LeaveResponse response = leaveService.applyForLeave(employeeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my-requests")
    public ResponseEntity<List<LeaveResponse>> getMyRequests(Authentication authentication) {
        Integer employeeId = resolveId(authentication);
        return ResponseEntity.ok(leaveService.getMyLeaves(employeeId));
    }

    @GetMapping("/balances")
    public ResponseEntity<List<LeaveBalanceResponse>> getMyBalances(Authentication authentication) {
        Integer employeeId = resolveId(authentication);
        return ResponseEntity.ok(leaveService.getEmployeeBalances(employeeId));
    }

    @GetMapping("/balances/audit")
    public ResponseEntity<List<LeaveBalanceAuditResponse>> getMyAuditTrail(
            @RequestParam(required = false) Integer leaveTypeId,
            @RequestParam(required = false) Integer year,
            Authentication authentication) {
        Integer employeeId = resolveId(authentication);
        int targetYear = (year != null) ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(leaveService.getBalanceAuditTrail(employeeId, leaveTypeId, targetYear));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<LeaveResponse> cancelLeave(
            @PathVariable Integer id,
            Authentication authentication) {
        Integer employeeId = resolveId(authentication);
        return ResponseEntity.ok(leaveService.cancelLeave(id, employeeId));
    }

    @PostMapping("/preview")
    public ResponseEntity<LeavePreviewResponse> previewLeave(
            @RequestBody LeaveApplyRequest request,
            Authentication authentication) {
        Integer employeeId = resolveId(authentication);
        return ResponseEntity.ok(leaveService.previewLeave(employeeId, request));
    }

    @GetMapping("/team-availability")
    public ResponseEntity<List<TeamAvailabilityDTO>> getTeamAvailability(
            Authentication authentication) {
        Integer employeeId = resolveId(authentication);
        return ResponseEntity.ok(leaveService.getTeamAvailability(employeeId));
    }

    @GetMapping("/dept-absentees")
    public ResponseEntity<List<DepartmentAbsenteeDTO>> getDeptAbsentees(
            Authentication authentication) {
        Integer employeeId = resolveId(authentication);
        return ResponseEntity.ok(leaveService.getDepartmentAbsentees(employeeId));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  MANAGER / ADMIN ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER', 'HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<LeaveResponse>> getPending(Authentication authentication) {
        Integer managerId = resolveId(authentication);
        return ResponseEntity.ok(leaveService.getPendingLeaves(managerId));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER', 'HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<LeaveResponse> approveLeave(
            @PathVariable Integer id,
            Authentication authentication) {
        Integer approverId = resolveId(authentication);
        return ResponseEntity.ok(leaveService.approveLeave(id, approverId));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER', 'HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<LeaveResponse> rejectLeave(
            @PathVariable Integer id,
            @Valid @RequestBody LeaveActionRequest request,
            Authentication authentication) {
        Integer approverId = resolveId(authentication);
        return ResponseEntity.ok(leaveService.rejectLeave(id, approverId, request));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  HR ADMIN ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════

    @PutMapping("/admin/{id}/revoke")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<LeaveResponse> revokeLeave(
            @PathVariable Integer id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        Integer adminId = resolveId(authentication);
        String reason = body.getOrDefault("reason", "");
        return ResponseEntity.ok(leaveService.revokeLeave(id, adminId, reason));
    }

    @PostMapping("/admin/grant")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<LeaveBalanceResponse> grantLeave(
            @Valid @RequestBody LeaveGrantRequest request,
            Authentication authentication) {
        Integer adminId = resolveId(authentication);
        return ResponseEntity.ok(leaveService.manualAdjustBalance(request, adminId));
    }

    @PostMapping("/admin/accrual/run")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> runAccrual(Authentication authentication) {
        leaveAccrualService.runManualAccrual();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/admin/balances/{employeeId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<LeaveBalanceResponse>> getEmployeeBalances(
            @PathVariable Integer employeeId) {
        return ResponseEntity.ok(leaveService.getEmployeeBalances(employeeId));
    }

    @GetMapping("/admin/audit/{employeeId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<LeaveBalanceAuditResponse>> getEmployeeAuditTrail(
            @PathVariable Integer employeeId,
            @RequestParam(required = false) Integer leaveTypeId,
            @RequestParam(required = false) Integer year) {
        int targetYear = (year != null) ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(leaveService.getBalanceAuditTrail(employeeId, leaveTypeId, targetYear));
    }

    @GetMapping("/admin/requests/{employeeId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<LeaveResponse>> getEmployeeRequests(@PathVariable Integer employeeId) {
        return ResponseEntity.ok(leaveService.getMyLeaves(employeeId));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  REFERENCE DATA
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/types")
    public ResponseEntity<List<LeaveType>> getLeaveTypes() {
        return ResponseEntity.ok(leaveService.getActiveLeaveTypes());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  HELPER
    // ═══════════════════════════════════════════════════════════════════

    private Integer resolveId(Authentication authentication) {
        String code = authentication.getName();
        return employeeRepository.findByEmployeeCode(code)
            .map(Employee::getId)
            .orElseThrow(() -> new EntityNotFoundException("Authenticated employee not found: " + code));
    }
}