// src/main/java/com/coresync/hrms/backend/repository/PayrollRecordRepository.java
package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.PayrollRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

import java.util.List;
import java.util.Optional;

public interface PayrollRecordRepository extends JpaRepository<PayrollRecord, Integer> {
    Optional<PayrollRecord> findByEmployeeIdAndPayrollYearAndPayrollMonth(
        Integer employeeId, int year, int month);

    List<PayrollRecord> findByPayrollYearAndPayrollMonth(int year, int month);

    List<PayrollRecord> findByEmployeeIdOrderByPayrollYearDescPayrollMonthDesc(Integer employeeId);
}