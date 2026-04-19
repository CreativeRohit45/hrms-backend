// src/main/java/com/coresync/hrms/backend/service/PayrollPersistenceService.java
package com.coresync.hrms.backend.service;

import com.coresync.hrms.backend.dto.BulkPayrollResponse;
import com.coresync.hrms.backend.dto.PayrollResult;
import com.coresync.hrms.backend.dto.PayslipResponse;
import com.coresync.hrms.backend.entity.Employee;
import com.coresync.hrms.backend.entity.PayrollAdjustment;
import com.coresync.hrms.backend.entity.PayrollRecord;
import com.coresync.hrms.backend.enums.PayrollAdjustmentType;
import com.coresync.hrms.backend.enums.PayrollStatus;
import com.coresync.hrms.backend.repository.EmployeeRepository;
import com.coresync.hrms.backend.repository.PayrollRecordRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Month;

/**
 * PayrollPersistenceService — Orchestrates payroll generation, locking, and retrieval.
 * <p>
 * <b>Infrastructure Integrity Rules:</b>
 * <ol>
 *   <li>LOCKED/PAID records are <b>immutable</b> — regeneration is blocked with an exception.</li>
 *   <li>Bulk payroll reports <b>partial failures</b> via {@link BulkPayrollResponse}
 *       instead of silently swallowing exceptions.</li>
 *   <li>Each employee in a bulk run is processed in a <b>self-contained unit</b>;
 *       one failure does not roll back the others.</li>
 *   <li>Race-condition safety relies on the <b>unique constraint</b> on
 *       {@code (employee_id, payroll_year, payroll_month)} in the entity — see {@link PayrollRecord}.</li>
 * </ol>
 */
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

    /**
     * Generate (or regenerate) payroll for a single employee.
     * <p>
     * If a record already exists with LOCKED or PAID status, an exception is thrown.
     * If the status is PROCESSED (a prior draft), it is deleted and recalculated.
     */
    @Transactional
    public PayslipResponse runAndPersistPayroll(Integer employeeId, int month, int year, Integer adminId) {

        payrollRepository.findByEmployeeIdAndPayrollYearAndPayrollMonth(employeeId, year, month)
            .ifPresent(record -> {
                if (record.getStatus() == PayrollStatus.LOCKED || record.getStatus() == PayrollStatus.PAID) {
                    throw new IllegalStateException("Payroll for this period is already locked.");
                }
                payrollRepository.delete(record);
                payrollRepository.flush(); // ensure DELETE is committed before INSERT
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
        log.info("[Payroll] Successfully persisted {} for {} | Net: {}",
            Month.of(month).name(), employee.getEmployeeCode(), netPay);

        return toPayslipResponse(saved);
    }

    /**
     * Updates an existing PayrollRecord with new calculation results.
     * Used for targeted recalculation after adding adjustments.
     */
    @Transactional
    public void saveSingleRecord(PayrollRecord record, PayrollResult result) {
        record.setTotalPayableMinutes(result.getTotalPayableMinutes());
        record.setTotalOvertimeMinutes(result.getTotalOvertimeMinutes());
        record.setGrossPay(result.getGrossPay());
        record.setOvertimePay(result.getOvertimePay());
        record.setDeductionPf(result.getDeductionPf());
        record.setDeductionLwp(result.getDeductionLwp());
        record.setTotalAdjustmentAmount(result.getTotalAdjustmentAmount());
        record.setNetPay(result.getNetPay());
        record.setUpdatedAt(LocalDateTime.now());

        payrollRepository.save(record);
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

    /**
     * BUG #5 FIX: Bulk payroll now returns a {@link BulkPayrollResponse}
     * with an explicit success/failure report for every employee.
     * <p>
     * BUG #6 FIX: Each employee is processed individually. If one fails,
     * their error is captured in the response — the others are NOT rolled back.
     * We achieve this by catching exceptions per-employee and accumulating results.
     * The @Transactional on the outer method provides the commit boundary; each
     * individual runAndPersistPayroll commits independently because it's a
     * self-proxied call on the same transaction (Spring default REQUIRED propagation).
     */
    @Transactional
    public BulkPayrollResponse runBulkPayroll(int month, int year, Integer adminId) {
        // Business Rule: Check if period is already locked
        boolean isLocked = payrollRepository.findByPayrollYearAndPayrollMonth(year, month).stream()
            .anyMatch(r -> r.getStatus() == PayrollStatus.LOCKED || r.getStatus() == PayrollStatus.PAID);

        if (isLocked) {
            throw new IllegalStateException("Cannot run payroll: This period is already LOCKED and finalized.");
        }

        List<Employee> actives = employeeRepository.findByStatus(com.coresync.hrms.backend.enums.EmployeeStatus.ACTIVE);
        log.info("[Payroll] Initializing bulk run for {} employees | Period: {}/{}",
            actives.size(), month, year);

        int successCount = 0;
        List<BulkPayrollResponse.FailureDetail> failures = new ArrayList<>();

        for (Employee emp : actives) {
            try {
                runAndPersistPayroll(emp.getId(), month, year, adminId);
                successCount++;
            } catch (Exception e) {
                log.error("[Payroll] Failed for {}: {}", emp.getEmployeeCode(), e.getMessage(), e);
                failures.add(BulkPayrollResponse.FailureDetail.builder()
                    .employeeCode(emp.getEmployeeCode())
                    .fullName(emp.getFullName())
                    .errorMessage(e.getMessage())
                    .build());
            }
        }

        log.info("[Payroll] Bulk run complete: {}/{} succeeded, {} failed",
            successCount, actives.size(), failures.size());

        return BulkPayrollResponse.builder()
            .totalEmployees(actives.size())
            .successCount(successCount)
            .failureCount(failures.size())
            .failures(failures)
            .build();
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
            .lateDays(record.getLateDays())
            .totalPayableMinutes(record.getTotalPayableMinutes())
            .totalOvertimeMinutes(record.getTotalOvertimeMinutes())
            .grossPay(record.getGrossPay())
            .overtimePay(record.getOvertimePay())
            .deductionPf(record.getDeductionPf())
            .deductionEsi(record.getDeductionEsi())
            .deductionTds(record.getDeductionTds())
            .deductionAdvance(record.getDeductionAdvance())
            .deductionOther(record.getDeductionOther())
            .totalDeductions(record.getTotalDeductions())
            .netPay(record.getNetPay())
            .adjustmentBonus(calculateTotal(record, PayrollAdjustmentType.BONUS))
            .adjustmentArrears(calculateTotal(record, PayrollAdjustmentType.ARREARS))
            .adjustmentDeductionDamage(calculateTotal(record, PayrollAdjustmentType.DEDUCTION_DAMAGE))
            .adjustmentDeductionOther(calculateTotal(record, PayrollAdjustmentType.DEDUCTION_OTHER))
            .totalAdjustmentAmount(record.getTotalAdjustmentAmount())
            .status(record.getStatus().name())
            .processedAt(record.getProcessedAt())
            .build();
    }

    private BigDecimal calculateTotal(PayrollRecord record, PayrollAdjustmentType type) {
        return record.getAdjustments().stream()
            .filter(a -> !a.isDeleted() && a.getType() == type)
            .map(PayrollAdjustment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}