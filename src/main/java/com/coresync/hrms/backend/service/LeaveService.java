package com.coresync.hrms.backend.service;

import com.coresync.hrms.backend.dto.*;
import com.coresync.hrms.backend.entity.*;
import com.coresync.hrms.backend.enums.LeaveStatus;
import com.coresync.hrms.backend.enums.LeaveTransactionType;
import com.coresync.hrms.backend.exception.InsufficientBalanceException;
import com.coresync.hrms.backend.repository.*;
import jakarta.persistence.EntityNotFoundException;
import com.coresync.hrms.backend.enums.EmployeeRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveBalanceAuditRepository auditRepository;
    private final EmployeeRepository employeeRepository;
    private final HolidayRepository holidayRepository;
    private final GatepassRepository gatepassRepository;
    private final SystemSettingsRepository systemSettingsRepository;
    private final CompOffRequestRepository compOffRequestRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final AttendanceService attendanceService;

    // ═══════════════════════════════════════════════════════════════════
    //  1. APPLY FOR LEAVE — The Fortress
    // ═══════════════════════════════════════════════════════════════════

    @Transactional
    public LeaveResponse applyForLeave(Integer employeeId, LeaveApplyRequest request) {

        // --- Guard 1: Time Travel Block ---
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date.");
        }

        // --- Guard 2: Half-Day Validation ---
        if (request.isHalfDay()) {
            if (request.getHalfDaySession() == null || request.getHalfDaySession().isBlank()) {
                throw new IllegalArgumentException("Half-day session (FIRST_HALF or SECOND_HALF) is required when halfDay is true.");
            }
            if (!request.getStartDate().equals(request.getEndDate())) {
                throw new IllegalArgumentException("Half-day leaves can only be applied for a single date.");
            }
        }

        // --- Resolve Employee & Leave Type ---
        Employee employee = findEmployee(employeeId);
        LeaveType leaveType = leaveTypeRepository.findById(request.getLeaveTypeId())
            .orElseThrow(() -> new EntityNotFoundException("Leave type not found: ID " + request.getLeaveTypeId()));

        if (!leaveType.isActive()) {
            throw new IllegalArgumentException("Leave type '" + leaveType.getName() + "' is currently inactive.");
        }

        // --- Guard 3: Gender Eligibility ---
        if (leaveType.getAllowedGenders() != null && !leaveType.getAllowedGenders().isBlank()) {
            String empGender = employee.getGender();
            if (empGender == null || !leaveType.getAllowedGenders().toUpperCase().contains(empGender.toUpperCase())) {
                throw new IllegalArgumentException(
                    leaveType.getName() + " is only available for: " + leaveType.getAllowedGenders());
            }
        }

        // --- Guard 4: Probation Check ---
        if (leaveType.isRequiresProbationCompletion()) {
            LocalDate probationEnd = employee.getProbationEndDate();
            if (probationEnd != null && LocalDate.now().isBefore(probationEnd)) {
                throw new IllegalArgumentException(
                    leaveType.getName() + " is not available during probation. Your probation ends on " + probationEnd);
            }
        }

        // --- Guard 5: Overlap Block ---
        List<LeaveRequest> conflicts = leaveRequestRepository.findOverlapping(
            employeeId, request.getStartDate(), request.getEndDate(),
            List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)
        );
        if (!conflicts.isEmpty()) {
            LeaveRequest first = conflicts.get(0);
            throw new IllegalArgumentException(String.format(
                "Leave dates overlap with an existing %s leave (ID: %d) from %s to %s.",
                first.getStatus().name(), first.getId(), first.getStartDate(), first.getEndDate()));
        }

        // --- Guard 6: Holiday Block ---
        for (LocalDate date = request.getStartDate(); !date.isAfter(request.getEndDate()); date = date.plusDays(1)) {
            if (holidayRepository.existsByHolidayDateAndLocation(date, employee.getLocation().getId())) {
                throw new IllegalArgumentException("Cannot apply for leave on " + date + " as it is a Company Holiday.");
            }
        }

        // --- Guard 7: Gatepass Conflict ---
        List<Gatepass> gpConflicts = gatepassRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
            .filter(gp -> gp.getCreatedAt().toLocalDate().isEqual(request.getStartDate()) || 
                          gp.getCreatedAt().toLocalDate().isEqual(request.getEndDate()))
            .toList();
        if (!gpConflicts.isEmpty()) {
            throw new IllegalArgumentException("Cannot apply for Leave on dates that have existing Gatepass requests.");
        }

        // --- Date Math: Calculate Actual Leave Days ---
        double appliedDays;
        if (request.isHalfDay()) {
            appliedDays = 0.5;
        } else {
            appliedDays = calculateActualLeaveDays(
                request.getStartDate(), request.getEndDate(), employee);
        }

        if (appliedDays <= 0) {
            throw new IllegalArgumentException(
                "No working days found in the selected date range (all dates are holidays or weekends).");
        }

        // --- Balance Check & Escrow ---
        int year = request.getStartDate().getYear();

        if (leaveType.isPaid()) {
            LeaveBalance balance = leaveBalanceRepository.findForUpdate(employeeId, leaveType.getId(), year)
                .orElse(null);

            if (balance == null) {
                throw new InsufficientBalanceException(0, appliedDays, leaveType.getCode());
            }

            if (!leaveType.isAllowNegativeBalance() && balance.getBalance() < appliedDays) {
                throw new InsufficientBalanceException(balance.getBalance(), appliedDays, leaveType.getCode());
            }

            // ESCROW DEDUCT
            balance.deduct(appliedDays);
            leaveBalanceRepository.save(balance);

            writeAudit(employee, leaveType, year, LeaveTransactionType.DEDUCTION,
                -appliedDays, balance.getBalance(),
                "Leave applied: " + request.getStartDate() + " to " + request.getEndDate(),
                null, null);

            log.info("[LeaveService] ESCROW DEDUCTED | Employee: {} | Type: {} | Days: {} | Remaining: {}",
                employee.getEmployeeCode(), leaveType.getCode(), appliedDays, balance.getBalance());
        }

        // --- Build & Save Leave Request ---
        LeaveRequest leave = LeaveRequest.builder()
            .employee(employee)
            .leaveType(leaveType)
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .appliedDays(appliedDays)
            .reason(request.getReason())
            .status(LeaveStatus.PENDING)
            .isHalfDay(request.isHalfDay())
            .halfDaySession(request.getHalfDaySession())
            .attachmentUrl(request.getAttachmentUrl())
            .build();

        LeaveRequest saved = leaveRequestRepository.save(leave);

        // Link audit to leave ID
        if (leaveType.isPaid()) {
            List<LeaveBalanceAudit> recentAudits = auditRepository.findByEmployeeIdAndLeaveTypeIdAndYearOrderByCreatedAtDesc(
                employeeId, leaveType.getId(), year);
            if (!recentAudits.isEmpty()) {
                LeaveBalanceAudit latest = recentAudits.get(0);
                if (latest.getReferenceLeaveId() == null) {
                    latest.setReferenceLeaveId(saved.getId());
                    auditRepository.save(latest);
                }
            }
        }

        return toResponse(saved);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  2. APPROVE / REJECT / CANCEL / REVOKE
    // ═══════════════════════════════════════════════════════════════════

    @Transactional
    public LeaveResponse approveLeave(Integer leaveId, Integer adminId) {
        LeaveRequest leave = findPendingLeave(leaveId);
        validateAdminExists(adminId);

        leave.setStatus(LeaveStatus.APPROVED);
        leave.setActionByUserId(adminId);
        leave.setActionAt(LocalDateTime.now());

        LeaveRequest saved = leaveRequestRepository.save(leave);
        
        // Instant Sync: Generate/Update attendance logs for the whole leave period (Inclusive Fix)
        attendanceService.syncLeaveLogs(leave.getEmployee(), leave.getStartDate(), leave.getEndDate());
        
        log.info("[LeaveService] Leave APPROVED | ID: {} | By Admin: {}", leaveId, adminId);
        return toResponse(saved);
    }

    @Transactional
    public LeaveResponse rejectLeave(Integer leaveId, Integer adminId, LeaveActionRequest actionRequest) {
        if (actionRequest.getRejectionReason() == null || actionRequest.getRejectionReason().isBlank()) {
            throw new IllegalArgumentException("A rejection reason is required.");
        }

        LeaveRequest leave = findPendingLeave(leaveId);
        validateAdminExists(adminId);

        // ESCROW REFUND
        refundBalance(leave, "Leave rejected by admin ID " + adminId);

        leave.setStatus(LeaveStatus.REJECTED);
        leave.setActionByUserId(adminId);
        leave.setActionAt(LocalDateTime.now());
        leave.setRejectionReason(actionRequest.getRejectionReason());

        LeaveRequest saved = leaveRequestRepository.save(leave);
        log.info("[LeaveService] Leave REJECTED | ID: {} | By Admin: {}", leaveId, adminId);
        return toResponse(saved);
    }

    @Transactional
    public LeaveResponse cancelLeave(Integer leaveId, Integer employeeId) {
        LeaveRequest leave = leaveRequestRepository.findById(leaveId)
            .orElseThrow(() -> new EntityNotFoundException("Leave request not found"));

        if (!leave.getEmployee().getId().equals(employeeId)) {
            throw new IllegalArgumentException("You can only cancel your own leave requests.");
        }

        if (leave.getStatus() != LeaveStatus.PENDING && leave.getStatus() != LeaveStatus.APPROVED) {
            throw new IllegalStateException("Status " + leave.getStatus() + " cannot be cancelled.");
        }

        if (!leave.getStartDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot cancel a leave that has already started.");
        }

        // Payroll Lock Check
        Optional<SystemSettings> lockSetting = systemSettingsRepository.findBySettingKey("payroll_locked_until_date");
        if (lockSetting.isPresent()) {
            LocalDate lockDate = LocalDate.parse(lockSetting.get().getSettingValue());
            if (!leave.getStartDate().isAfter(lockDate)) {
                throw new IllegalStateException("Leave period is locked for payroll (Locked until " + lockDate + ").");
            }
        }

        refundBalance(leave, "Leave cancelled by employee");
        leave.setStatus(LeaveStatus.CANCELLED);
        leave.setActionByUserId(employeeId);
        leave.setActionAt(LocalDateTime.now());

        LeaveRequest saved = leaveRequestRepository.save(leave);
        log.info("[LeaveService] Leave CANCELLED | ID: {} | By Employee: {}", leaveId, employeeId);
        return toResponse(saved);
    }

    @Transactional
    public LeaveResponse revokeLeave(Integer leaveId, Integer requesterId, String reason) {
        LeaveRequest leave = leaveRequestRepository.findById(leaveId)
            .orElseThrow(() -> new EntityNotFoundException("Leave request not found"));

        if (leave.getStatus() != LeaveStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED leave requests can be revoked. Current status: " + leave.getStatus());
        }

        Employee requester = employeeRepository.findById(requesterId)
            .orElseThrow(() -> new EntityNotFoundException("Requester not found"));

        boolean isAdmin = requester.getRole() == EmployeeRole.HR_ADMIN || requester.getRole() == EmployeeRole.SUPER_ADMIN;
        boolean isOwner = leave.getEmployee().getId().equals(requesterId);

        if (!isAdmin && !isOwner) {
            throw new IllegalArgumentException("Unauthorized: You can only revoke your own leave or be an Admin.");
        }

        // --- Trap 1 Fix: The Time-Machine Guard ---
        if (!isAdmin && leave.getStartDate().isBefore(LocalDate.now())) {
            throw new IllegalStateException("Employees cannot revoke leaves that have already started or occurred. Contact HR.");
        }

        refundBalance(leave, "Leave REVOKED by " + (isAdmin ? "Admin" : "Employee") + ": " + reason);
        leave.setStatus(LeaveStatus.REVOKED);
        leave.setActionByUserId(requesterId);
        leave.setActionAt(LocalDateTime.now());
        leave.setRejectionReason(reason);

        LeaveRequest saved = leaveRequestRepository.save(leave);
        
        // Reverse the attendance logs if it was already approved/synced
        attendanceService.syncLeaveLogs(leave.getEmployee(), leave.getStartDate(), leave.getEndDate());

        log.info("[LeaveService] Leave REVOKED | ID: {} | By User: {}", leaveId, requesterId);
        return toResponse(saved);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  3. COMP-OFF (CMP) WORKFLOW
    // ═══════════════════════════════════════════════════════════════════

    @Transactional
    public void requestCompOff(Integer employeeId, Long attendanceLogId, String reason) {
        AttendanceLog logEntry = attendanceLogRepository.findById(attendanceLogId)
            .orElseThrow(() -> new EntityNotFoundException("Attendance log not found"));

        if (!logEntry.getEmployee().getId().equals(employeeId)) {
            throw new IllegalArgumentException("Unauthorized access to attendance log.");
        }

        if (logEntry.getAttendanceStatus() != com.coresync.hrms.backend.enums.AttendanceStatus.WEEKEND_WORK && 
            logEntry.getAttendanceStatus() != com.coresync.hrms.backend.enums.AttendanceStatus.HOLIDAY_WORK) {
            throw new IllegalArgumentException("Comp-Off only available for weekend/holiday work.");
        }

        CompOffRequest request = CompOffRequest.builder()
            .employee(logEntry.getEmployee())
            .attendanceLog(logEntry)
            .status(LeaveStatus.PENDING)
            .reason(reason)
            .build();
        
        compOffRequestRepository.save(request);
    }

    @Transactional
    public void approveCompOff(Integer requestId, Integer adminId) {
        CompOffRequest request = compOffRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Comp-Off request not found"));

        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalStateException("Request is already " + request.getStatus());
        }

        LeaveType cmpType = leaveTypeRepository.findByCode("CMP")
            .orElseThrow(() -> new EntityNotFoundException("CMP Leave Type not found"));

        manualAdjustBalance(LeaveGrantRequest.builder()
            .employeeId(request.getEmployee().getId())
            .leaveTypeId(cmpType.getId())
            .amount(1.0)
            .reason("Comp-Off for " + request.getAttendanceLog().getWorkDate())
            .build(), adminId);

        request.setStatus(LeaveStatus.APPROVED);
        request.setActionByUserId(adminId);
        request.setActionAt(LocalDateTime.now());
        compOffRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<CompOffRequest> getPendingCompOffs() {
        return compOffRequestRepository.findByStatus(LeaveStatus.PENDING);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  4. ADMINISTRATIVE & REVENUE TOOLS
    // ═══════════════════════════════════════════════════════════════════

    @Transactional
    public LeaveBalanceResponse manualAdjustBalance(LeaveGrantRequest request, Integer adminId) {
        Employee employee = findEmployee(request.getEmployeeId());
        LeaveType type = leaveTypeRepository.findById(request.getLeaveTypeId())
            .orElseThrow(() -> new EntityNotFoundException("Leave type not found"));

        int year = LocalDate.now().getYear();
        LeaveBalance balance = leaveBalanceRepository
            .findByEmployeeIdAndLeaveTypeIdAndYear(employee.getId(), type.getId(), year)
            .orElseGet(() -> createEmptyBalance(employee, type, year));

        balance.credit(request.getAmount());
        leaveBalanceRepository.save(balance);

        writeAudit(employee, type, year, LeaveTransactionType.MANUAL_ADJUSTMENT,
            request.getAmount(), balance.getBalance(),
            "Manual adjustment by admin " + adminId + ": " + request.getReason(),
            null, adminId);

        return toBalanceResponse(balance);
    }

    @Transactional
    public LeaveBalanceResponse overrideBalance(Integer employeeId, Integer leaveTypeId, Double amount, String reason, Integer adminId) {
        Employee employee = findEmployee(employeeId);
        LeaveType type = leaveTypeRepository.findById(leaveTypeId)
            .orElseThrow(() -> new EntityNotFoundException("Leave type not found"));

        int year = LocalDate.now().getYear();
        LeaveBalance balance = leaveBalanceRepository.findForUpdate(employee.getId(), type.getId(), year)
            .orElseGet(() -> createEmptyBalance(employee, type, year));

        // Direct adjustment (can be negative)
        if (amount > 0) {
            balance.credit(amount);
        } else {
            balance.deduct(Math.abs(amount));
        }
        
        leaveBalanceRepository.save(balance);

        writeAudit(employee, type, year, LeaveTransactionType.MANUAL_ADJUSTMENT,
            amount, balance.getBalance(),
            "HR OVERRIDE by admin " + adminId + ": " + reason,
            null, adminId);

        log.info("[LeaveService] HR OVERRIDE | Employee: {} | Type: {} | Adj: {} | Final: {}", 
            employee.getEmployeeCode(), type.getCode(), amount, balance.getBalance());

        return toBalanceResponse(balance);
    }

    /**
     * Initializes proration-based balances for new employees.
     */
    @Transactional
    public void initializeBalancesForNewEmployee(Employee employee) {
        initializeBalancesForNewEmployee(employee, null);
    }

    public void initializeBalancesForNewEmployee(Employee employee, List<InitialLeaveBalanceDTO> overrides) {
        int year = LocalDate.now().getYear();
        int monthsRemaining = 12 - LocalDate.now().getMonthValue() + 1;
        
        List<LeaveType> types = leaveTypeRepository.findByIsActiveTrue();
        Map<Integer, Double> overrideMap = new HashMap<>();
        if (overrides != null) {
            for (InitialLeaveBalanceDTO o : overrides) {
                overrideMap.put(o.getLeaveTypeId(), o.getBalance());
            }
        }

        for (LeaveType type : types) {
            // Basic gender/eligibility check
            if (type.getAllowedGenders() != null && !type.getAllowedGenders().isBlank()) {
                if (employee.getGender() == null || !type.getAllowedGenders().toUpperCase().contains(employee.getGender().toUpperCase())) {
                    continue;
                }
            }

            double quota;
            String reason;

            if (overrideMap.containsKey(type.getId())) {
                quota = overrideMap.get(type.getId());
                reason = "Manual initial allocation (Negotiated/Overridden)";
            } else {
                quota = (type.getMonthlyAccrualRate() > 0) 
                    ? type.getMonthlyAccrualRate() * monthsRemaining
                    : (type.getDefaultAnnualQuota() / 12.0) * monthsRemaining;
                
                quota = Math.round(quota * 2) / 2.0;
                reason = "Pro-rated initial allocation (" + monthsRemaining + " months)";
            }
            
            if (quota <= 0 && !overrideMap.containsKey(type.getId())) continue;

            LeaveBalance bal = LeaveBalance.builder()
                .employee(employee).leaveType(type).year(year)
                .allocated(quota).used(0).balance(quota).build();
            leaveBalanceRepository.save(bal);

            writeAudit(employee, type, year, LeaveTransactionType.ACCRUAL,
                quota, quota, reason, null, null);
        }
    }

    @Transactional(readOnly = true)
    public List<TeamAvailabilityDTO> getTeamAvailability(Integer employeeId) {
        Employee employee = findEmployee(employeeId);
        if (employee.getDepartment() == null) return Collections.emptyList();

        LocalDate today = LocalDate.now();
        return leaveRequestRepository.findApprovedLeavesForDepartmentInRange(
                employee.getDepartment().getId(), employeeId, today, today)
            .stream().map(l -> TeamAvailabilityDTO.builder()
                .employeeCode(l.getEmployee().getEmployeeCode())
                .fullName(l.getEmployee().getFullName())
                .designation(l.getEmployee().getDesignation())
                .leaveTypeName(l.getLeaveType().getName())
                .leaveTypeCode(l.getLeaveType().getCode())
                .startDate(l.getStartDate())
                .endDate(l.getEndDate())
                .halfDay(l.isHalfDay())
                .halfDaySession(l.getHalfDaySession())
                .build()).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveType> getActiveLeaveTypes() {
        return leaveTypeRepository.findByIsActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<DepartmentAbsenteeDTO> getDepartmentAbsentees(Integer requesterId) {
        Employee requester = findEmployee(requesterId);
        if (requester.getDepartment() == null) return Collections.emptyList();

        Integer deptId = requester.getDepartment().getId();
        LocalDate today = LocalDate.now();

        List<Employee> deptEmployees = employeeRepository.findByStatus(com.coresync.hrms.backend.enums.EmployeeStatus.ACTIVE).stream()
            .filter(e -> e.getDepartment() != null && e.getDepartment().getId().equals(deptId))
            .filter(e -> !e.getId().equals(requesterId))
            .collect(Collectors.toList());

        List<Integer> presentIds = attendanceLogRepository.findEmployeeIdsWithStatusOnDate(
             deptId, today, List.of(
                 com.coresync.hrms.backend.enums.AttendanceStatus.PRESENT,
                 com.coresync.hrms.backend.enums.AttendanceStatus.LATE,
                 com.coresync.hrms.backend.enums.AttendanceStatus.HALF_DAY,
                 com.coresync.hrms.backend.enums.AttendanceStatus.WEEKEND_WORK,
                 com.coresync.hrms.backend.enums.AttendanceStatus.HOLIDAY_WORK
             )
        );

        List<DepartmentAbsenteeDTO> results = new ArrayList<>();
        for (Employee e : deptEmployees) {
            if (presentIds.contains(e.getId())) continue;

            Optional<LeaveRequest> leave = leaveRequestRepository.findOverlapping(
                e.getId(), today, today, List.of(LeaveStatus.APPROVED)).stream().findFirst();

            String initials = Arrays.stream(e.getFullName().split(" "))
                .map(s -> s.isEmpty() ? "" : s.substring(0, 1))
                .collect(Collectors.joining()).toUpperCase();

            results.add(DepartmentAbsenteeDTO.builder()
                .employeeId(e.getId())
                .fullName(e.getFullName())
                .employeeCode(e.getEmployeeCode())
                .departmentName(e.getDepartment().getName())
                .initials(initials.substring(0, Math.min(2, initials.length())))
                .status(leave.isPresent() ? "ON_LEAVE" : "ABSENT")
                .leaveTypeCode(leave.map(l -> l.getLeaveType().getCode()).orElse(null))
                .build());
        }
        return results;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  5. QUERIES & PREVIEW
    // ═══════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<LeaveResponse> getMyLeaves(Integer empId) {
        return leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(empId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> getPendingLeaves(Integer managerId) {
        Employee manager = employeeRepository.findById(managerId)
            .orElseThrow(() -> new EntityNotFoundException("Manager not found"));
            
        if (manager.getRole() == EmployeeRole.DEPARTMENT_MANAGER) {
            return leaveRequestRepository.findByStatusAndEmployeeDepartmentId(
                LeaveStatus.PENDING, manager.getDepartment().getId())
                .stream().map(this::toResponse).toList();
        }
        return leaveRequestRepository.findByStatusOrderByCreatedAtDesc(LeaveStatus.PENDING)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> getEmployeeBalances(Integer empId) {
        int year = LocalDate.now().getYear();
        return leaveBalanceRepository.findByEmployeeIdAndYear(empId, year)
            .stream().map(this::toBalanceResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceAuditResponse> getBalanceAuditTrail(Integer empId, Integer typeId, int year) {
        List<LeaveBalanceAudit> trail = (typeId != null) 
            ? auditRepository.findByEmployeeIdAndLeaveTypeIdAndYearOrderByCreatedAtDesc(empId, typeId, year)
            : auditRepository.findByEmployeeIdAndYearOrderByCreatedAtDesc(empId, year);
        return trail.stream().map(this::toAuditResponse).toList();
    }

    @Transactional(readOnly = true)
    public LeavePreviewResponse previewLeave(Integer empId, LeaveApplyRequest request) {
        List<String> warnings = new ArrayList<>();
        if (request.getStartDate() == null || request.getEndDate() == null) {
            return LeavePreviewResponse.builder().warnings(List.of("Dates required")).build();
        }

        Employee emp = findEmployee(empId);
        LeaveType type = leaveTypeRepository.findById(request.getLeaveTypeId()).orElseThrow();
        
        double days = request.isHalfDay() ? 0.5 : calculateActualLeaveDays(request.getStartDate(), request.getEndDate(), emp);
        
        if (days <= 0) warnings.add("No working days in range");
        
        double currentBal = 0;
        double balAfter = 0;
        if (type.isPaid()) {
            LeaveBalance bal = leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(empId, type.getId(), request.getStartDate().getYear()).orElse(null);
            if (bal != null) {
                currentBal = bal.getBalance();
                balAfter = currentBal - days;
                if (!type.isAllowNegativeBalance() && balAfter < 0) warnings.add("Insufficient balance");
            } else {
                warnings.add("No balance allocated");
            }
        }

        return LeavePreviewResponse.builder()
            .appliedDays(days)
            .leaveTypeName(type.getName())
            .currentBalance(currentBal)
            .balanceAfterDeduction(balAfter)
            .warnings(warnings)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  6. HELPERS
    // ═══════════════════════════════════════════════════════════════════

    public double calculateActualLeaveDays(LocalDate start, LocalDate end, Employee emp) {
        CompanyLocation loc = emp.getLocation();
        Set<DayOfWeek> weekends = parseWeekendDays(loc.getWeekendDays());
        List<Holiday> holidays = holidayRepository.findByDateRangeAndLocation(start, end, loc.getId());
        Set<LocalDate> holidayDates = holidays.stream().map(Holiday::getHolidayDate).collect(Collectors.toSet());

        double count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!weekends.contains(d.getDayOfWeek()) && !holidayDates.contains(d)) count++;
        }
        return count;
    }

    private void refundBalance(LeaveRequest leave, String reason) {
        if (!leave.getLeaveType().isPaid()) return;
        int year = leave.getStartDate().getYear();
        leaveBalanceRepository.findForUpdate(leave.getEmployee().getId(), leave.getLeaveType().getId(), year)
            .ifPresent(bal -> {
                bal.refund(leave.getAppliedDays());
                leaveBalanceRepository.save(bal);
                writeAudit(leave.getEmployee(), leave.getLeaveType(), year, LeaveTransactionType.REFUND,
                    leave.getAppliedDays(), bal.getBalance(), reason, leave.getId(), null);
            });
    }

    private void writeAudit(Employee e, LeaveType t, int y, LeaveTransactionType tx, double amt, double after, String res, Integer refId, Integer adminId) {
        auditRepository.save(LeaveBalanceAudit.builder()
            .employee(e).leaveType(t).year(y).transactionType(tx)
            .amount(amt).balanceAfter(after).reason(res)
            .referenceLeaveId(refId).performedByUserId(adminId).build());
    }

    private Set<DayOfWeek> parseWeekendDays(String config) {
        if (config == null || config.isBlank()) return Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        Set<DayOfWeek> set = new HashSet<>();
        for (String s : config.split(",")) {
            try { set.add(DayOfWeek.valueOf(s.trim().toUpperCase())); } catch (Exception ex) {
                switch(s.trim().toUpperCase()) {
                    case "SAT" -> set.add(DayOfWeek.SATURDAY);
                    case "SUN" -> set.add(DayOfWeek.SUNDAY);
                }
            }
        }
        return set;
    }

    private LeaveRequest findPendingLeave(Integer id) {
        LeaveRequest l = leaveRequestRepository.findById(id).orElseThrow();
        if (l.getStatus() != LeaveStatus.PENDING) throw new IllegalStateException("Already " + l.getStatus());
        return l;
    }

    private Employee findEmployee(Integer id) {
        return employeeRepository.findById(id).orElseThrow();
    }

    private void validateAdminExists(Integer id) {
        if (!employeeRepository.existsById(id)) throw new EntityNotFoundException("Admin not found");
    }

    private LeaveBalance createEmptyBalance(Employee e, LeaveType t, int y) {
        return LeaveBalance.builder().employee(e).leaveType(t).year(y).allocated(0).used(0).balance(0).build();
    }

    private LeaveResponse toResponse(LeaveRequest l) {
        return LeaveResponse.builder()
            .id(l.getId()).employeeId(l.getEmployee().getId()).employeeCode(l.getEmployee().getEmployeeCode())
            .fullName(l.getEmployee().getFullName()).leaveTypeId(l.getLeaveType().getId())
            .leaveTypeName(l.getLeaveType().getName()).leaveTypeCode(l.getLeaveType().getCode())
            .startDate(l.getStartDate()).endDate(l.getEndDate()).appliedDays(l.getAppliedDays())
            .reason(l.getReason()).status(l.getStatus().name()).halfDay(l.isHalfDay())
            .halfDaySession(l.getHalfDaySession()).attachmentUrl(l.getAttachmentUrl())
            .actionByUserId(l.getActionByUserId()).actionAt(l.getActionAt())
            .rejectionReason(l.getRejectionReason()).createdAt(l.getCreatedAt()).build();
    }

    private LeaveBalanceResponse toBalanceResponse(LeaveBalance lb) {
        return LeaveBalanceResponse.builder().leaveTypeId(lb.getLeaveType().getId())
            .leaveTypeName(lb.getLeaveType().getName()).leaveTypeCode(lb.getLeaveType().getCode())
            .allocated(lb.getAllocated()).used(lb.getUsed()).balance(lb.getBalance()).year(lb.getYear()).build();
    }

    private LeaveBalanceAuditResponse toAuditResponse(LeaveBalanceAudit a) {
        return LeaveBalanceAuditResponse.builder().id(a.getId()).leaveTypeName(a.getLeaveType().getName())
            .leaveTypeCode(a.getLeaveType().getCode()).transactionType(a.getTransactionType().name())
            .amount(a.getAmount()).balanceAfter(a.getBalanceAfter()).reason(a.getReason())
            .referenceLeaveId(a.getReferenceLeaveId()).performedByUserId(a.getPerformedByUserId())
            .createdAt(a.getCreatedAt()).build();
    }
}