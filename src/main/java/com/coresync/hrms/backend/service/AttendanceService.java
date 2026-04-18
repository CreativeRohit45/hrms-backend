package com.coresync.hrms.backend.service;

import com.coresync.hrms.backend.dto.CorrectionRequest;
import com.coresync.hrms.backend.dto.PunchInRequest;
import com.coresync.hrms.backend.entity.AttendanceLog;
import com.coresync.hrms.backend.entity.CompanyLocation;
import com.coresync.hrms.backend.entity.Employee;
import com.coresync.hrms.backend.enums.AttendanceStatus;
import com.coresync.hrms.backend.enums.CorrectionStatus;
import com.coresync.hrms.backend.enums.EmployeeStatus;
import com.coresync.hrms.backend.exception.LocationVerificationException;
import com.coresync.hrms.backend.exception.OpenSessionException;
import com.coresync.hrms.backend.repository.AttendanceLogRepository;
import com.coresync.hrms.backend.repository.EmployeeRepository;
import com.coresync.hrms.backend.repository.HolidayRepository;
import com.coresync.hrms.backend.enums.EmployeeRole;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceService {

    private final AttendanceLogRepository attendanceLogRepository;
    private final EmployeeRepository employeeRepository;
    private final HolidayRepository holidayRepository;
    private final com.coresync.hrms.backend.repository.LeaveRequestRepository leaveRequestRepository;
    private final com.coresync.hrms.backend.repository.GatepassRepository gatepassRepository;
    private final SystemService systemService;

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;


    @Transactional
    public AttendanceLog punchIn(Integer employeeId, Double latitude, Double longitude) {

        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        CompanyLocation location = employee.getLocation();
        boolean locationVerified = isWithinGeofence(latitude, longitude, location);
        if (!locationVerified) {
            log.warn("Punch-IN geofence FAILED for employee ID {} — continuing execution, but with flag=false", employeeId);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDate todayIst = now.toLocalDate();

        Optional<AttendanceLog> openSession = attendanceLogRepository.findOpenSession(employee.getId());
        if (openSession.isPresent()) {
            log.warn("Punch-in BLOCKED for employee {}: open session ID {}",
                employee.getEmployeeCode(), openSession.get().getId());
            throw new OpenSessionException(openSession.get().getId());
        }

        boolean alreadyCompletedToday = attendanceLogRepository
            .existsByEmployeeIdAndWorkDateAndPunchOutTimeIsNotNull(employee.getId(), todayIst);
        if (alreadyCompletedToday) {
            log.warn("Punch-in BLOCKED for employee {}: completed session already exists for {}",
                employee.getEmployeeCode(), todayIst);
            throw new IllegalStateException(
                "You have already completed your attendance for today (" + todayIst + "). "
                + "Use a gate pass for temporary exits.");
        }

        AttendanceStatus status = resolveAttendanceStatus(employee, now);

        AttendanceLog logEntity = AttendanceLog.builder()
            .employee(employee)
            .shift(employee.getShift())
            .location(location)
            .punchInTime(now)
            .punchOutTime(null)
            .isLocationVerifiedIn(true)
            .isLocationVerifiedOut(null)
            .calculatedPayableMinutes(null)
            .isOvertime(false)
            .overtimeMinutes(0)
            .attendanceStatus(status)
            .workDate(todayIst)
            .isManuallyCorrected(false)
            .correctionReason(null)
            .correctedByUserId(null)
            .build();

        AttendanceLog saved = attendanceLogRepository.save(logEntity);
        log.info("Punch-IN recorded for employee {} at {} | Status: {} | LocationVerified: true",
            employee.getEmployeeCode(), now, status);

        return saved;
    }

    @Transactional
    public AttendanceLog punchOut(Integer employeeId, Double latitude, Double longitude) {

        AttendanceLog openLog = attendanceLogRepository.findOpenSession(employeeId)
            .orElseThrow(() -> new IllegalStateException(
                "No open punch-in session found for employee: " + employeeId));

        CompanyLocation location = openLog.getLocation();
        boolean locationVerified = isWithinGeofence(latitude, longitude, location);

        if (!locationVerified) {
            log.warn("Punch-OUT geofence FAILED for employee ID {} — recording with flag=false", employeeId);
        }

        LocalDateTime now = LocalDateTime.now();

        long grossMinutes = ChronoUnit.MINUTES.between(openLog.getPunchInTime(), now);
        
        // Fix 2: "Unpaid Break" Robbery - Only deduct if worked >= 5 hours (300 mins)
        int unpaidBreakMinutes = 0;
        if (grossMinutes >= 300) {
            unpaidBreakMinutes = (int) openLog.getShift().getUnpaidBreakMinutes();
        }
        long payableMinutes = Math.max(0, grossMinutes - unpaidBreakMinutes);

        // --- GATEPASS DEDUCTION INTEGRATION ---
        // Rule: Only PERSONAL gatepasses deduct from payable minutes.
        List<com.coresync.hrms.backend.entity.Gatepass> personalGatepasses = 
            gatepassRepository.findCompletedByLogAndType(openLog.getId(), com.coresync.hrms.backend.enums.GatepassType.PERSONAL);
        
        long gatepassMinutes = personalGatepasses.stream()
            .mapToLong(g -> {
                // FALLBACK: If employee forgot to 'Mark Entry' before punch-out, use punch-out time
                java.time.LocalDateTime returnTime = g.getActualInTime() != null ? g.getActualInTime() : now;
                return java.time.temporal.ChronoUnit.MINUTES.between(g.getActualOutTime(), returnTime);
            })
            .sum();
            
        if (gatepassMinutes > 0) {
            log.info("[AttendanceService] Deducting {} minutes for PERSONAL gatepasses from employee {}", 
                gatepassMinutes, openLog.getEmployee().getEmployeeCode());
            payableMinutes = Math.max(0, payableMinutes - gatepassMinutes);
        }

        // Fix 1: "Night Shift" Overtime Bug - Detect crossing midnight
        LocalTime shiftStartTime = openLog.getShift().getStartTime();
        LocalTime shiftEndTime = openLog.getShift().getEndTime();
        LocalDateTime shiftEndDateTime = openLog.getWorkDate().atTime(shiftEndTime);
        
        if (shiftEndTime.isBefore(shiftStartTime) || openLog.getShift().isOvernight()) {
            shiftEndDateTime = shiftEndDateTime.plusDays(1);
        }

        // Fix 3: "Late Overtime" Loophole - Only if after shift end AND exceeds standard hours
        long standardMinutes = (long) (openLog.getShift().getStandardHours().doubleValue() * 60);
        boolean isOvertime = now.isAfter(shiftEndDateTime) && payableMinutes > standardMinutes;
        
        int overtimeMinutes = 0;
        if (isOvertime) {
            LocalDateTime overtimeStart = openLog.getPunchInTime().isAfter(shiftEndDateTime) 
                ? openLog.getPunchInTime() 
                : shiftEndDateTime;
            overtimeMinutes = (int) Math.max(0, ChronoUnit.MINUTES.between(overtimeStart, now));
        }

        openLog.setPunchOutTime(now);
        openLog.setIsLocationVerifiedOut(locationVerified);
        openLog.setCalculatedPayableMinutes((int) payableMinutes);
        openLog.setOvertime(isOvertime);
        openLog.setOvertimeMinutes(overtimeMinutes);

        // Fix 5: Missing "Half-Day" Status Assignment
        if (payableMinutes < (standardMinutes / 2.0)) {
            log.info("Status OVERWRITE: Setting HALF_DAY for employee {} (Worked {}m < Required {}m/2)", 
                openLog.getEmployee().getEmployeeCode(), payableMinutes, standardMinutes);
            openLog.setAttendanceStatus(AttendanceStatus.HALF_DAY);
        }

        AttendanceLog saved = attendanceLogRepository.save(openLog);
        log.info(
            "Punch-OUT recorded for employee ID {} | Gross: {}m | Break: {}m | Payable: {}m "
            + "| Overtime: {}m | LocationVerified: {} | Status: {}",
            employeeId, grossMinutes, unpaidBreakMinutes, (int) payableMinutes,
            overtimeMinutes, locationVerified, openLog.getAttendanceStatus());

        return saved;
    }

    @Scheduled(cron = "0 59 23 * * ?")
    @Transactional
    public void midnightAttendanceCronJob() {
        LocalDate today = LocalDate.now();
        LocalDateTime endOfDay = today.atTime(23, 59, 0);

        log.info("=== Midnight Attendance Cron started for {} ===", today);

        // Task A: Auto-close open sessions (EXCLUDING Night Shifts)
        List<AttendanceLog> openSessions = attendanceLogRepository.findOpenSessionsByWorkDate(today);
        int closedCount = 0;

        for (AttendanceLog openLog : openSessions) {
            // Fix 4: Skip Night Shift workers - they are likely still working
            boolean isNightShift = openLog.getShift().isOvernight() || 
                                 openLog.getShift().getEndTime().isBefore(openLog.getShift().getStartTime());
            
            if (isNightShift) {
                log.info("Skipping auto-close for Night Shift employee {} (Shift ends: {})", 
                    openLog.getEmployee().getEmployeeCode(), openLog.getShift().getEndTime());
                continue;
            }

            openLog.setPunchOutTime(endOfDay);
            openLog.setCalculatedPayableMinutes(0);
            openLog.setOvertime(false);
            openLog.setOvertimeMinutes(0);
            openLog.setManuallyCorrected(true);
            openLog.setCorrectionReason("System Auto-Closed: Missing Punch Out");
            attendanceLogRepository.save(openLog);
            closedCount++;

            log.warn("Auto-closed open session ID {} for employee {} (work date: {})",
                openLog.getId(), openLog.getEmployee().getEmployeeCode(), openLog.getWorkDate());
        }

        log.info("Task A complete: {} open session(s) auto-closed.", closedCount);

        // Task B: Mark absent employees
        Set<Integer> employeesWithLog = attendanceLogRepository.findEmployeeIdsWithLogOnDate(today);
        List<Employee> activeEmployees = employeeRepository.findByStatus(EmployeeStatus.ACTIVE);

        int absentCount = 0;
        for (Employee employee : activeEmployees) {
            if (employeesWithLog.contains(employee.getId())) continue;

            LocalDate date = today;
            boolean isHoliday = holidayRepository.existsByHolidayDate(date);
            String dayName = date.getDayOfWeek().name().toUpperCase();
            boolean isWeekend = employee.getLocation().getWeekendDays().toUpperCase().contains(dayName);

            if (isHoliday || isWeekend) {
                log.debug("Skipping ABSENT for employee {} — holiday or weekend on {}",
                    employee.getEmployeeCode(), today);
                continue;
            }

            boolean isOnApprovedLeave = leaveRequestRepository.existsApprovedLeaveOnDate(employee.getId(), today);

            if (isOnApprovedLeave) {
                AttendanceLog leaveLog = AttendanceLog.builder()
                    .employee(employee)
                    .shift(employee.getShift())
                    .location(employee.getLocation())
                    .punchInTime(endOfDay)
                    .punchOutTime(endOfDay)
                    .isLocationVerifiedIn(false)
                    .isLocationVerifiedOut(false)
                    .calculatedPayableMinutes(0)
                    .isOvertime(false)
                    .overtimeMinutes(0)
                    .attendanceStatus(AttendanceStatus.ON_LEAVE)
                    .workDate(today)
                    .isManuallyCorrected(false)
                    .correctionReason("System Auto-Generated: On Approved Leave")
                    .correctedByUserId(null)
                    .build();

                attendanceLogRepository.save(leaveLog);
                log.info("ON_LEAVE record created for employee {} on {} (Approved Leave detected)", 
                    employee.getEmployeeCode(), today);
                continue;
            }

            AttendanceLog absentLog = AttendanceLog.builder()
                .employee(employee)
                .shift(employee.getShift())
                .location(employee.getLocation())
                .punchInTime(endOfDay)
                .punchOutTime(endOfDay)
                .isLocationVerifiedIn(false)
                .isLocationVerifiedOut(false)
                .calculatedPayableMinutes(0)
                .isOvertime(false)
                .overtimeMinutes(0)
                .attendanceStatus(AttendanceStatus.ABSENT)
                .workDate(today)
                .isManuallyCorrected(false)
                .correctionReason("System Auto-Generated: No Punch In Recorded")
                .correctedByUserId(null)
                .build();

            attendanceLogRepository.save(absentLog);
            absentCount++;

            log.info("ABSENT record created for employee {} on {}", employee.getEmployeeCode(), today);
        }

        log.info("Task B complete: {} employee(s) marked ABSENT.", absentCount);
        log.info("=== Midnight Attendance Cron finished for {} ===", today);
    }

    /**
     * Fix 4: Afternoon Cleanup for Night Shifts
     * Runs at 12:00 PM (Noon) to close any overnight shifts that started yesterday
     * and still don't have a punch-out.
     */
    @Scheduled(cron = "0 0 12 * * ?")
    @Transactional
    public void noonOvernightCleanupCronJob() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime noonToday = LocalDate.now().atTime(12, 0, 0);

        log.info("=== Noon Overnight Attendance Cleanup started for work date: {} ===", yesterday);

        List<AttendanceLog> openSessions = attendanceLogRepository.findOpenSessionsByWorkDate(yesterday);
        int closedCount = 0;

        for (AttendanceLog openLog : openSessions) {
            // We only auto-close if it was indeed an overnight shift or if they missed 
            // a punch-out on a day shift that was previously skipped.
            openLog.setPunchOutTime(noonToday);
            openLog.setCalculatedPayableMinutes(0);
            openLog.setOvertime(false);
            openLog.setOvertimeMinutes(0);
            openLog.setManuallyCorrected(true);
            openLog.setCorrectionReason("System Auto-Closed: Missing Punch Out (Night Shift Cleanup)");
            attendanceLogRepository.save(openLog);
            closedCount++;

            log.warn("Night-shift cleanup closed session ID {} for employee {} (work date: {})",
                openLog.getId(), openLog.getEmployee().getEmployeeCode(), openLog.getWorkDate());
        }

        log.info("Noon Cleanup complete: {} overnight session(s) auto-closed.", closedCount);
    }

    public List<AttendanceLog> getLogs(Integer employeeId, LocalDate startDate, LocalDate endDate) {
        List<AttendanceLog> logs = attendanceLogRepository.findClosedSessionsForPeriod(employeeId, startDate, endDate);
        for (AttendanceLog logEntity : logs) {
            autoHealLog(logEntity);
        }
        return logs;
    }

    public List<AttendanceLog> getMyLogs(String employeeCode) {
        List<AttendanceLog> logs = attendanceLogRepository.findByEmployeeEmployeeCodeOrderByWorkDateDesc(employeeCode);
        
        // Auto-Heal: Fix status for regularized logs and correct legacy overtime calculations
        for (AttendanceLog log : logs) {
            autoHealLog(log);
        }
        return logs;
    }

    public List<AttendanceLog> getDailyLogs(LocalDate date) {
        List<AttendanceLog> logs = attendanceLogRepository.findByWorkDateOrderByPunchInTimeDesc(date);
        
        // Auto-Heal: Fix status for regularized logs and correct legacy overtime calculations
        for (AttendanceLog log : logs) {
            autoHealLog(log);
        }
        return logs;
    }

    @Transactional
    public void syncLeaveLogs(Employee employee, LocalDate startDate, LocalDate endDate) {
        log.info("[AttendanceService] Syncing leave logs for employee {} from {} to {}", 
            employee.getEmployeeCode(), startDate, endDate);
            
        for (LocalDate date = startDate; !date.isBefore(startDate) && !date.isAfter(endDate); date = date.plusDays(1)) {
            Optional<AttendanceLog> existing = attendanceLogRepository.findFirstByEmployeeIdAndWorkDateOrderByIdDesc(employee.getId(), date);
            
            if (existing.isPresent()) {
                AttendanceLog logEntity = existing.get();
                if (logEntity.getAttendanceStatus() == AttendanceStatus.ABSENT || logEntity.getAttendanceStatus() == AttendanceStatus.HALF_DAY) {
                    logEntity.setAttendanceStatus(AttendanceStatus.ON_LEAVE);
                    logEntity.setCorrectionReason("Sync: Approved Leave taking precedence");
                    attendanceLogRepository.save(logEntity);
                }
            } else {
                AttendanceLog leaveLog = AttendanceLog.builder()
                    .employee(employee)
                    .shift(employee.getShift())
                    .location(employee.getLocation())
                    .attendanceStatus(AttendanceStatus.ON_LEAVE)
                    .workDate(date)
                    .isManuallyCorrected(false)
                    .correctionReason("System Sync: Approved Leave")
                    .build();
                attendanceLogRepository.save(leaveLog);
            }
        }
    }

    @Transactional
    public void triggerRetroactiveLeaveSync() {
        log.info("[AttendanceService] Running ON-DEMAND retroactive sync for all approved leaves...");
        try {
            List<com.coresync.hrms.backend.entity.LeaveRequest> leaves = leaveRequestRepository.findAll();
            for (com.coresync.hrms.backend.entity.LeaveRequest lr : leaves) {
                if (lr.getStatus() == com.coresync.hrms.backend.enums.LeaveStatus.APPROVED) {
                    syncLeaveLogs(lr.getEmployee(), lr.getStartDate(), lr.getEndDate());
                }
            }
        } catch (Exception e) {
            log.error("Retroactive leave sync failed", e);
        }
    }

    private void autoHealLog(AttendanceLog logEntity) {
        boolean changed = false;

        // 1. Fix Status for Approved Corrections (if stuck in LATE)
        if (logEntity.getCorrectionStatus() == CorrectionStatus.APPROVED && logEntity.getAttendanceStatus() == AttendanceStatus.LATE) {
            AttendanceStatus newStatus = resolveAttendanceStatus(logEntity.getEmployee(), logEntity.getPunchInTime());
            if (newStatus != logEntity.getAttendanceStatus()) {
                logEntity.setAttendanceStatus(newStatus);
                changed = true;
            }
        }

        // 2. Fix ABSENT or incorrectly categorized records to ON_LEAVE (The "Recovered Leave" sync)
        if (logEntity.getAttendanceStatus() == AttendanceStatus.ABSENT || logEntity.getAttendanceStatus() == AttendanceStatus.HALF_DAY) {
            boolean hasLeave = leaveRequestRepository.existsApprovedLeaveOnDate(logEntity.getEmployee().getId(), logEntity.getWorkDate());
            if (hasLeave) {
                logEntity.setAttendanceStatus(AttendanceStatus.ON_LEAVE);
                logEntity.setCorrectionReason("Auto-Healed: Approved Leave prioritized over activity");
                changed = true;
            }
        }

        // 2. Correct Legacy Overtime Calculations (if saved with incorrect duration)
        Boolean approved = logEntity.getIsOvertimeApproved();
        if (logEntity.isOvertime() && (approved == null || !approved)) {
            LocalDateTime shiftEndDateTime = logEntity.getWorkDate().atTime(logEntity.getShift().getEndTime());
            if (logEntity.getPunchOutTime().isAfter(shiftEndDateTime)) {
                LocalDateTime otStart = logEntity.getPunchInTime().isAfter(shiftEndDateTime) 
                    ? logEntity.getPunchInTime() 
                    : shiftEndDateTime;
                
                int correctOtMinutes = (int) Math.max(0, ChronoUnit.MINUTES.between(otStart, logEntity.getPunchOutTime()));
                
                if (logEntity.getOvertimeMinutes() != correctOtMinutes) {
                    logEntity.setOvertimeMinutes(correctOtMinutes);
                    changed = true;
                }
            }
        }

        if (changed) {
            attendanceLogRepository.save(logEntity);
        }
    }

    private void verifyGeofence(double empLat, double empLon, CompanyLocation location) {
        double distance = calculateHaversineDistance(
            empLat, empLon,
            location.getLatitude().doubleValue(),
            location.getLongitude().doubleValue());
        if (distance > location.getAllowedRadiusMeters()) {
            throw new LocationVerificationException(distance, location.getAllowedRadiusMeters());
        }
    }

    private boolean isWithinGeofence(double empLat, double empLon, CompanyLocation location) {
        double distance = calculateHaversineDistance(
            empLat, empLon,
            location.getLatitude().doubleValue(),
            location.getLongitude().doubleValue());
        return distance <= location.getAllowedRadiusMeters();
    }

    public static double calculateHaversineDistance(
            double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(radLat1) * Math.cos(radLat2)
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private AttendanceStatus resolveAttendanceStatus(Employee employee, LocalDateTime punchTime) {
        LocalDate date = punchTime.toLocalDate();

        boolean isHoliday = holidayRepository.existsByHolidayDate(date);
        if (isHoliday) {
            return AttendanceStatus.HOLIDAY_WORK;
        }

        String dayName = date.getDayOfWeek().name().toUpperCase();
        boolean isWeekend = employee.getLocation().getWeekendDays().toUpperCase().contains(dayName);
        if (isWeekend) {
            return AttendanceStatus.WEEKEND_WORK;
        }

        LocalDateTime shiftStart = date.atTime(employee.getShift().getStartTime());
        // Always enforce a 10-minute grace period from the specific employee's shift start
        LocalDateTime lateThreshold = shiftStart.plusMinutes(10);

        return punchTime.isAfter(lateThreshold)
            ? AttendanceStatus.LATE
            : AttendanceStatus.PRESENT;
    }
    
    @Transactional
    public AttendanceLog requestCorrection(Long logId, CorrectionRequest request) {
        AttendanceLog logEntity = attendanceLogRepository.findById(logId)
            .orElseThrow(() -> new EntityNotFoundException("Log not found"));
        
        // Fix 7: Immutable History - Check Payroll Lock
        LocalDate lockDate = systemService.getPayrollLockDate();
        if (!logEntity.getWorkDate().isAfter(lockDate)) {
            throw new IllegalStateException("Attendance records for " + logEntity.getWorkDate() + 
                " are locked for payroll. Contact HR for manual adjustments.");
        }

        logEntity.setCorrectionStatus(CorrectionStatus.PENDING);
        logEntity.setRequestedPunchInTime(request.getRequestedPunchInTime());
        logEntity.setRequestedPunchOutTime(request.getRequestedPunchOutTime());
        logEntity.setCorrectionReason(request.getReason());
        return attendanceLogRepository.save(logEntity);
    }

    @Transactional
    public AttendanceLog approveCorrection(Long logId) {
        AttendanceLog logEntity = attendanceLogRepository.findById(logId)
            .orElseThrow(() -> new EntityNotFoundException("Log not found"));

        // Overwrite times
        logEntity.setPunchInTime(logEntity.getRequestedPunchInTime());
        logEntity.setPunchOutTime(logEntity.getRequestedPunchOutTime());
        logEntity.setCorrectionStatus(CorrectionStatus.APPROVED);
        logEntity.setManuallyCorrected(true);

        // --- APPLY THE EXACT SAME MATH AS PUNCH-OUT ---
        long grossMinutes = ChronoUnit.MINUTES.between(logEntity.getPunchInTime(), logEntity.getPunchOutTime());
        
        // 1. Unpaid Break Logic
        int unpaidBreakMinutes = 0;
        if (grossMinutes >= 300) {
            unpaidBreakMinutes = (int) logEntity.getShift().getUnpaidBreakMinutes();
        }
        long payableMinutes = Math.max(0, grossMinutes - unpaidBreakMinutes);

        // 2. Night Shift Logic
        LocalTime shiftStartTime = logEntity.getShift().getStartTime();
        LocalTime shiftEndTime = logEntity.getShift().getEndTime();
        LocalDateTime shiftEndDateTime = logEntity.getWorkDate().atTime(shiftEndTime);
        
        if (shiftEndTime.isBefore(shiftStartTime) || logEntity.getShift().isOvernight()) {
            shiftEndDateTime = shiftEndDateTime.plusDays(1);
        }

        LocalDateTime punchInTime = logEntity.getPunchInTime();
        LocalDateTime punchOutTime = logEntity.getPunchOutTime();

        // 3. Overtime Loophole Logic
        long standardMinutes = (long) (logEntity.getShift().getStandardHours().doubleValue() * 60);
        boolean isOvertime = punchOutTime.isAfter(shiftEndDateTime) && payableMinutes > standardMinutes;
        
        int otMinutes = 0;
        if (isOvertime) {
            LocalDateTime otStart = punchInTime.isAfter(shiftEndDateTime) 
                ? punchInTime 
                : shiftEndDateTime;
            otMinutes = (int) Math.max(0, ChronoUnit.MINUTES.between(otStart, punchOutTime));
        }

        logEntity.setCalculatedPayableMinutes((int) payableMinutes);
        logEntity.setOvertime(isOvertime);
        logEntity.setOvertimeMinutes(otMinutes);
        if(isOvertime) logEntity.setIsOvertimeApproved(false);

        // 4. Recalculate Attendance Status (Including Half-Day Check)
        if (payableMinutes < (standardMinutes / 2.0)) {
            logEntity.setAttendanceStatus(AttendanceStatus.HALF_DAY);
        } else {
            logEntity.setAttendanceStatus(resolveAttendanceStatus(logEntity.getEmployee(), logEntity.getPunchInTime()));
        }

        return attendanceLogRepository.save(logEntity);
    }

    @Transactional
    public AttendanceLog rejectCorrection(Long logId, String reason) {
        AttendanceLog logEntity = attendanceLogRepository.findById(logId)
            .orElseThrow(() -> new EntityNotFoundException("Log not found"));
        logEntity.setCorrectionStatus(CorrectionStatus.REJECTED);
        logEntity.setCorrectionReason("Rejected: " + reason);
        return attendanceLogRepository.save(logEntity);
    }

    @Transactional
    public AttendanceLog approveOvertime(Long logId) {
        AttendanceLog logEntity = attendanceLogRepository.findById(logId)
            .orElseThrow(() -> new EntityNotFoundException("Log not found"));
        logEntity.setIsOvertimeApproved(true);
        return attendanceLogRepository.save(logEntity);
    }

    @Transactional(readOnly = true)
    public List<AttendanceLog> getPendingCorrections(Integer managerId) {
        Employee manager = employeeRepository.findById(managerId)
            .orElseThrow(() -> new EntityNotFoundException("Manager not found"));
            
        if (manager.getRole() == EmployeeRole.DEPARTMENT_MANAGER) {
            return attendanceLogRepository.findByCorrectionStatusAndEmployeeDepartmentId(
                CorrectionStatus.PENDING, manager.getDepartment().getId());
        }
        return attendanceLogRepository.findByCorrectionStatus(CorrectionStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<AttendanceLog> getDailyRoster(Integer managerId, LocalDate date) {
        Employee manager = employeeRepository.findById(managerId)
            .orElseThrow(() -> new EntityNotFoundException("Manager not found"));

        if (manager.getRole() == EmployeeRole.DEPARTMENT_MANAGER) {
            return attendanceLogRepository.findByEmployeeDepartmentIdAndWorkDate(
                manager.getDepartment().getId(), date);
        }
        return attendanceLogRepository.findByWorkDateOrderByPunchInTimeDesc(date);
    }
}