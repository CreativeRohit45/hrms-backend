// src/main/java/com/coresync/hrms/backend/service/PayrollPersistenceService.java
package com.coresync.hrms.backend.service;

import com.coresync.hrms.backend.dto.PayrollResult;
import com.coresync.hrms.backend.dto.PayslipResponse;
import com.coresync.hrms.backend.entity.Employee;
import com.coresync.hrms.backend.entity.PayrollRecord;
import com.coresync.hrms.backend.enums.PayrollStatus;
import com.coresync.hrms.backend.repository.EmployeeRepository;
import com.coresync.hrms.backend.repository.PayrollRecordRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Month;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollPersistenceService {

    private final PayrollCalculationService calculationService;
    private final PayrollRecordRepository payrollRepository;
    private final EmployeeRepository employeeRepository;

    private static final BigDecimal PF_RATE = new BigDecimal("0.12"); 
    private static final BigDecimal ESI_RATE = new BigDecimal("0.0075"); 
    private static final BigDecimal ESI_CEILING = new BigDecimal("21000");

    @Transactional
    public PayslipResponse runAndPersistPayroll(Integer employeeId, int month, int year, Integer adminId) {
        
        payrollRepository.findByEmployeeIdAndPayrollYearAndPayrollMonth(employeeId, year, month)
            .ifPresent(record -> {
                if (record.getStatus() == PayrollStatus.LOCKED || record.getStatus() == PayrollStatus.PAID) {
                    throw new IllegalStateException("Payroll for this period is already locked.");
                }
                payrollRepository.delete(record);
            });

        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        PayrollResult rawResult = calculationService.calculateMonthlyPayroll(employeeId, month, year);

        BigDecimal grossPay = rawResult.getGrossPay();
        BigDecimal basicPay = grossPay.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pfDeduction = basicPay.multiply(PF_RATE).setScale(2, RoundingMode.HALF_UP);
        
        BigDecimal esiDeduction = BigDecimal.ZERO;
        if (grossPay.compareTo(ESI_CEILING) <= 0) {
            esiDeduction = grossPay.multiply(ESI_RATE).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal tdsDeduction = grossPay.compareTo(new BigDecimal("50000")) > 0 
            ? grossPay.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP) 
            : BigDecimal.ZERO;

        BigDecimal totalDeductions = pfDeduction.add(esiDeduction).add(tdsDeduction);
        BigDecimal netPay = grossPay.subtract(totalDeductions);

        PayrollRecord record = PayrollRecord.builder()
            .employee(employee)
            .payrollMonth(month)
            .payrollYear(year)
            .totalPayableMinutes(rawResult.getTotalPayableMinutes())
            .totalOvertimeMinutes(rawResult.getTotalOvertimeMinutes())
            .hourlyRateSnapshot(rawResult.getHourlyRateSnapshot())
            .overtimeRateSnapshot(rawResult.getOvertimeRateSnapshot())
            .grossPay(grossPay)
            .overtimePay(rawResult.getOvertimePay())
            .deductionPf(pfDeduction)
            .deductionEsi(esiDeduction)
            .deductionTds(tdsDeduction)
            .deductionAdvance(BigDecimal.ZERO)
            .deductionOther(BigDecimal.ZERO)
            .totalDeductions(totalDeductions)
            .netPay(netPay)
            .presentDays(rawResult.getPresentDays())
            .absentDays(rawResult.getAbsentDays())
            .lateDays(rawResult.getLateDays())
            .leaveDays(rawResult.getLeaveDays())
            .status(PayrollStatus.PROCESSED)
            .processedByUserId(adminId)
            .processedAt(LocalDateTime.now())
            .build();

        PayrollRecord saved = payrollRepository.save(record);
        log.info("[Payroll] Successfully persisted {} for {} | Net: {}", Month.of(month).name(), employee.getEmployeeCode(), netPay);

        return toPayslipResponse(saved);
    }

    @Transactional(readOnly = true)
    public PayslipResponse getPayslip(Integer recordId) {
        PayrollRecord record = payrollRepository.findById(recordId)
            .orElseThrow(() -> new EntityNotFoundException("Payroll record not found"));
        return toPayslipResponse(record);
    }

    @Transactional(readOnly = true)
    public List<com.coresync.hrms.backend.dto.PayslipResponse> getCompanyPayroll(int month, int year) {
        return payrollRepository.findByPayrollYearAndPayrollMonth(year, month).stream()
            .map(this::toPayslipResponse)
            .toList();
    }

    @Transactional
    public void runBulkPayroll(int month, int year, Integer adminId) {
        // Business Rule: Check if period is already locked
        boolean isLocked = payrollRepository.findByPayrollYearAndPayrollMonth(year, month).stream()
            .anyMatch(r -> r.getStatus() == PayrollStatus.LOCKED || r.getStatus() == PayrollStatus.PAID);
        
        if (isLocked) {
            throw new IllegalStateException("Cannot run payroll: This period is already LOCKED and finalized.");
        }

        List<Employee> actives = employeeRepository.findByStatus(com.coresync.hrms.backend.enums.EmployeeStatus.ACTIVE);
        log.info("[Payroll] Initializing bulk run for {} employees | Period: {}/{}", actives.size(), month, year);

        for (Employee emp : actives) {
            try {
                runAndPersistPayroll(emp.getId(), month, year, adminId);
            } catch (Exception e) {
                log.error("[Payroll] Failed for {}: {}", emp.getEmployeeCode(), e.getMessage());
            }
        }
    }

    @Transactional
    public void lockPayroll(int month, int year) {
        List<PayrollRecord> records = payrollRepository.findByPayrollYearAndPayrollMonth(year, month);
        if (records.isEmpty()) {
            throw new EntityNotFoundException("No payroll records found for the specified period to lock.");
        }
        records.forEach(r -> {
            if (r.getStatus() == PayrollStatus.PROCESSED) {
                r.setStatus(PayrollStatus.LOCKED);
            }
        });
        payrollRepository.saveAll(records);
        log.info("[Payroll] Period {}/{} has been LOCKED for disbursement.", month, year);
    }

    @Transactional(readOnly = true)
    public List<com.coresync.hrms.backend.dto.PayslipResponse> getEmployeeHistory(Integer employeeId) {
        return payrollRepository.findByEmployeeIdOrderByPayrollYearDescPayrollMonthDesc(employeeId).stream()
            .map(this::toPayslipResponse)
            .toList();
    }

    private PayslipResponse toPayslipResponse(PayrollRecord record) {
        String periodName = Month.of(record.getPayrollMonth()).name() + " " + record.getPayrollYear();
        
        return PayslipResponse.builder()
            .recordId(record.getId())
            .employeeCode(record.getEmployee().getEmployeeCode())
            .fullName(record.getEmployee().getFullName())
            .departmentName(record.getEmployee().getDepartment().getName())
            .designation(record.getEmployee().getDesignation())
            .period(periodName)
            .presentDays(record.getPresentDays())
            .absentDays(record.getAbsentDays())
            .leaveDays(record.getLeaveDays())
            .grossPay(record.getGrossPay())
            .deductionPf(record.getDeductionPf())
            .deductionEsi(record.getDeductionEsi())
            .deductionTds(record.getDeductionTds())
            .totalDeductions(record.getTotalDeductions())
            .netPay(record.getNetPay())
            .status(record.getStatus().name())
            .build();
    }
}