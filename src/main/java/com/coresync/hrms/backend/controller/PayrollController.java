// src/main/java/com/coresync/hrms/backend/controller/PayrollController.java
package com.coresync.hrms.backend.controller;

import com.coresync.hrms.backend.dto.BulkPayrollResponse;
import com.coresync.hrms.backend.dto.PayslipResponse;
import com.coresync.hrms.backend.entity.Employee;
import com.coresync.hrms.backend.enums.PayrollAdjustmentType;
import com.coresync.hrms.backend.repository.EmployeeRepository;
import com.coresync.hrms.backend.service.PayrollAdjustmentService;
import com.coresync.hrms.backend.service.PayrollPersistenceService;
import com.coresync.hrms.backend.service.PdfService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollPersistenceService payrollPersistenceService;
    private final PayrollAdjustmentService adjustmentService;
    private final PdfService pdfService;
    private final EmployeeRepository employeeRepository;

    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<PayslipResponse> runPayroll(
            @RequestParam Integer employeeId,
            @RequestParam int month,
            @RequestParam int year,
            Authentication authentication) {

        Integer adminId = resolveId(authentication);
        PayslipResponse response = payrollPersistenceService.runAndPersistPayroll(employeeId, month, year, adminId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/run-bulk")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<BulkPayrollResponse> runBulk(
            @RequestParam int month,
            @RequestParam int year,
            Authentication authentication) {
        Integer adminId = resolveId(authentication);
        BulkPayrollResponse result = payrollPersistenceService.runBulkPayroll(month, year, adminId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/lock")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> lockPayroll(@RequestParam int month, @RequestParam int year) {
        payrollPersistenceService.lockPayroll(month, year);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/records/{id}/recalculate")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> recalculate(@PathVariable Integer id) {
        adjustmentService.recalculateRecord(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/records/{id}/adjustments")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> addAdjustment(
            @PathVariable Integer id,
            @RequestParam PayrollAdjustmentType type,
            @RequestParam BigDecimal amount,
            @RequestParam String description,
            Principal principal) {
        adjustmentService.addAdjustment(id, type, amount, description, principal.getName());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/adjustments/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteAdjustment(@PathVariable Long id) {
        adjustmentService.deleteAdjustment(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/company-payroll")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<PayslipResponse>> getCompanyPayroll(
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(payrollPersistenceService.getCompanyPayroll(month, year));
    }

    @GetMapping("/my")
    public ResponseEntity<List<PayslipResponse>> getMyHistory(Authentication authentication) {
        Integer employeeId = resolveId(authentication);
        return ResponseEntity.ok(payrollPersistenceService.getEmployeeHistory(employeeId));
    }

    @GetMapping("/payslip/{recordId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN', 'EMPLOYEE')")
    public ResponseEntity<PayslipResponse> getPayslip(@PathVariable Integer recordId) {
        return ResponseEntity.ok(payrollPersistenceService.getPayslip(recordId));
    }

    @GetMapping("/payslip/{recordId}/download")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN', 'EMPLOYEE')")
    public ResponseEntity<byte[]> downloadPayslipPdf(@PathVariable Integer recordId) {
        PayslipResponse payslip = payrollPersistenceService.getPayslip(recordId);
        byte[] pdfBytes = pdfService.generatePayslipPdf(payslip);

        String filename = String.format("Payslip_%s_%s.pdf", payslip.getEmployeeCode(), payslip.getPeriod().replace(" ", "_"));

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdfBytes);
    }

    private Integer resolveId(Authentication authentication) {
        String identifier = authentication.getName();
        return employeeRepository.findByEmployeeCode(identifier)
            .or(() -> employeeRepository.findByEmail(identifier))
            .map(Employee::getId)
            .orElseThrow(() -> new EntityNotFoundException("Employee profile not found for identifier: " + identifier));
    }
}