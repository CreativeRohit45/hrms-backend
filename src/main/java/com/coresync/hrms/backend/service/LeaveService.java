package com.coresync.hrms.backend.service;

import com.coresync.hrms.backend.dto.*;
import com.coresync.hrms.backend.entity.*;
import com.coresync.hrms.backend.enums.LeaveStatus;
import com.coresync.hrms.backend.enums.LeaveTransactionType;
import com.coresync.hrms.backend.exception.InsufficientBalanceException;
import com.coresync.hrms.backend.repository.*;
import jakarta.persistence.EntityNotFoundException;
import com.coresync.hrms.backend.enums.EmployeeRole;
import com.coresync.hrms.backend.enums.EmployeeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

        // --- Date Math: Calculate Actual Leave Days or Hours ---
        double appliedDuration;
        if (leaveType.getUnit() == LeaveUnit.HOURS) {
            if (request.getAppliedHours() == null || request.getAppliedHours() <= 0) {
                throw new IllegalArgumentException("Hours requested is required for '" + leaveType.getName() + "'.");
            }
            appliedDuration = request.getAppliedHours();
        } else if (request.isHalfDay()) {
            appliedDuration = 0.5;
        } else {
            appliedDuration = calculateActualLeaveDays(
                request.getStartDate(), request.getEndDate(), employee);
        }

        if (appliedDuration <= 0) {
            throw new IllegalArgumentException(
                "No valid leave duration found (check date range or requested hours).");
        }

        // --- Balance Check & Escrow ---
        int year = request.getStartDate().getYear();

        if (leaveType.isPaid()) {
            LeaveBalance balance = leaveBalanceRepository.findForUpdate(employeeId, leaveType.getId(), year)
                .orElse(null);

            if (balance == null) {
                throw new InsufficientBalanceException(0, appliedDuration, leaveType.getCode());
            }

            if (!leaveType.isAllowNegativeBalance() && balance.getBalance() < appliedDuration) {
                throw new InsufficientBalanceException(balance.getBalance(), appliedDuration, leaveType.getCode());
            }

            // ESCROW DEDUCT
            balance.deduct(appliedDuration);
            leaveBalanceRepository.save(balance);

            writeAudit(employee, leaveType, year, LeaveTransactionType.DEDUCTION,
                -appliedDuration, balance.getBalance(),
                "Leave applied: " + request.getStartDate() + " to " + request.getEndDate() + 
                (leaveType.getUnit() == LeaveUnit.HOURS ? " (" + appliedDuration + "h)" : ""),
                null, null);

            log.info("[LeaveService] ESCROW DEDUCTED | Employee: {} | Type: {} | Duration: {} | Unit: {} | Remaining: {}",
                employee.getEmployeeCode(), leaveType.getCode(), appliedDuration, leaveType.getUnit(), balance.getBalance());
        }

        // --- Build & Save Leave Request ---
        LeaveRequest leave = LeaveRequest.builder()
            .employee(employee)
            .leaveType(leaveType)
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .appliedDays(appliedDuration) // Reusing column for hours if unit is HOURS
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
                employeeId, leaveType.getId(), year, org.springframework.data.domain.PageRequest.of(0, 1)).getContent();
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

        if (leave.getEmployee().getId().equals(adminId)) {
            throw new IllegalStateException("Self-approval is strictly prohibited.");
        }

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

        if (leave.getEmployee().getId().equals(adminId)) {
            throw new IllegalStateException("Self-approval is strictly prohibited.");
        }

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
        
        // Managers can revoke leaves for their department
        boolean isManager = requester.getRole() == EmployeeRole.DEPARTMENT_MANAGER && 
                           requester.getDepartment() != null && 
                           leave.getEmployee().getDepartment() != null &&
                           requester.getDepartment().getId().equals(leave.getEmployee().getDepartment().getId());

        if (!isAdmin && !isOwner && !isManager) {
            throw new IllegalArgumentException("Unauthorized: You can only revoke your own leave, be an Admin, or be their Manager.");
        }

        // --- The Time-Machine Guard ---
        // Leave requests can only be revoked before they start. Once a leave has started
        // or is in the past, it cannot be revoked through this automated workflow.
        if (!leave.getStartDate().isAfter(LocalDate.now())) {
            throw new IllegalStateException("You cannot revoke leave requests that have already started. Please contact HR.");
        }

        refundBalance(leave, "Leave REVOKED by " + (isAdmin ? "Admin" : (isManager ? "Manager" : "Employee")) + ": " + reason);
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
    public LeaveImpactPreviewDTO previewLeaveImpact(Integer leaveId, Integer requesterId) {
        LeaveRequest leave = leaveRequestRepository.findDetailedById(leaveId)
            .orElseThrow(() -> new EntityNotFoundException("Leave request not found"));
        Employee requester = findEmployee(requesterId);

        validateImpactPreviewAccess(requester, leave);
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalStateException("Impact preview is only available for pending leave requests.");
        }

        return buildImpactPreview(leave);
    }

    @Transactional(readOnly = true)
    public List<LeaveImpactPreviewDTO> previewLeaveImpactBulk(List<Integer> leaveIds, Integer requesterId) {
        if (leaveIds == null || leaveIds.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<Integer> uniqueIds = leaveIds.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<LeaveImpactPreviewDTO> previews = new ArrayList<>();
        for (Integer leaveId : uniqueIds) {
            previews.add(previewLeaveImpact(leaveId, requesterId));
        }
        return previews;
    }

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
            return leaveRequestRepository.findByStatusAndEmployeeDepartmentIdAndEmployeeIdNot(
                LeaveStatus.PENDING, manager.getDepartment().getId(), managerId)
                .stream()
                .filter(l -> l.getEmployee().getRole() == EmployeeRole.EMPLOYEE)
                .map(this::toResponse).toList();
        } else if (manager.getRole() == EmployeeRole.HR_ADMIN) {
            return leaveRequestRepository.findByStatusAndEmployeeIdNot(LeaveStatus.PENDING, managerId)
                .stream()
                .filter(l -> l.getEmployee().getRole() == EmployeeRole.EMPLOYEE)
                .map(this::toResponse).toList();
        }
        
        return leaveRequestRepository.findByStatusAndEmployeeIdNot(LeaveStatus.PENDING, managerId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> getEmployeeBalances(Integer empId) {
        int year = LocalDate.now().getYear();
        Employee employee = findEmployee(empId);
        List<LeaveType> activeTypes = leaveTypeRepository.findByIsActiveTrue();
        List<LeaveBalance> existingBalances = leaveBalanceRepository.findByEmployeeIdAndYear(empId, year);
        
        Map<Integer, LeaveBalance> balanceMap = existingBalances.stream()
            .collect(Collectors.toMap(b -> b.getLeaveType().getId(), b -> b));

        return activeTypes.stream()
            .filter(type -> {
                // Hide LWP
                if ("LWP".equals(type.getCode())) return false;
                
                // Gender eligibility check
                if (type.getAllowedGenders() != null && !type.getAllowedGenders().isBlank()) {
                    return employee.getGender() == null || type.getAllowedGenders().toUpperCase().contains(employee.getGender().toUpperCase());
                }
                return true;
            })
            .map(type -> {
                LeaveBalance bal = balanceMap.get(type.getId());
                if (bal != null) {
                    return toBalanceResponse(bal);
                } else {
                    // Return a virtual zero-balance for types the employee doesn't have records for yet
                    return LeaveBalanceResponse.builder()
                        .leaveTypeId(type.getId())
                        .leaveTypeName(type.getName())
                        .leaveTypeCode(type.getCode())
                        .unit(type.getUnit().name())
                        .allocated(0.0)
                        .used(0.0)
                        .balance(0.0)
                        .year(year)
                        .build();
                }
            })
            .collect(Collectors.toList());
    }

    @Transactional
    public void creditOvertimeAsCompOff(Integer employeeId, int overtimeMinutes) {
        if (overtimeMinutes <= 0) return;

        double overtimeHours = Math.round((overtimeMinutes / 60.0) * 10.0) / 10.0;
        if (overtimeHours <= 0) return;

        Employee employee = findEmployee(employeeId);
        LeaveType cmpType = leaveTypeRepository.findByCode("CMP")
            .orElseThrow(() -> new EntityNotFoundException("CMP Leave Type not found in system settings."));

        int year = LocalDate.now().getYear();
        LeaveBalance balance = leaveBalanceRepository.findForUpdate(employeeId, cmpType.getId(), year)
            .orElseGet(() -> createEmptyBalance(employee, cmpType, year));

        balance.credit(overtimeHours);
        leaveBalanceRepository.save(balance);

        writeAudit(employee, cmpType, year, LeaveTransactionType.ACCRUAL,
            overtimeHours, balance.getBalance(),
            "Automatic Overtime Accrual: " + overtimeMinutes + " minutes",
            null, null);

        log.info("[LeaveService] OT ACCRUED to CMP | Employee: {} | Mins: {} | Hours: {} | New Bal: {}",
            employee.getEmployeeCode(), overtimeMinutes, overtimeHours, balance.getBalance());
    }

    @Transactional(readOnly = true)
    public Page<LeaveBalanceAuditResponse> getBalanceAuditTrail(Integer empId, Integer typeId, int year, Pageable pageable) {
        Page<LeaveBalanceAudit> trail = (typeId != null) 
            ? auditRepository.findByEmployeeIdAndLeaveTypeIdAndYearOrderByCreatedAtDesc(empId, typeId, year, pageable)
            : auditRepository.findByEmployeeIdAndYearOrderByCreatedAtDesc(empId, year, pageable);
        return trail.map(this::toAuditResponse);
    }

    @Transactional(readOnly = true)
    public LeavePreviewResponse previewLeave(Integer empId, LeaveApplyRequest request) {
        List<String> warnings = new ArrayList<>();
        if (request.getStartDate() == null || request.getEndDate() == null) {
            return LeavePreviewResponse.builder().warnings(List.of("Dates required")).build();
        }

        Employee emp = findEmployee(empId);
        LeaveType type = leaveTypeRepository.findById(request.getLeaveTypeId()).orElseThrow();
        
        double amount;
        if (type.getUnit() == LeaveUnit.HOURS) {
            amount = request.getAppliedHours() != null ? request.getAppliedHours() : 0.0;
        } else {
            amount = request.isHalfDay() ? 0.5 : calculateActualLeaveDays(request.getStartDate(), request.getEndDate(), emp);
        }
        
        if (amount <= 0 && type.getUnit() != LeaveUnit.HOURS) warnings.add("No working days in range");
        
        double currentBal = 0;
        double balAfter = 0;
        if (type.isPaid()) {
            LeaveBalance bal = leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(empId, type.getId(), request.getStartDate().getYear()).orElse(null);
            if (bal != null) {
                currentBal = bal.getBalance();
                balAfter = currentBal - amount;
                if (!type.isAllowNegativeBalance() && balAfter < 0) warnings.add("Insufficient balance");
            } else {
                warnings.add("No balance allocated");
            }
        }

        return LeavePreviewResponse.builder()
            .appliedDays(amount)
            .leaveTypeName(type.getName())
            .currentBalance(currentBal)
            .balanceAfterDeduction(balAfter)
            .warnings(warnings)
            .build();
    }


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


    // ═══════════════════════════════════════════════════════════════════
    //  6. HELPERS
    // ═══════════════════════════════════════════════════════════════════
    private LeaveImpactPreviewDTO buildImpactPreview(LeaveRequest leave) {
        Employee targetEmployee = leave.getEmployee();
        Shift shift = targetEmployee.getShift();

        if (targetEmployee.getDepartment() == null || shift == null) {
            throw new IllegalStateException("Cannot calculate coverage preview without department and shift data.");
        }

        List<LocalDate> effectiveDates = getEffectiveWorkingDates(leave);
        if (effectiveDates.isEmpty()) {
            throw new IllegalStateException("No working dates found in this leave request.");
        }

        int scheduledCount = Math.toIntExact(employeeRepository.countByDepartmentIdAndShiftIdAndStatusAndRole(
            targetEmployee.getDepartment().getId(),
            shift.getId(),
            EmployeeStatus.ACTIVE,
            EmployeeRole.EMPLOYEE
        ));

        List<LeaveRequest> approvedLeaves = leaveRequestRepository.findApprovedLeavesForDepartmentShiftInRange(
            targetEmployee.getDepartment().getId(),
            shift.getId(),
            effectiveDates.get(0),
            effectiveDates.get(effectiveDates.size() - 1)
        );

        LocalDate worstCaseDate = effectiveDates.get(0);
        double worstApprovedOffCount = 0.0;
        double lowestProjectedAvailableCount = Double.MAX_VALUE;

        for (LocalDate date : effectiveDates) {
            double alreadyApprovedOffCount = approvedLeaves.stream()
                .mapToDouble(existingLeave -> getLeaveImpactOnDate(existingLeave, date))
                .sum();
            double currentRequestImpact = getLeaveImpactOnDate(leave, date);
            double projectedAvailableCount = roundToSingleDecimal(scheduledCount - alreadyApprovedOffCount - currentRequestImpact);

            if (projectedAvailableCount < lowestProjectedAvailableCount) {
                lowestProjectedAvailableCount = projectedAvailableCount;
                worstApprovedOffCount = roundToSingleDecimal(alreadyApprovedOffCount);
                worstCaseDate = date;
            }
        }

        Integer minimumHeadcount = shift.getMinimumHeadcount();
        boolean configured = minimumHeadcount != null;
        String severity = resolveSeverity(configured, minimumHeadcount, lowestProjectedAvailableCount);

        return LeaveImpactPreviewDTO.builder()
            .leaveRequestId(leave.getId())
            .startDate(leave.getStartDate())
            .endDate(leave.getEndDate())
            .worstCaseDate(worstCaseDate)
            .shiftId(shift.getId())
            .shiftName(shift.getShiftName())
            .scheduledCount(scheduledCount)
            .alreadyApprovedOffCount(worstApprovedOffCount)
            .projectedAvailableCount(roundToSingleDecimal(lowestProjectedAvailableCount))
            .minimumHeadcount(minimumHeadcount)
            .configured(configured)
            .severity(severity)
            .message(buildImpactMessage(severity, shift.getShiftName(), worstCaseDate))
            .build();
    }

    private void validateImpactPreviewAccess(Employee requester, LeaveRequest leave) {
        if (requester.getRole() == EmployeeRole.DEPARTMENT_MANAGER) {
            if (leave.getEmployee().getRole() != EmployeeRole.EMPLOYEE) {
                throw new IllegalArgumentException("Department managers can only preview employee leave requests.");
            }
            if (requester.getDepartment() == null || leave.getEmployee().getDepartment() == null ||
                !requester.getDepartment().getId().equals(leave.getEmployee().getDepartment().getId())) {
                throw new IllegalArgumentException("Unauthorized: leave request is outside your department.");
            }
            if (leave.getEmployee().getId().equals(requester.getId())) {
                throw new IllegalArgumentException("Department managers cannot preview their own leave requests here.");
            }
            return;
        }

        if (requester.getRole() == EmployeeRole.HR_ADMIN) {
            if (leave.getEmployee().getRole() != EmployeeRole.EMPLOYEE) {
                throw new IllegalArgumentException("HR Admin can only preview employee leave requests.");
            }
            return;
        }

        if (requester.getRole() != EmployeeRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("Unauthorized: insufficient role for impact preview.");
        }
    }

    private List<LocalDate> getEffectiveWorkingDates(LeaveRequest leave) {
        Employee employee = leave.getEmployee();
        CompanyLocation location = employee.getLocation();
        Set<DayOfWeek> weekends = parseWeekendDays(location.getWeekendDays());
        Set<LocalDate> holidayDates = holidayRepository.findByDateRangeAndLocation(
                leave.getStartDate(), leave.getEndDate(), location.getId())
            .stream()
            .map(Holiday::getHolidayDate)
            .collect(Collectors.toSet());

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = leave.getStartDate(); !date.isAfter(leave.getEndDate()); date = date.plusDays(1)) {
            if (!weekends.contains(date.getDayOfWeek()) && !holidayDates.contains(date)) {
                dates.add(date);
            }
        }
        return dates;
    }

    private double getLeaveImpactOnDate(LeaveRequest leave, LocalDate date) {
        if (date.isBefore(leave.getStartDate()) || date.isAfter(leave.getEndDate())) {
            return 0.0;
        }

        if (!getEffectiveWorkingDates(leave).contains(date)) {
            return 0.0;
        }

        return leave.isHalfDay() ? 0.5 : 1.0;
    }

    private String resolveSeverity(boolean configured, Integer minimumHeadcount, double projectedAvailableCount) {
        if (!configured) {
            return "UNCONFIGURED";
        }
        if (projectedAvailableCount < minimumHeadcount) {
            return "UNDERSTAFFED";
        }
        if (projectedAvailableCount == minimumHeadcount) {
            return "RISK";
        }
        return "SAFE";
    }

    private String buildImpactMessage(String severity, String shiftName, LocalDate worstCaseDate) {
        return switch (severity) {
            case "UNDERSTAFFED" -> String.format(
                "Approving this leave would leave the %s shift understaffed on %s.", shiftName, worstCaseDate);
            case "RISK" -> String.format(
                "Approving this leave would leave the %s shift exactly at the minimum headcount on %s.", shiftName, worstCaseDate);
            case "UNCONFIGURED" -> "Coverage threshold is not configured for this shift yet.";
            default -> String.format(
                "Approving this leave keeps the %s shift above the minimum headcount on %s.", shiftName, worstCaseDate);
        };
    }

    private double roundToSingleDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
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
        if (l.getStatus() != com.coresync.hrms.backend.enums.LeaveStatus.PENDING) throw new IllegalStateException("Already " + l.getStatus());
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
        String actionByName = null;
        if (l.getActionByUserId() != null) {
            actionByName = employeeRepository.findById(l.getActionByUserId())
                .map(Employee::getFullName).orElse(null);
        }

        String pendingApproverName = null;
        if (com.coresync.hrms.backend.enums.LeaveStatus.PENDING == l.getStatus()) {
            if (l.getEmployee().getRole() == EmployeeRole.DEPARTMENT_MANAGER) {
                pendingApproverName = "Super Admin";
            } else if (l.getEmployee().getDepartment() != null) {
                pendingApproverName = employeeRepository.findManagerNameByDepartmentId(l.getEmployee().getDepartment().getId()).orElse("Super Admin");
            } else {
                pendingApproverName = "Super Admin";
            }
        }

        return LeaveResponse.builder()
            .id(l.getId()).employeeId(l.getEmployee().getId()).employeeCode(l.getEmployee().getEmployeeCode())
            .fullName(l.getEmployee().getFullName()).leaveTypeId(l.getLeaveType().getId())
            .leaveTypeName(l.getLeaveType().getName()).leaveTypeCode(l.getLeaveType().getCode())
            .startDate(l.getStartDate()).endDate(l.getEndDate()).appliedDays(l.getAppliedDays())
            .reason(l.getReason()).status(l.getStatus().name()).halfDay(l.isHalfDay())
            .halfDaySession(l.getHalfDaySession()).attachmentUrl(l.getAttachmentUrl())
            .actionByUserId(l.getActionByUserId())
            .actionByName(actionByName)
            .pendingApproverName(pendingApproverName)
            .actionAt(l.getActionAt())
            .rejectionReason(l.getRejectionReason()).createdAt(l.getCreatedAt()).build();
    }

    private LeaveBalanceResponse toBalanceResponse(LeaveBalance lb) {
        return LeaveBalanceResponse.builder().leaveTypeId(lb.getLeaveType().getId())
            .leaveTypeName(lb.getLeaveType().getName()).leaveTypeCode(lb.getLeaveType().getCode())
            .unit(lb.getLeaveType().getUnit().name())
            .allocated(lb.getAllocated()).used(lb.getUsed()).balance(lb.getBalance()).year(lb.getYear()).build();
    }

    private LeaveBalanceAuditResponse toAuditResponse(LeaveBalanceAudit a) {
        String performedByName = "System";
        if (a.getPerformedByUserId() != null) {
            performedByName = employeeRepository.findById(a.getPerformedByUserId())
                .map(Employee::getFullName).orElse("System");
        }

        return LeaveBalanceAuditResponse.builder().id(a.getId()).leaveTypeName(a.getLeaveType().getName())
            .leaveTypeCode(a.getLeaveType().getCode()).transactionType(a.getTransactionType().name())
            .amount(a.getAmount()).balanceAfter(a.getBalanceAfter()).reason(a.getReason())
            .referenceLeaveId(a.getReferenceLeaveId()).performedByUserId(a.getPerformedByUserId())
            .performedByName(performedByName)
            .createdAt(a.getCreatedAt()).build();
    }
}
