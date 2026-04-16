// src/main/java/com/coresync/hrms/backend/service/PayrollCalculationService.java
package com.coresync.hrms.backend.service;

import com.coresync.hrms.backend.dto.PayrollResult;
import com.coresync.hrms.backend.entity.AttendanceLog;
import com.coresync.hrms.backend.entity.Employee;
import com.coresync.hrms.backend.entity.Gatepass;
import com.coresync.hrms.backend.enums.GatepassStatus;
import com.coresync.hrms.backend.enums.GatepassType;
import com.coresync.hrms.backend.repository.AttendanceLogRepository;
import com.coresync.hrms.backend.repository.EmployeeRepository;
import com.coresync.hrms.backend.repository.GatepassRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollCalculationService {

    private final AttendanceLogRepository attendanceLogRepository;
    private final GatepassRepository gatepassRepository;
    private final EmployeeRepository employeeRepository;

    private static final int MINUTES_PER_HOUR = 60;
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    @Transactional(readOnly = true)
    public PayrollResult calculateMonthlyPayroll(Integer employeeId, int payrollMonth, int payrollYear) {

        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + employeeId));

        BigDecimal hourlyRateSnapshot = employee.getHourlyRate();
        BigDecimal overtimeRateSnapshot = hourlyRateSnapshot.multiply(employee.getOvertimeRateMultiplier()).setScale(SCALE, ROUNDING);

        LocalDate periodStart = LocalDate.of(payrollYear, payrollMonth, 1);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());

        List<AttendanceLog> sessions = attendanceLogRepository.findClosedSessionsForPeriod(employeeId, periodStart, periodEnd);
        log.info("Payroll calculation for employee {} | Period: {} to {} | Sessions: {}", employee.getEmployeeCode(), periodStart, periodEnd, sessions.size());

        int totalRegularMinutes = 0;
        int totalOvertimeMinutes = 0;
        int totalDeductedMinutes = 0;
        int presentDays = 0, absentDays = 0, lateDays = 0, leaveDays = 0;

        for (AttendanceLog session : sessions) {
            int netSessionMinutes = session.getCalculatedPayableMinutes() != null ? session.getCalculatedPayableMinutes() : 0;
            int shiftStandardMinutes = (int)(session.getShift().getStandardHours().doubleValue() * 60);

            if (netSessionMinutes > shiftStandardMinutes) {
                totalRegularMinutes += shiftStandardMinutes;
                totalOvertimeMinutes += (netSessionMinutes - shiftStandardMinutes);
            } else {
                totalRegularMinutes += netSessionMinutes;
            }

            switch (session.getAttendanceStatus()) {
                case PRESENT  -> presentDays++;
                case LATE     -> { presentDays++; lateDays++; }
                case ABSENT   -> absentDays++;
                case ON_LEAVE -> leaveDays++;
                case HALF_DAY -> presentDays++;
                default       -> {} 
            }
        }

        int totalPayableMinutes = totalRegularMinutes + totalOvertimeMinutes;

        BigDecimal regularHours = BigDecimal.valueOf(totalRegularMinutes).divide(BigDecimal.valueOf(MINUTES_PER_HOUR), 4, ROUNDING);
        BigDecimal overtimeHours = BigDecimal.valueOf(totalOvertimeMinutes).divide(BigDecimal.valueOf(MINUTES_PER_HOUR), 4, ROUNDING);

        BigDecimal regularPay = regularHours.multiply(hourlyRateSnapshot).setScale(SCALE, ROUNDING);
        BigDecimal overtimePay = overtimeHours.multiply(overtimeRateSnapshot).setScale(SCALE, ROUNDING);
        
        // HRA and PF logic
        BigDecimal baseSalary = employee.getBaseSalary() != null ? employee.getBaseSalary() : BigDecimal.ZERO;
        BigDecimal hraPercentage = employee.getHraPercentage() != null ? employee.getHraPercentage() : new BigDecimal("40.0");
        BigDecimal pfPercentage = employee.getPfPercentage() != null ? employee.getPfPercentage() : new BigDecimal("12.0");

        BigDecimal hraLabel = baseSalary.multiply(hraPercentage).divide(new BigDecimal("100"), SCALE, ROUNDING);
        BigDecimal pfDeduction = baseSalary.multiply(pfPercentage).divide(new BigDecimal("100"), SCALE, ROUNDING);

        // --- DEDUCTION LOGIC: LWP & SHORTFALLS ---
        int lwpDays = 0;
        long totalShortfallMinutes = 0;
        int standardShiftMinutes = 480; // Default 8h, or fetch from shift

        for (AttendanceLog session : sessions) {
            if (session.getAttendanceStatus() == com.coresync.hrms.backend.enums.AttendanceStatus.ON_LEAVE) {
                lwpDays++;
            } else if (session.getAttendanceStatus() == com.coresync.hrms.backend.enums.AttendanceStatus.PRESENT || 
                       session.getAttendanceStatus() == com.coresync.hrms.backend.enums.AttendanceStatus.LATE) {
                
                int expectedMinutes = session.getShift() != null ? 
                    (int)(session.getShift().getStandardHours().doubleValue() * 60) : standardShiftMinutes;
                    
                int workedMinutes = session.getCalculatedPayableMinutes() != null ? session.getCalculatedPayableMinutes() : 0;
                
                if (workedMinutes < expectedMinutes) {
                    totalShortfallMinutes += (expectedMinutes - workedMinutes);
                }
            }
        }

        // Calculate Daily Rate dynamically for LWP
        int daysInMonth = YearMonth.of(payrollYear, payrollMonth).lengthOfMonth();
        BigDecimal dailyRate = baseSalary.divide(BigDecimal.valueOf(daysInMonth), 4, ROUNDING);
        BigDecimal lwpDeduction = dailyRate.multiply(BigDecimal.valueOf(lwpDays)).setScale(SCALE, ROUNDING);

        // Calculate Hourly Rate for Shortfall (Gatepass) Deductions
        BigDecimal hourlyRate = dailyRate.divide(BigDecimal.valueOf(8), 4, ROUNDING); // Assuming 8h standard day
        BigDecimal gatepassDeduction = hourlyRate.multiply(BigDecimal.valueOf(totalShortfallMinutes))
            .divide(BigDecimal.valueOf(60), 4, ROUNDING).setScale(SCALE, ROUNDING);

        // Final Gross Pay calculation
        // Total pay is calculated based on WORKED minutes (from loop) + HRA, minus PF.
        // But since regularPay already only includes worked minutes, we DO NOT deduct lwpDeduction if we use regularPay.
        // To stick to a "Salaried" model requested by user (Base - Penalties), we should use BaseSalary instead of regularPay.
        
        BigDecimal grossPayBeforeDeductions = baseSalary.add(hraLabel).add(overtimePay).setScale(SCALE, ROUNDING);
        BigDecimal netPay = grossPayBeforeDeductions.subtract(pfDeduction)
                                                  .subtract(lwpDeduction)
                                                  .subtract(gatepassDeduction)
                                                  .setScale(SCALE, ROUNDING);

        return PayrollResult.builder()
            .employeeId(employeeId).payrollMonth(payrollMonth).payrollYear(payrollYear)
            .totalPayableMinutes(totalPayableMinutes).totalOvertimeMinutes(totalOvertimeMinutes)
            .totalGatepassDeductedMinutes((int)totalShortfallMinutes)
            .hourlyRateSnapshot(hourlyRateSnapshot).overtimeRateSnapshot(overtimeRateSnapshot)
            .regularPay(regularPay).overtimePay(overtimePay).grossPay(grossPayBeforeDeductions)
            .deductionPf(pfDeduction)
            .allowanceHra(hraLabel)
            .deductionLwp(lwpDeduction)
            .netPay(netPay)
            .presentDays(presentDays).absentDays(absentDays).lateDays(lateDays).leaveDays(leaveDays)
            .build();
    }

    // Redundant gatepass calculation removed - now handled in AttendanceService.punchOut()
}