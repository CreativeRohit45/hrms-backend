package com.coresync.hrms.backend.service;

import com.coresync.hrms.backend.dto.PayrollResult;
import com.coresync.hrms.backend.entity.PayrollAdjustment;
import com.coresync.hrms.backend.entity.PayrollRecord;
import com.coresync.hrms.backend.enums.PayrollAdjustmentType;
import com.coresync.hrms.backend.enums.PayrollStatus;
import com.coresync.hrms.backend.repository.PayrollAdjustmentRepository;
import com.coresync.hrms.backend.repository.PayrollRecordRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollAdjustmentService {

    private final PayrollAdjustmentRepository adjustmentRepository;
    private final PayrollRecordRepository recordRepository;
    private final PayrollCalculationService calculationService;
    private final PayrollPersistenceService persistenceService;

    @Transactional
    public void addAdjustment(Integer recordId, PayrollAdjustmentType type, BigDecimal amount, String description, String createdBy) {
        PayrollRecord record = recordRepository.findById(recordId)
            .orElseThrow(() -> new EntityNotFoundException("Payroll record not found"));

        validateNotLocked(record);

        PayrollAdjustment adjustment = PayrollAdjustment.builder()
            .payrollRecord(record)
            .type(type)
            .amount(amount)
            .description(description)
            .createdBy(createdBy)
            .build();

        adjustmentRepository.save(adjustment);
        log.info("Adjustment added to record {}: {} - ₹{}", recordId, type, amount);
        
        // Auto-trigger recalculation to keep net pay in sync
        recalculateRecord(recordId);
    }

    @Transactional
    public void deleteAdjustment(Long adjustmentId) {
        PayrollAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new EntityNotFoundException("Adjustment not found"));

        validateNotLocked(adjustment.getPayrollRecord());

        // Soft delete
        adjustment.setDeleted(true);
        adjustmentRepository.save(adjustment);
        log.info("Adjustment {} soft-deleted", adjustmentId);

        // Auto-trigger recalculation
        recalculateRecord(adjustment.getPayrollRecord().getId());
    }

    @Transactional
    public void recalculateRecord(Integer recordId) {
        PayrollRecord record = recordRepository.findById(recordId)
            .orElseThrow(() -> new EntityNotFoundException("Payroll record not found"));

        validateNotLocked(record);

        log.info("Recalculating payroll record {} for employee {}", recordId, record.getEmployee().getEmployeeCode());

        // 1. Run pure calculation (this will pick up current attendance AND non-deleted adjustments)
        PayrollResult result = calculationService.calculateMonthlyPayroll(
            record.getEmployee().getId(), 
            record.getPayrollMonth(), 
            record.getPayrollYear()
        );

        // 2. Persist updated values (overwrites existing fields)
        persistenceService.saveSingleRecord(record, result);
    }

    private void validateNotLocked(PayrollRecord record) {
        if (record.getStatus() == PayrollStatus.LOCKED || record.getStatus() == PayrollStatus.PAID) {
            throw new IllegalStateException("Cannot modify or recalculate a locked or paid payroll record.");
        }
    }
}
