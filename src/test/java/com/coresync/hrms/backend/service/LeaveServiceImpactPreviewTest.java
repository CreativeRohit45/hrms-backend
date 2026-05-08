package com.coresync.hrms.backend.service;

import com.coresync.hrms.backend.dto.LeaveImpactPreviewDTO;
import com.coresync.hrms.backend.entity.CompanyLocation;
import com.coresync.hrms.backend.entity.Department;
import com.coresync.hrms.backend.entity.Employee;
import com.coresync.hrms.backend.entity.LeaveRequest;
import com.coresync.hrms.backend.entity.Shift;
import com.coresync.hrms.backend.enums.EmployeeRole;
import com.coresync.hrms.backend.enums.EmployeeStatus;
import com.coresync.hrms.backend.enums.LeaveStatus;
import com.coresync.hrms.backend.repository.AttendanceLogRepository;
import com.coresync.hrms.backend.repository.CompOffRequestRepository;
import com.coresync.hrms.backend.repository.EmployeeRepository;
import com.coresync.hrms.backend.repository.GatepassRepository;
import com.coresync.hrms.backend.repository.HolidayRepository;
import com.coresync.hrms.backend.repository.LeaveBalanceAuditRepository;
import com.coresync.hrms.backend.repository.LeaveBalanceRepository;
import com.coresync.hrms.backend.repository.LeaveRequestRepository;
import com.coresync.hrms.backend.repository.LeaveTypeRepository;
import com.coresync.hrms.backend.repository.SystemSettingsRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveServiceImpactPreviewTest {

    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private LeaveBalanceRepository leaveBalanceRepository;
    @Mock private LeaveBalanceAuditRepository auditRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private GatepassRepository gatepassRepository;
    @Mock private SystemSettingsRepository systemSettingsRepository;
    @Mock private CompOffRequestRepository compOffRequestRepository;
    @Mock private AttendanceLogRepository attendanceLogRepository;
    @Mock private AttendanceService attendanceService;

    @InjectMocks
    private LeaveService leaveService;

    @Test
    void previewLeaveImpact_returnsWorstCaseAcrossMultiDayRange() {
        Shift shift = buildShift(2, "General", 2);
        Department department = buildDepartment(3, "Ops");
        CompanyLocation location = buildLocation(4, "SATURDAY,SUNDAY");
        Employee requester = buildEmployee(10, EmployeeRole.DEPARTMENT_MANAGER, department, shift, location);
        Employee target = buildEmployee(11, EmployeeRole.EMPLOYEE, department, shift, location);
        Employee colleague = buildEmployee(12, EmployeeRole.EMPLOYEE, department, shift, location);

        LeaveRequest pendingLeave = buildLeave(100, target, LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 13), LeaveStatus.PENDING, false);
        LeaveRequest approvedLeave = buildLeave(200, colleague, LocalDate.of(2026, 5, 12), LocalDate.of(2026, 5, 12), LeaveStatus.APPROVED, false);

        when(leaveRequestRepository.findDetailedById(100)).thenReturn(Optional.of(pendingLeave));
        when(employeeRepository.findById(10)).thenReturn(Optional.of(requester));
        when(employeeRepository.countByDepartmentIdAndShiftIdAndStatusAndRole(3, 2, EmployeeStatus.ACTIVE, EmployeeRole.EMPLOYEE)).thenReturn(4L);
        when(leaveRequestRepository.findApprovedLeavesForDepartmentShiftInRange(3, 2, LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 13)))
            .thenReturn(List.of(approvedLeave));
        when(holidayRepository.findByDateRangeAndLocation(LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 13), 4)).thenReturn(List.of());

        LeaveImpactPreviewDTO preview = leaveService.previewLeaveImpact(100, 10);

        assertEquals(LocalDate.of(2026, 5, 12), preview.getWorstCaseDate());
        assertEquals("RISK", preview.getSeverity());
        assertEquals(1.0, preview.getAlreadyApprovedOffCount());
        assertEquals(2.0, preview.getProjectedAvailableCount());
    }

    @Test
    void previewLeaveImpact_countsHalfDayAsPointFive() {
        Shift shift = buildShift(2, "General", 1);
        Department department = buildDepartment(3, "Ops");
        CompanyLocation location = buildLocation(4, "SATURDAY,SUNDAY");
        Employee requester = buildEmployee(20, EmployeeRole.HR_ADMIN, department, shift, location);
        Employee target = buildEmployee(21, EmployeeRole.EMPLOYEE, department, shift, location);
        Employee colleague = buildEmployee(22, EmployeeRole.EMPLOYEE, department, shift, location);

        LeaveRequest pendingLeave = buildLeave(101, target, LocalDate.of(2026, 5, 14), LocalDate.of(2026, 5, 14), LeaveStatus.PENDING, true);
        LeaveRequest approvedLeave = buildLeave(201, colleague, LocalDate.of(2026, 5, 14), LocalDate.of(2026, 5, 14), LeaveStatus.APPROVED, true);

        when(leaveRequestRepository.findDetailedById(101)).thenReturn(Optional.of(pendingLeave));
        when(employeeRepository.findById(20)).thenReturn(Optional.of(requester));
        when(employeeRepository.countByDepartmentIdAndShiftIdAndStatusAndRole(3, 2, EmployeeStatus.ACTIVE, EmployeeRole.EMPLOYEE)).thenReturn(2L);
        when(leaveRequestRepository.findApprovedLeavesForDepartmentShiftInRange(3, 2, LocalDate.of(2026, 5, 14), LocalDate.of(2026, 5, 14)))
            .thenReturn(List.of(approvedLeave));
        when(holidayRepository.findByDateRangeAndLocation(LocalDate.of(2026, 5, 14), LocalDate.of(2026, 5, 14), 4)).thenReturn(List.of());

        LeaveImpactPreviewDTO preview = leaveService.previewLeaveImpact(101, 20);

        assertEquals(0.5, preview.getAlreadyApprovedOffCount());
        assertEquals(1.0, preview.getProjectedAvailableCount());
        assertEquals("RISK", preview.getSeverity());
    }

    @Test
    void previewLeaveImpact_marksUnconfiguredShiftWhenHeadcountMissing() {
        Shift shift = buildShift(2, "General", null);
        Department department = buildDepartment(3, "Ops");
        CompanyLocation location = buildLocation(4, "SATURDAY,SUNDAY");
        Employee requester = buildEmployee(30, EmployeeRole.SUPER_ADMIN, department, shift, location);
        Employee target = buildEmployee(31, EmployeeRole.DEPARTMENT_MANAGER, department, shift, location);
        LeaveRequest pendingLeave = buildLeave(102, target, LocalDate.of(2026, 5, 15), LocalDate.of(2026, 5, 15), LeaveStatus.PENDING, false);

        when(leaveRequestRepository.findDetailedById(102)).thenReturn(Optional.of(pendingLeave));
        when(employeeRepository.findById(30)).thenReturn(Optional.of(requester));
        when(employeeRepository.countByDepartmentIdAndShiftIdAndStatusAndRole(3, 2, EmployeeStatus.ACTIVE, EmployeeRole.EMPLOYEE)).thenReturn(4L);
        when(leaveRequestRepository.findApprovedLeavesForDepartmentShiftInRange(3, 2, LocalDate.of(2026, 5, 15), LocalDate.of(2026, 5, 15)))
            .thenReturn(List.of());
        when(holidayRepository.findByDateRangeAndLocation(LocalDate.of(2026, 5, 15), LocalDate.of(2026, 5, 15), 4)).thenReturn(List.of());

        LeaveImpactPreviewDTO preview = leaveService.previewLeaveImpact(102, 30);

        assertEquals("UNCONFIGURED", preview.getSeverity());
        assertFalse(preview.isConfigured());
        assertEquals(null, preview.getMinimumHeadcount());
    }

    @Test
    void previewLeaveImpact_blocksDepartmentManagerOutsideDepartment() {
        Shift shift = buildShift(2, "General", 2);
        CompanyLocation location = buildLocation(4, "SATURDAY,SUNDAY");
        Department sales = buildDepartment(3, "Sales");
        Department ops = buildDepartment(5, "Ops");
        Employee requester = buildEmployee(40, EmployeeRole.DEPARTMENT_MANAGER, sales, shift, location);
        Employee target = buildEmployee(41, EmployeeRole.EMPLOYEE, ops, shift, location);
        LeaveRequest pendingLeave = buildLeave(103, target, LocalDate.of(2026, 5, 16), LocalDate.of(2026, 5, 16), LeaveStatus.PENDING, false);

        when(leaveRequestRepository.findDetailedById(103)).thenReturn(Optional.of(pendingLeave));
        when(employeeRepository.findById(40)).thenReturn(Optional.of(requester));

        assertThrows(IllegalArgumentException.class, () -> leaveService.previewLeaveImpact(103, 40));
    }

    private LeaveRequest buildLeave(int id, Employee employee, LocalDate start, LocalDate end, LeaveStatus status, boolean halfDay) {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(id);
        leave.setEmployee(employee);
        leave.setStartDate(start);
        leave.setEndDate(end);
        leave.setStatus(status);
        leave.setHalfDay(halfDay);
        leave.setAppliedDays(halfDay ? 0.5 : 1.0);
        return leave;
    }

    private Employee buildEmployee(int id, EmployeeRole role, Department department, Shift shift, CompanyLocation location) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setRole(role);
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee.setDepartment(department);
        employee.setShift(shift);
        employee.setLocation(location);
        return employee;
    }

    private Department buildDepartment(int id, String name) {
        Department department = new Department();
        department.setId(id);
        department.setName(name);
        return department;
    }

    private Shift buildShift(int id, String name, Integer minimumHeadcount) {
        Shift shift = new Shift();
        shift.setId(id);
        shift.setShiftName(name);
        shift.setMinimumHeadcount(minimumHeadcount);
        return shift;
    }

    private CompanyLocation buildLocation(int id, String weekendDays) {
        CompanyLocation location = new CompanyLocation();
        location.setId(id);
        location.setWeekendDays(weekendDays);
        return location;
    }
}
