// src/main/java/com/coresync/hrms/backend/service/EmployeeService.java
package com.coresync.hrms.backend.service;

import com.coresync.hrms.backend.dto.EmployeeCreateRequest;
import com.coresync.hrms.backend.dto.EmployeeResponse;
import com.coresync.hrms.backend.dto.EmployeeUpdateRequest;
import com.coresync.hrms.backend.dto.ProfileUpdateRequest;
import com.coresync.hrms.backend.entity.*;
import com.coresync.hrms.backend.enums.EmployeeRole;
import com.coresync.hrms.backend.enums.PaymentType;
import com.coresync.hrms.backend.enums.EmployeeStatus;
import com.coresync.hrms.backend.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final ShiftRepository shiftRepository;
    private final CompanyLocationRepository companyLocationRepository;
    private final PasswordEncoder passwordEncoder;
    private final @Lazy LeaveService leaveService;

    private static final String CODE_PREFIX = "CS-"; // Changed from LDK to CS for CoreSync
    private static final String DEFAULT_PASSWORD = "Welcome@123";

    @Transactional
    public EmployeeResponse createEmployee(EmployeeCreateRequest request) {

        if (request.getEmail() != null && employeeRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("An employee with email '" + request.getEmail() + "' already exists.");
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
            .orElseThrow(() -> new EntityNotFoundException("Department not found: ID " + request.getDepartmentId()));

        Shift shift = shiftRepository.findById(request.getShiftId())
            .orElseThrow(() -> new EntityNotFoundException("Shift not found: ID " + request.getShiftId()));

        CompanyLocation location = companyLocationRepository.findById(request.getLocationId())
            .orElseThrow(() -> new EntityNotFoundException("Company location not found: ID " + request.getLocationId()));

        String employeeCode = generateNextEmployeeCode();
        String hashedPassword = passwordEncoder.encode(DEFAULT_PASSWORD);

        Employee employee = Employee.builder()
            .employeeCode(employeeCode)
            .fullName(request.getFullName())
            .phone(request.getPhone())
            .email(request.getEmail())
            .dateOfBirth(request.getDateOfBirth())
            .dateOfJoining(request.getDateOfJoining())
            .department(department)
            .designation(request.getDesignation())
            .shift(shift)
            .location(location)
            .paymentType(request.getPaymentType())
            .hourlyRate(request.getHourlyRate())
            .overtimeRateMultiplier(request.getOvertimeRateMultiplier() != null ? request.getOvertimeRateMultiplier() : new BigDecimal("1.50"))
            .baseSalary(request.getBaseSalary())
            .hraPercentage(request.getHraPercentage() != null ? request.getHraPercentage() : new BigDecimal("40.00"))
            .pfPercentage(request.getPfPercentage() != null ? request.getPfPercentage() : new BigDecimal("12.00"))
            .status(EmployeeStatus.ACTIVE)
            .role(request.getRole())
            .passwordHash(hashedPassword)
            .build();

        Employee saved = employeeRepository.save(employee);
        log.info("[EmployeeService] Created employee: {} | Code: {} | Role: {}", saved.getFullName(), saved.getEmployeeCode(), saved.getRole());

        // Initialize per-type leave balances for the new employee
        // Strictly atomic: If this fails, the employee creation rolls back
        log.info("[EmployeeService] Triggering leave balance initialization for {}", saved.getEmployeeCode());
        leaveService.initializeBalancesForNewEmployee(saved, request.getInitialBalances());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> getAllEmployees(String requesterCode, Integer deptIdFilter, Pageable pageable) {
        Employee requester = employeeRepository.findByEmployeeCode(requesterCode)
            .orElseThrow(() -> new EntityNotFoundException("Requester not found: " + requesterCode));

        if (requester.getRole() == EmployeeRole.DEPARTMENT_MANAGER) {
            // Strict scoping: Manager only sees their department
            return employeeRepository.findByDepartmentId(requester.getDepartment().getId(), pageable)
                .map(this::toResponse);
        }

        // HR or Super Admin: Apply optional department filter
        if (deptIdFilter != null) {
            return employeeRepository.findByDepartmentId(deptIdFilter, pageable)
                .map(this::toResponse);
        }

        return employeeRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployeeById(Integer id) {
        Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found: ID " + id));
        return toResponse(employee);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployeeByCode(String code) {
        Employee employee = employeeRepository.findByEmployeeCode(code)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found with code: " + code));
        return toResponse(employee);
    }

    @Transactional
    public EmployeeResponse updateMyProfile(String employeeCode, ProfileUpdateRequest request) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + employeeCode));

        if (request.getPhone() != null) employee.setPhone(request.getPhone());
        if (request.getEmail() != null) employee.setEmail(request.getEmail());
        if (request.getPhotoUrl() != null) employee.setPhotoUrl(request.getPhotoUrl());

        Employee saved = employeeRepository.save(employee);
        log.info("[EmployeeService] Updated profile for: {}", employeeCode);
        return toResponse(saved);
    }

    @Transactional
    public void changePassword(String employeeCode, String currentPassword, String newPassword) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + employeeCode));

        if (!passwordEncoder.matches(currentPassword, employee.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        employee.setPasswordHash(passwordEncoder.encode(newPassword));
        employeeRepository.save(employee);
        log.info("[EmployeeService] Changed password for: {}", employeeCode);
    }

    @Transactional
    public EmployeeResponse updateEmployee(Integer id, EmployeeUpdateRequest request) {
        Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found: ID " + id));

        if (request.getEmail() != null && !request.getEmail().equals(employee.getEmail()) && employeeRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("An employee with email '" + request.getEmail() + "' already exists.");
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
            .orElseThrow(() -> new EntityNotFoundException("Department not found: ID " + request.getDepartmentId()));

        Shift shift = shiftRepository.findById(request.getShiftId())
            .orElseThrow(() -> new EntityNotFoundException("Shift not found: ID " + request.getShiftId()));

        CompanyLocation location = companyLocationRepository.findById(request.getLocationId())
            .orElseThrow(() -> new EntityNotFoundException("Company location not found: ID " + request.getLocationId()));

        employee.setFullName(request.getFullName());
        employee.setPhone(request.getPhone());
        employee.setEmail(request.getEmail());
        employee.setDateOfBirth(request.getDateOfBirth());
        employee.setDateOfJoining(request.getDateOfJoining());
        employee.setDepartment(department);
        employee.setDesignation(request.getDesignation());
        employee.setShift(shift);
        employee.setLocation(location);
        employee.setPaymentType(request.getPaymentType());
        employee.setHourlyRate(request.getHourlyRate());
        employee.setOvertimeRateMultiplier(request.getOvertimeRateMultiplier() != null ? request.getOvertimeRateMultiplier() : new BigDecimal("1.50"));
        employee.setBaseSalary(request.getBaseSalary());
        employee.setHraPercentage(request.getHraPercentage() != null ? request.getHraPercentage() : new BigDecimal("40.00"));
        employee.setPfPercentage(request.getPfPercentage() != null ? request.getPfPercentage() : new BigDecimal("12.00"));
        employee.setStatus(request.getStatus());
        employee.setRole(request.getRole());

        Employee saved = employeeRepository.save(employee);
        log.info("[EmployeeService] Updated employee manually via Admin. Code: {}", saved.getEmployeeCode());

        return toResponse(saved);
    }

    /**
     * Soft-delete an employee.
     * Instead of a hard DELETE (which would crash with FK constraint violations
     * from attendance_logs, leave_requests, payroll_records, etc.), we flip
     * the is_deleted flag. The @SQLRestriction on the Employee entity
     * automatically hides soft-deleted records from all standard queries.
     */
    @Transactional
    public void deleteEmployee(Integer id) {
        Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found: ID " + id));
        employee.setDeleted(true);
        employee.setStatus(EmployeeStatus.TERMINATED);
        employeeRepository.save(employee);
        log.info("[EmployeeService] Soft-deleted employee: {} (ID {})", employee.getEmployeeCode(), id);
    }

    private String generateNextEmployeeCode() {
        String lastCode = employeeRepository.findMaxEmployeeCode(CODE_PREFIX);
        int nextNumber = 1;
        if (lastCode != null) {
            try {
                String numericPart = lastCode.replace(CODE_PREFIX, "");
                nextNumber = Integer.parseInt(numericPart) + 1;
            } catch (NumberFormatException e) {
                log.warn("[EmployeeService] Could not parse last code '{}' — defaulting to count+1", lastCode);
                nextNumber = (int) employeeRepository.count() + 1;
            }
        }
        return String.format("%s%04d", CODE_PREFIX, nextNumber);
    }

    public EmployeeResponse toResponse(Employee e) {
        return EmployeeResponse.builder()
            .id(e.getId())
            .employeeCode(e.getEmployeeCode())
            .fullName(e.getFullName())
            .phone(e.getPhone())
            .email(e.getEmail())
            .photoUrl(e.getPhotoUrl())
            .dateOfBirth(e.getDateOfBirth())
            .dateOfJoining(e.getDateOfJoining())
            .departmentId(e.getDepartment().getId())
            .departmentName(e.getDepartment().getName())
            .designation(e.getDesignation())
            .shiftId(e.getShift().getId())
            .shiftName(e.getShift().getShiftName())
            .shiftStartTime(e.getShift().getStartTime())
            .shiftEndTime(e.getShift().getEndTime())
            .locationId(e.getLocation().getId())
            .locationName(e.getLocation().getLocationName())
            .paymentType(e.getPaymentType().name())
            .hourlyRate(e.getHourlyRate())
            .overtimeRateMultiplier(e.getOvertimeRateMultiplier())
            .status(e.getStatus().name())
            .role(e.getRole().name())
            .baseSalary(e.getBaseSalary())
            .hraPercentage(e.getHraPercentage())
            .pfPercentage(e.getPfPercentage())
            .createdAt(e.getCreatedAt())
            .updatedAt(e.getUpdatedAt())
            .build();
    }
}