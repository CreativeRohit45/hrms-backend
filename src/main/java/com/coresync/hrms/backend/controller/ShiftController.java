package com.coresync.hrms.backend.controller;

import com.coresync.hrms.backend.entity.Shift;
import com.coresync.hrms.backend.repository.ShiftRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftRepository shiftRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<Shift>> getAll() {
        return ResponseEntity.ok(shiftRepository.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Shift> getById(@PathVariable Integer id) {
        Shift shift = shiftRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Shift not found: " + id));
        return ResponseEntity.ok(shift);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Shift> create(@RequestBody Shift shift) {
        Shift saved = shiftRepository.save(shift);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Shift> update(@PathVariable Integer id, @RequestBody Shift body) {
        Shift existing = shiftRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Shift not found: " + id));

        existing.setShiftName(body.getShiftName());
        existing.setStartTime(body.getStartTime());
        existing.setEndTime(body.getEndTime());
        existing.setUnpaidBreakMinutes(body.getUnpaidBreakMinutes());
        existing.setOvernight(body.isOvernight());
        existing.setStandardHours(body.getStandardHours());
        existing.setGracePeriodMinutes(body.getGracePeriodMinutes());
        existing.setActive(body.isActive());

        Shift saved = shiftRepository.save(existing);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        if (!shiftRepository.existsById(id)) {
            throw new EntityNotFoundException("Shift not found: " + id);
        }
        shiftRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
