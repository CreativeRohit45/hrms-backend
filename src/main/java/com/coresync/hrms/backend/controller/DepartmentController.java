package com.coresync.hrms.backend.controller;

import com.coresync.hrms.backend.entity.Department;
import com.coresync.hrms.backend.repository.DepartmentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<Department>> getAll() {
        return ResponseEntity.ok(departmentRepository.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Department> getById(@PathVariable Integer id) {
        Department dept = departmentRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Department not found: " + id));
        return ResponseEntity.ok(dept);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Department> create(@RequestBody Department department) {
        Department saved = departmentRepository.save(department);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Department> update(@PathVariable Integer id, @RequestBody Department body) {
        Department existing = departmentRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Department not found: " + id));

        existing.setName(body.getName());
        existing.setDescription(body.getDescription());
        existing.setActive(body.isActive());

        Department saved = departmentRepository.save(existing);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        if (!departmentRepository.existsById(id)) {
            throw new EntityNotFoundException("Department not found: " + id);
        }
        departmentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
