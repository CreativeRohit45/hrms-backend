// src/main/java/com/coresync/hrms/backend/controller/GatepassController.java
package com.coresync.hrms.backend.controller;

import com.coresync.hrms.backend.dto.GatepassActionRequest;
import com.coresync.hrms.backend.dto.GatepassApplyRequest;
import com.coresync.hrms.backend.dto.GatepassResponse;
import com.coresync.hrms.backend.entity.Employee;
import com.coresync.hrms.backend.repository.EmployeeRepository;
import com.coresync.hrms.backend.service.GatepassService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/gatepasses")
@RequiredArgsConstructor
public class GatepassController {

    private final GatepassService gatepassService;
    private final EmployeeRepository employeeRepository;

    @PostMapping("/apply")
    public ResponseEntity<GatepassResponse> apply(
            Principal principal,
            @RequestBody GatepassApplyRequest request) {
        Employee employee = getEmployee(principal.getName());
        return ResponseEntity.ok(gatepassService.applyGatepass(employee.getId(), request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER', 'HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<GatepassResponse> approve(
            Principal principal,
            @PathVariable Integer id) {
        Employee approver = getEmployee(principal.getName());
        return ResponseEntity.ok(gatepassService.approveGatepass(id, approver.getId()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER', 'HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<GatepassResponse> reject(
            Principal principal,
            @PathVariable Integer id,
            @RequestBody GatepassActionRequest request) {
        Employee approver = getEmployee(principal.getName());
        return ResponseEntity.ok(gatepassService.rejectGatepass(id, approver.getId(), request));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<GatepassResponse> cancel(
            Principal principal,
            @PathVariable Integer id) {
        Employee employee = getEmployee(principal.getName());
        return ResponseEntity.ok(gatepassService.cancelGatepass(id, employee.getId()));
    }

    @PostMapping("/{id}/exit")
    public ResponseEntity<GatepassResponse> markExit(
            Principal principal,
            @PathVariable Integer id) {
        Employee employee = getEmployee(principal.getName());
        return ResponseEntity.ok(gatepassService.markExit(id, employee.getId()));
    }

    @PostMapping("/{id}/entry")
    public ResponseEntity<GatepassResponse> markEntry(
            Principal principal,
            @PathVariable Integer id) {
        Employee employee = getEmployee(principal.getName());
        return ResponseEntity.ok(gatepassService.markEntry(id, employee.getId()));
    }

    @GetMapping("/my")
    public ResponseEntity<List<GatepassResponse>> getMy(Principal principal) {
        Employee employee = getEmployee(principal.getName());
        return ResponseEntity.ok(gatepassService.getMyGatepasses(employee.getId()));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER', 'HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<GatepassResponse>> getPending(Principal principal) {
        Employee manager = getEmployee(principal.getName());
        return ResponseEntity.ok(gatepassService.getPendingGatepasses(manager.getId()));
    }

    private Employee getEmployee(String identifier) {
        return employeeRepository.findByEmployeeCode(identifier)
            .or(() -> employeeRepository.findByEmail(identifier))
            .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException(
                "Employee profile not found for identifier: " + identifier));
    }
}