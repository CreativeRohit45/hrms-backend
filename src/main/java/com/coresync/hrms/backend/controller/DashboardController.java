package com.coresync.hrms.backend.controller;

import com.coresync.hrms.backend.dto.DashboardStatsDTO;
import com.coresync.hrms.backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/me")
    public ResponseEntity<DashboardStatsDTO> getMyDashboardStats(Authentication authentication) {
        String employeeCode = authentication.getName();
        return ResponseEntity.ok(dashboardService.getDashboardStats(employeeCode));
    }

    @GetMapping("/employee/{employeeCode}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN', 'DEPARTMENT_MANAGER')")
    public ResponseEntity<DashboardStatsDTO> getEmployeeDashboardStats(@org.springframework.web.bind.annotation.PathVariable String employeeCode) {
        return ResponseEntity.ok(dashboardService.getDashboardStats(employeeCode));
    }
}
