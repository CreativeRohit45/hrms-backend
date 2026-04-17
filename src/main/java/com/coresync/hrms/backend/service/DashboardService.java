package com.coresync.hrms.backend.service;

import com.coresync.hrms.backend.dto.DashboardStatsDTO;
import com.coresync.hrms.backend.entity.*;
import com.coresync.hrms.backend.enums.LeaveStatus;
import com.coresync.hrms.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayRepository holidayRepository;
    private final CompanyLocationRepository companyLocationRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;

    @Transactional(readOnly = true)
    public DashboardStatsDTO getDashboardStats(String employeeCode) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Employee not found"));

        CompanyLocation companyLocation = companyLocationRepository.findFirstByIsActiveTrueOrderByIdAsc()
            .orElseThrow(() -> new IllegalStateException("Active company location is not configured"));

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());

        // 1. Attendance for current month
        List<AttendanceLog> monthlyLogs = attendanceLogRepository.findClosedSessionsForPeriod(employee.getId(), monthStart, monthEnd);
        
        long present = 0;
        long late = 0;
        long leaveDays = 0;
        long totalWorkedMinutes = 0;
        
        if (monthlyLogs != null) {
            present = monthlyLogs.stream()
                .filter(l -> l.getAttendanceStatus() != null && 
                    (l.getAttendanceStatus().name().equals("PRESENT") || 
                     l.getAttendanceStatus().name().equals("LATE") ||
                     l.getAttendanceStatus().name().equals("HALF_DAY") ||
                     l.getAttendanceStatus().name().equals("WEEKEND_WORK") ||
                     l.getAttendanceStatus().name().equals("HOLIDAY_WORK")))
                .count();
            late = monthlyLogs.stream()
                .filter(l -> l.getAttendanceStatus() != null && (
                    l.getAttendanceStatus().name().equals("LATE") ||
                    (l.getAttendanceStatus().name().equals("HALF_DAY") && 
                     l.getPunchInTime() != null && 
                     l.getPunchInTime().toLocalTime().isAfter(l.getShift().getStartTime().plusMinutes(10)))
                ))
                .count();
            leaveDays = monthlyLogs.stream()
                .filter(l -> l.getAttendanceStatus() != null && l.getAttendanceStatus().name().equals("ON_LEAVE"))
                .count();
            
            totalWorkedMinutes = monthlyLogs.stream()
                .filter(l -> l.getAttendanceStatus() != null && 
                    (l.getAttendanceStatus().name().equals("PRESENT") || 
                     l.getAttendanceStatus().name().equals("LATE") ||
                     l.getAttendanceStatus().name().equals("HALF_DAY") ||
                     l.getAttendanceStatus().name().equals("WEEKEND_WORK") ||
                     l.getAttendanceStatus().name().equals("HOLIDAY_WORK")))
                .mapToLong(l -> l.getCalculatedPayableMinutes() != null ? l.getCalculatedPayableMinutes() : 0)
                .sum();
        }

        // Get all holidays for the current month to subtract from working days
        List<LocalDate> monthHolidays = holidayRepository.findAll().stream()
            .map(Holiday::getHolidayDate)
            .filter(d -> !d.isBefore(monthStart) && !d.isAfter(monthEnd))
            .collect(Collectors.toList());

        List<String> weekendList = java.util.Arrays.stream(companyLocation.getWeekendDays().split(","))
            .map(String::trim)
            .map(String::toUpperCase)
            .collect(Collectors.toList());

        // Get all approved leaves for the month for precise absence calculation
        List<LeaveRequest> monthlyLeave = leaveRequestRepository.findOverlapping(
                employee.getId(), monthStart, monthEnd, List.of(LeaveStatus.APPROVED));

        long absent = 0;
        for (int i = 1; i <= today.getDayOfMonth(); i++) {
            LocalDate d = today.withDayOfMonth(i);
            if (d.equals(today)) continue;
            
            boolean isWeekend = weekendList.contains(d.getDayOfWeek().name());
            boolean isHoliday = monthHolidays.contains(d);
            
            if (!isWeekend && !isHoliday) {
                boolean attendanceCovered = monthlyLogs != null && monthlyLogs.stream()
                        .anyMatch(l -> l.getWorkDate().equals(d) && 
                            l.getAttendanceStatus() != null &&
                            (l.getAttendanceStatus().name().equals("PRESENT") || 
                             l.getAttendanceStatus().name().equals("LATE") ||
                             l.getAttendanceStatus().name().equals("HALF_DAY") ||
                             l.getAttendanceStatus().name().equals("WEEKEND_WORK") ||
                             l.getAttendanceStatus().name().equals("HOLIDAY_WORK")));
                
                boolean leaveCovered = monthlyLeave.stream()
                        .anyMatch(lr -> !d.isBefore(lr.getStartDate()) && !d.isAfter(lr.getEndDate()));

                if (!attendanceCovered && !leaveCovered) {
                    absent++;
                }
            }
        }

        // 2. Leave Summary — now from LeaveBalance table (single source of truth)
        int year = today.getYear();
        List<LeaveBalance> balances = leaveBalanceRepository.findByEmployeeIdAndYear(employee.getId(), year);

        double totalAllocated = balances.stream().mapToDouble(LeaveBalance::getAllocated).sum();
        double totalUsed = balances.stream().mapToDouble(LeaveBalance::getUsed).sum();
        double totalRemaining = balances.stream().mapToDouble(LeaveBalance::getBalance).sum();

        List<DashboardStatsDTO.LeaveBalanceDetail> balanceDetails = balances.stream()
            .map(lb -> DashboardStatsDTO.LeaveBalanceDetail.builder()
                .leaveTypeName(lb.getLeaveType().getName())
                .leaveTypeCode(lb.getLeaveType().getCode())
                .allocated(lb.getAllocated())
                .used(lb.getUsed())
                .balance(lb.getBalance())
                .build())
            .collect(Collectors.toList());

        // 3. Current Session
        AttendanceLog openSession = attendanceLogRepository.findOpenSession(employee.getId()).orElse(null);
        DashboardStatsDTO.AttendanceSessionDTO sessionDTO = null;
        if (openSession != null) {
            sessionDTO = DashboardStatsDTO.AttendanceSessionDTO.builder()
                .id(openSession.getId())
                .punchInTime(openSession.getPunchInTime())
                .active(true)
                .build();
        }

        // 3b. Check if today's session is already complete
        boolean todayCompleted = attendanceLogRepository.existsByEmployeeIdAndWorkDateAndPunchOutTimeIsNotNull(employee.getId(), today);
        Integer todayTotalMinutes = null;
        if (todayCompleted) {
            AttendanceLog todayLog = attendanceLogRepository.findFirstByEmployeeIdAndWorkDateOrderByIdDesc(employee.getId(), today).orElse(null);
            if (todayLog != null) {
                todayTotalMinutes = todayLog.getCalculatedPayableMinutes();
            }
        }

        // 4. Upcoming Holidays
        List<DashboardStatsDTO.HolidayDTO> holidays = holidayRepository.findUpcomingHolidays(today).stream()
            .limit(5)
            .map(h -> DashboardStatsDTO.HolidayDTO.builder()
                .name(h.getName())
                .date(h.getHolidayDate())
                .description(h.getDescription())
                .build())
            .collect(Collectors.toList());

        // 5. Recent Logs (Last 5)
        List<DashboardStatsDTO.AttendanceLogDTO> recentLogs = attendanceLogRepository.findByEmployeeIdOrderByWorkDateDesc(employee.getId()).stream()
            .limit(5)
            .map(l -> DashboardStatsDTO.AttendanceLogDTO.builder()
                .date(l.getWorkDate())
                .status(l.getAttendanceStatus().name())
                .punchIn(l.getPunchInTime())
                .punchOut(l.getPunchOutTime())
                .build())
            .collect(Collectors.toList());

        // All Holiday Dates (for frontend stats)
        List<LocalDate> allHolidayDates = holidayRepository.findAll().stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toList());

        return DashboardStatsDTO.builder()
            .leavesTaken(BigDecimal.valueOf(totalUsed))
            .leavesRemaining(BigDecimal.valueOf(totalRemaining))
            .totalLeaveQuota(BigDecimal.valueOf(totalAllocated))
            .leaveBalances(balanceDetails)
            .presentDays(present)
            .lateDays(late)
            .absentDays(absent)
            .leaveDays(leaveDays)
            .totalWorkedMinutes(totalWorkedMinutes)
            .currentSession(sessionDTO)
            .todayCompleted(todayCompleted)
            .todayTotalMinutes(todayTotalMinutes)
            .weekendDays(companyLocation.getWeekendDays())
            .upcomingHolidays(holidays)
            .recentLogs(recentLogs)
            .allHolidays(allHolidayDates)
            .build();
    }
}
