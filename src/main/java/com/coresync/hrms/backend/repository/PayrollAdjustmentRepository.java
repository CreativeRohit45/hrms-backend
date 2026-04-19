package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.PayrollAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PayrollAdjustmentRepository extends JpaRepository<PayrollAdjustment, Long> {
    
    List<PayrollAdjustment> findByPayrollRecordIdAndIsDeletedFalse(Integer payrollRecordId);
    
}
