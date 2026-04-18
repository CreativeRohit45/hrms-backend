// src/main/java/com/coresync/hrms/backend/service/PayrollCalculationService.java
package com.coresync.hrms.backend.service;

import com.coresync.hrms.backend.dto.PayrollResult;
import com.coresync.hrms.backend.entity.AttendanceLog;
import com.coresync.hrms.backend.entity.Employee;
import com.coresync.hrms.backend.entity.LeaveRequest;
import com.coresync.hrms.backend.enums.AttendanceStatus;
import com.coresync.hrms.backend.enums.LeaveStatus;
import com.coresync.hrms.backend.repository.AttendanceLogRepository;
import com.coresync.hrms.backend.repository.EmployeeRepository;
import com.coresync.hrms.backend.repository.LeaveRequestRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * PayrollCalculationService — Pure, deterministic payroll math.
 * <p>
 * This service takes an employee's attendance data for a calendar month and
 * produces a {@link PayrollResult} containing all monetary values. It does NOT
 * persist anything; that responsibility belongs to
 * {@link PayrollPersistenceService}.
 * <p>
 * <b>Financial Integrity Rules:</b>
 * <ol>
 *   <li>All currency values use {@link BigDecimal} — never {@code double}.</li>
 *   <li>Only <b>unpaid</b> leaves (LeaveType.isPaid == false) are deducted as LWP.</li>
 *   <li>Personal gatepass minutes are already deducted from
 *       {@code calculatedPayableMinutes} at punch-out by {@link AttendanceService}.
 *       This service does <b>NOT</b> re-deduct them.</li>
 *   <li>Shift-specific {@code standardHours} is used for all rate calculations
 *       — never a hardcoded 8-hour assumption.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollCalculationService {

    private final AttendanceLogRepository attendanceLogRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;
    private static final int INTERMEDIATE_SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    @Transactional(readOnly = true)
    public PayrollResult calculateMonthlyPayroll(Integer employeeId, int payrollMonth, int payrollYear) {

        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + employeeId));

        // ── Snapshot employee rates at calculation time ──────────────
        BigDecimal hourlyRateSnapshot = employee.getHourlyRate();
        BigDecimal overtimeRateSnapshot = hourlyRateSnapshot
            .multiply(employee.getOvertimeRateMultiplier())
            .setScale(SCALE, ROUNDING);

        // ── Period boundaries ────────────────────────────────────────
        LocalDate periodStart = LocalDate.of(payrollYear, payrollMonth, 1);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());

        // ── Fetch all closed attendance sessions for the period ──────
        List<AttendanceLog> sessions = attendanceLogRepository
            .findClosedSessionsForPeriod(employeeId, periodStart, periodEnd);
        log.info("Payroll calculation for employee {} | Period: {} to {} | Sessions: {}",
            employee.getEmployeeCode(), periodStart, periodEnd, sessions.size());

        // ═══════════════════════════════════════════════════════════
        //  PASS 1: Accumulate minutes and day-counts from sessions
        // ═══════════════════════════════════════════════════════════
        int totalRegularMinutes = 0;
        int totalOvertimeMinutes = 0;
        int presentDays = 0, absentDays = 0, lateDays = 0, leaveDays = 0;

        for (AttendanceLog session : sessions) {
            int netSessionMinutes = session.getCalculatedPayableMinutes() != null
                ? session.getCalculatedPayableMinutes() : 0;

            // BUG #1 FIX: Pure BigDecimal conversion — no doubleValue() truncation
            int shiftStandardMinutes = session.getShift().getStandardHours()
                .multiply(MINUTES_PER_HOUR)
                .setScale(0, ROUNDING)
                .intValue();

            if (netSessionMinutes > shiftStandardMinutes) {
                totalRegularMinutes += shiftStandardMinutes;
                totalOvertimeMinutes += (netSessionMinutes - shiftStandardMinutes);
            } else {
                totalRegularMinutes += netSessionMinutes;
            }

            switch (session.getAttendanceStatus()) {
                case PRESENT      -> presentDays++;
                case LATE         -> { presentDays++; lateDays++; }
                case ABSENT       -> absentDays++;
                case ON_LEAVE     -> leaveDays++;
                case HALF_DAY     -> presentDays++;
                case HOLIDAY_WORK -> presentDays++;
                case WEEKEND_WORK -> presentDays++;
                default           -> {}
            }
        }

        int totalPayableMinutes = totalRegularMinutes + totalOvertimeMinutes;

        // ── Compute hours-based pay ──────────────────────────────────
        BigDecimal regularHours = BigDecimal.valueOf(totalRegularMinutes)
            .divide(MINUTES_PER_HOUR, INTERMEDIATE_SCALE, ROUNDING);
        BigDecimal overtimeHours = BigDecimal.valueOf(totalOvertimeMinutes)
            .divide(MINUTES_PER_HOUR, INTERMEDIATE_SCALE, ROUNDING);

        BigDecimal regularPay = regularHours.multiply(hourlyRateSnapshot).setScale(SCALE, ROUNDING);
        BigDecimal overtimePay = overtimeHours.multiply(overtimeRateSnapshot).setScale(SCALE, ROUNDING);

        // ═══════════════════════════════════════════════════════════
        //  PASS 2: Salaried model — Base + HRA - Deductions
        // ═══════════════════════════════════════════════════════════
        BigDecimal baseSalary = employee.getBaseSalary() != null
            ? employee.getBaseSalary() : BigDecimal.ZERO;
        BigDecimal hraPercentage = employee.getHraPercentage() != null
            ? employee.getHraPercentage() : new BigDecimal("40.0");
        BigDecimal pfPercentage = employee.getPfPercentage() != null
            ? employee.getPfPercentage() : new BigDecimal("12.0");

        BigDecimal hraAllowance = baseSalary.multiply(hraPercentage)
            .divide(ONE_HUNDRED, SCALE, ROUNDING);
        BigDecimal pfDeduction = baseSalary.multiply(pfPercentage)
            .divide(ONE_HUNDRED, SCALE, ROUNDING);

        // ═══════════════════════════════════════════════════════════
        //  BUG #2 FIX: LWP Deduction — Only for UNPAID leave types
        //
        //  We cross-reference the LeaveRequest table to find approved
        //  leaves that overlap with this payroll period. Only leaves
        //  where leaveType.isPaid() == false contribute to LWP days.
        // ═══════════════════════════════════════════════════════════
        List<LeaveRequest> approvedLeaves = leaveRequestRepository.findOverlapping(
            employeeId, periodStart, periodEnd,
            List.of(LeaveStatus.APPROVED)
        );

        double lwpDays = 0;
        for (LeaveRequest leave : approvedLeaves) {
            if (!leave.getLeaveType().isPaid()) {
                // Clamp the leave dates to the payroll period boundaries
                LocalDate effectiveStart = leave.getStartDate().isBefore(periodStart)
                    ? periodStart : leave.getStartDate();
                LocalDate effectiveEnd = leave.getEndDate().isAfter(periodEnd)
                    ? periodEnd : leave.getEndDate();

                long daysInPeriod = ChronoUnit.DAYS.between(effectiveStart, effectiveEnd) + 1;

                // Half-day leaves count as 0.5
                if (leave.isHalfDay() && daysInPeriod == 1) {
                    lwpDays += 0.5;
                } else {
                    lwpDays += daysInPeriod;
                }

                log.info("[Payroll] LWP detected: LeaveType={}, Days={}, HalfDay={}",
                    leave.getLeaveType().getName(), daysInPeriod, leave.isHalfDay());
            }
        }

        int daysInMonth = YearMonth.of(payrollYear, payrollMonth).lengthOfMonth();
        BigDecimal dailyRate = baseSalary.divide(BigDecimal.valueOf(daysInMonth), INTERMEDIATE_SCALE, ROUNDING);
        BigDecimal lwpDeduction = dailyRate.multiply(BigDecimal.valueOf(lwpDays)).setScale(SCALE, ROUNDING);

        // ═══════════════════════════════════════════════════════════
        //  BUG #3 FIX: No shortfall/gatepass deduction here.
        //
        //  Personal gatepass minutes are ALREADY deducted from
        //  calculatedPayableMinutes by AttendanceService.punchOut().
        //  Re-computing shortfall here would double-count them.
        //  The variable totalGatepassDeductedMinutes is set to 0
        //  because this service no longer performs that deduction.
        // ═══════════════════════════════════════════════════════════

        // ── Final pay formula: Salaried model ────────────────────────
        BigDecimal grossPayBeforeDeductions = baseSalary
            .add(hraAllowance)
            .add(overtimePay)
            .setScale(SCALE, ROUNDING);

        BigDecimal netPay = grossPayBeforeDeductions
            .subtract(pfDeduction)
            .subtract(lwpDeduction)
            .setScale(SCALE, ROUNDING);

        log.info("[Payroll] {} | Base={} HRA={} OT={} Gross={} | PF={} LWP={} ({}d) | Net={}",
            employee.getEmployeeCode(), baseSalary, hraAllowance, overtimePay,
            grossPayBeforeDeductions, pfDeduction, lwpDeduction, lwpDays, netPay);

        return PayrollResult.builder()
            .employeeId(employeeId)
            .payrollMonth(payrollMonth)
            .payrollYear(payrollYear)
            .totalPayableMinutes(totalPayableMinutes)
            .totalOvertimeMinutes(totalOvertimeMinutes)
            .totalGatepassDeductedMinutes(0)
            .hourlyRateSnapshot(hourlyRateSnapshot)
            .overtimeRateSnapshot(overtimeRateSnapshot)
            .regularPay(regularPay)
            .overtimePay(overtimePay)
            .grossPay(grossPayBeforeDeductions)
            .deductionPf(pfDeduction)
            .allowanceHra(hraAllowance)
            .deductionLwp(lwpDeduction)
            .netPay(netPay)
            .presentDays(presentDays)
            .absentDays(absentDays)
            .lateDays(lateDays)
            .leaveDays(leaveDays)
            .build();
    }
}