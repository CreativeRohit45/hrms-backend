// src/main/java/com/coresync/hrms/backend/controller/EmployeeController.java
package com.coresync.hrms.backend.controller;

import com.coresync.hrms.backend.dto.EmployeeCreateRequest;
import com.coresync.hrms.backend.dto.EmployeeResponse;
import com.coresync.hrms.backend.dto.ProfileUpdateRequest;
import com.coresync.hrms.backend.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<EmployeeResponse> createEmployee(@Valid @RequestBody EmployeeCreateRequest request) {
        EmployeeResponse response = employeeService.createEmployee(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN', 'DEPARTMENT_MANAGER')")
    public ResponseEntity<Page<EmployeeResponse>> getAllEmployees(
            @RequestParam(required = false) Integer deptId,
            Pageable pageable,
            Authentication authentication) {
        String requesterCode = authentication.getName();
        return ResponseEntity.ok(employeeService.getAllEmployees(requesterCode, deptId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN', 'DEPARTMENT_MANAGER')")
    public ResponseEntity<EmployeeResponse> getEmployee(@PathVariable Integer id) {
        return ResponseEntity.ok(employeeService.getEmployeeById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN', 'DEPARTMENT_MANAGER')")
    public ResponseEntity<EmployeeResponse> updateEmployee(@PathVariable Integer id, @Valid @RequestBody com.coresync.hrms.backend.dto.EmployeeUpdateRequest request) {
        EmployeeResponse response = employeeService.updateEmployee(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<EmployeeResponse> getMyProfile(Authentication authentication) {
        String employeeCode = authentication.getName();
        return ResponseEntity.ok(employeeService.getEmployeeByCode(employeeCode));
    }

    @PatchMapping("/me")
    public ResponseEntity<EmployeeResponse> updateMyProfile(Authentication authentication, @RequestBody ProfileUpdateRequest request) {
        String employeeCode = authentication.getName();
        return ResponseEntity.ok(employeeService.updateMyProfile(employeeCode, request));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(Authentication authentication, @Valid @RequestBody com.coresync.hrms.backend.dto.ChangePasswordRequest request) {
        String employeeCode = authentication.getName();
        employeeService.changePassword(employeeCode, request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Integer id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }
}