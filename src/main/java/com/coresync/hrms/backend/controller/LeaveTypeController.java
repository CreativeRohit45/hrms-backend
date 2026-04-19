package com.coresync.hrms.backend.controller;

import com.coresync.hrms.backend.entity.LeaveType;
import com.coresync.hrms.backend.repository.LeaveTypeRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/leaves/admin/types")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
public class LeaveTypeController {

    private final LeaveTypeRepository leaveTypeRepository;

    @GetMapping
    public ResponseEntity<List<LeaveType>> getAll() {
        return ResponseEntity.ok(leaveTypeRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<LeaveType> create(@Valid @RequestBody LeaveType leaveType) {
        if (leaveTypeRepository.existsByCode(leaveType.getCode())) {
            throw new IllegalArgumentException("Leave type code '" + leaveType.getCode() + "' already exists.");
        }
        return ResponseEntity.ok(leaveTypeRepository.save(leaveType));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LeaveType> update(@PathVariable Integer id, @Valid @RequestBody LeaveType request) {
        LeaveType existing = leaveTypeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Leave type not found"));

        existing.setName(request.getName());
        existing.setActive(request.isActive());
        existing.setPaid(request.isPaid());
        existing.setRequiresAttachment(request.isRequiresAttachment());
        existing.setAttachmentThresholdDays(request.getAttachmentThresholdDays());
        existing.setMaxDaysPerRequest(request.getMaxDaysPerRequest());
        existing.setRequiresProbationCompletion(request.isRequiresProbationCompletion());
        existing.setAllowNegativeBalance(request.isAllowNegativeBalance());
        existing.setDefaultAnnualQuota(request.getDefaultAnnualQuota());
        existing.setMonthlyAccrualRate(request.getMonthlyAccrualRate());
        existing.setCarryForwardAllowed(request.isCarryForwardAllowed());
        existing.setMaxCarryForwardDays(request.getMaxCarryForwardDays());
        existing.setAllowedGenders(request.getAllowedGenders());

        return ResponseEntity.ok(leaveTypeRepository.save(existing));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        LeaveType existing = leaveTypeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Leave type not found"));
        
        // Soft delete by setting inactive
        existing.setActive(false);
        leaveTypeRepository.save(existing);
        return ResponseEntity.noContent().build();
    }
}
