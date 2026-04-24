package com.coresync.hrms.backend.service;

import com.coresync.hrms.backend.entity.Employee;
import com.coresync.hrms.backend.entity.LeaveBalance;
import com.coresync.hrms.backend.entity.LeaveType;
import com.coresync.hrms.backend.enums.EmployeeStatus;
import com.coresync.hrms.backend.enums.LeaveTransactionType;
import com.coresync.hrms.backend.repository.EmployeeRepository;
import com.coresync.hrms.backend.repository.LeaveBalanceAuditRepository;
import com.coresync.hrms.backend.repository.LeaveBalanceRepository;
import com.coresync.hrms.backend.repository.LeaveTypeRepository;
import com.coresync.hrms.backend.repository.SystemSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LeaveAccrualServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @Mock
    private LeaveBalanceAuditRepository auditRepository;

    @Mock
    private SystemSettingsRepository systemSettingsRepository;

    @InjectMocks
    private LeaveAccrualService leaveAccrualService;

    @Test
    void testAccrueForEmployee_Success() {
        // Arrange
        Integer employeeId = 1;
        int year = 2026;
        String month = "APRIL";

        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee.setGender("MALE");
        employee.setDateOfJoining(java.time.LocalDate.of(2025, 1, 1));
        employee.setEmployeeCode("EMP001");

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        LeaveType leaveType = new LeaveType();
        leaveType.setId(10);
        leaveType.setName("Annual Leave");
        leaveType.setCode("AL");
        leaveType.setMonthlyAccrualRate(1.5);
        leaveType.setMaxCarryForwardDays(30);
        leaveType.setDefaultAnnualQuota(18.0);
        leaveType.setAllowedGenders(null);

        List<LeaveType> accrualTypes = List.of(leaveType);

        LeaveBalance balance = new LeaveBalance();
        balance.setEmployee(employee);
        balance.setLeaveType(leaveType);
        balance.setBalance(10.0);
        balance.setAllocated(10.0);

        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, leaveType.getId(), year))
                .thenReturn(Optional.of(balance));

        // Act
        leaveAccrualService.accrueForEmployee(employeeId, year, month, accrualTypes);

        // Assert
        verify(leaveBalanceRepository, times(1)).save(balance);
        verify(auditRepository, times(1)).save(argThat(audit -> 
            audit.getTransactionType() == LeaveTransactionType.ACCRUAL &&
            audit.getAmount() == 1.5 &&
            audit.getReason().contains("APRIL") && 
            audit.getReason().contains("2026")
        ));
    }
}
