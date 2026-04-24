// src/main/java/com/coresync/hrms/backend/seeder/DatabaseSeeder.java
package com.coresync.hrms.backend.seeder;

import com.coresync.hrms.backend.entity.*;
import com.coresync.hrms.backend.enums.*;
import com.coresync.hrms.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final ShiftRepository shiftRepository;
    private final CompanyLocationRepository companyLocationRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String SUPER_ADMIN_CODE = "LDK-0001";
    private static final String DEFAULT_SEED_PASSWORD = "Admin@1234";

    @Override
    @Transactional
    public void run(String... args) {
        seedLeaveTypes();

        if (employeeRepository.existsByRole(EmployeeRole.SUPER_ADMIN)) {
            log.info("[Seeder] SUPER_ADMIN already exists — skipping employee seed.");
            return;
        }

        log.info("[Seeder] No SUPER_ADMIN found. Seeding default data...");

        // 1. Company Location
        CompanyLocation location = CompanyLocation.builder()
            .locationName("CoreSync Industries - Main Gate")
            .address("CoreSync Industries, Main Gate, Pune, Maharashtra, India")
            .latitude(new BigDecimal("18.5204303"))
            .longitude(new BigDecimal("73.8567437"))
            .allowedRadiusMeters((short) 150)
            .isActive(true)
            .build();
        location = companyLocationRepository.save(location);

        // 2. Department
        Department department = Department.builder()
            .name("Administration")
            .description("Company administration and HR oversight")
            .isActive(true)
            .build();
        department = departmentRepository.save(department);

        // 3. Shift
        Shift shift = Shift.builder()
            .shiftName("General Shift")
            .startTime(LocalTime.of(9, 0))
            .endTime(LocalTime.of(18, 0))
            .unpaidBreakMinutes((short) 60)
            .isOvernight(false)
            .standardHours(new BigDecimal("8.00"))
            .gracePeriodMinutes((short) 15)
            .isActive(true)
            .build();
        shift = shiftRepository.save(shift);

        // 4. Super Admin Employee
        String seedPassword = System.getenv("APP_SEED_PASSWORD");
        if (seedPassword == null || seedPassword.isBlank()) {
            seedPassword = DEFAULT_SEED_PASSWORD;
        }
        String hashedPassword = passwordEncoder.encode(seedPassword);

        Employee superAdmin = Employee.builder()
            .employeeCode(SUPER_ADMIN_CODE)
            .fullName("System Administrator")
            .email("admin@coresync.com")
            .phone("9999999999")
            .dateOfJoining(LocalDate.now())
            .department(department)
            .designation("System Administrator")
            .shift(shift)
            .location(location)
            .paymentType(PaymentType.FIXED_MONTHLY)
            .hourlyRate(new BigDecimal("500.00"))
            .overtimeRateMultiplier(new BigDecimal("1.50"))
            .status(EmployeeStatus.ACTIVE)
            .role(EmployeeRole.SUPER_ADMIN)
            .passwordHash(hashedPassword)
            .build();
            
        employeeRepository.save(superAdmin);

        log.info("═══════════════════════════════════════════════════════");
        log.info("[Seeder] ✅ SUPER_ADMIN seeded successfully.");
        log.info("[Seeder]    Employee Code : {}", SUPER_ADMIN_CODE);
        log.info("[Seeder]    Email         : admin@coresync.com");
        log.info("[Seeder]    Password      : ******* (set via APP_SEED_PASSWORD env or default)");
        log.info("[Seeder]    ⚠ Change this password immediately after first login!");
        log.info("═══════════════════════════════════════════════════════");
    }

    private void seedLeaveTypes() {
        if (leaveTypeRepository.count() > 0) {
            log.info("[Seeder] Leave types already exist — skipping leave type seed.");
            return;
        }

        log.info("[Seeder] Seeding default leave types...");

        List<LeaveType> types = List.of(
            LeaveType.builder()
                .name("Casual Leave").code("CL")
                .isPaid(true).requiresAttachment(false).attachmentThresholdDays(0)
                .allowedGenders(null).maxDaysPerRequest(3)
                .requiresProbationCompletion(false).allowNegativeBalance(false)
                .defaultAnnualQuota(12.0).monthlyAccrualRate(1.0)
                .isCarryForwardAllowed(false).maxCarryForwardDays(0)
                .isActive(true)
                .build(),
            LeaveType.builder()
                .name("Sick Leave").code("SL")
                .isPaid(true).requiresAttachment(true).attachmentThresholdDays(2)
                .allowedGenders(null).maxDaysPerRequest(null)
                .requiresProbationCompletion(false).allowNegativeBalance(false)
                .defaultAnnualQuota(12.0).monthlyAccrualRate(1.0)
                .isCarryForwardAllowed(false).maxCarryForwardDays(0)
                .isActive(true)
                .build(),
            LeaveType.builder()
                .name("Earned Leave").code("EL")
                .isPaid(true).requiresAttachment(false).attachmentThresholdDays(0)
                .allowedGenders(null).maxDaysPerRequest(null)
                .requiresProbationCompletion(true).allowNegativeBalance(false)
                .defaultAnnualQuota(15.0).monthlyAccrualRate(1.25)
                .isCarryForwardAllowed(true).maxCarryForwardDays(5)
                .isActive(true)
                .build(),
            LeaveType.builder()
                .name("Leave Without Pay").code("LWP")
                .isPaid(false).requiresAttachment(false).attachmentThresholdDays(0)
                .allowedGenders(null).maxDaysPerRequest(null)
                .requiresProbationCompletion(false).allowNegativeBalance(false)
                .defaultAnnualQuota(0).monthlyAccrualRate(0)
                .isCarryForwardAllowed(false).maxCarryForwardDays(0)
                .isActive(true)
                .build(),
            LeaveType.builder()
                .name("Compensatory Off").code("CMP")
                .isPaid(true).requiresAttachment(false).attachmentThresholdDays(0)
                .allowedGenders(null).maxDaysPerRequest(1)
                .requiresProbationCompletion(false).allowNegativeBalance(false)
                .defaultAnnualQuota(0).monthlyAccrualRate(0)
                .isCarryForwardAllowed(false).maxCarryForwardDays(0)
                .isActive(true)
                .build(),
            LeaveType.builder()
                .name("Maternity Leave").code("ML")
                .isPaid(true).requiresAttachment(true).attachmentThresholdDays(0)
                .allowedGenders("FEMALE").maxDaysPerRequest(null)
                .requiresProbationCompletion(false).allowNegativeBalance(false)
                .defaultAnnualQuota(182.0).monthlyAccrualRate(0)
                .isCarryForwardAllowed(false).maxCarryForwardDays(0)
                .isActive(true)
                .build(),
            LeaveType.builder()
                .name("Paternity Leave").code("PL")
                .isPaid(true).requiresAttachment(false).attachmentThresholdDays(0)
                .allowedGenders("MALE").maxDaysPerRequest(null)
                .requiresProbationCompletion(false).allowNegativeBalance(false)
                .defaultAnnualQuota(15.0).monthlyAccrualRate(0)
                .isCarryForwardAllowed(false).maxCarryForwardDays(0)
                .isActive(true)
                .build()
        );

        leaveTypeRepository.saveAll(types);
        log.info("[Seeder] ✅ {} leave types seeded: {}", types.size(),
            types.stream().map(t -> t.getCode() + "(" + t.getName() + ")").toList());
    }
}