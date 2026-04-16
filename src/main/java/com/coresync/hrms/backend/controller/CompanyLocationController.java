// src/main/java/com/coresync/hrms/backend/controller/CompanyLocationController.java
package com.coresync.hrms.backend.controller;

import com.coresync.hrms.backend.entity.CompanyLocation;
import com.coresync.hrms.backend.repository.CompanyLocationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/company-locations")
@RequiredArgsConstructor
public class CompanyLocationController {

    private final CompanyLocationRepository locationRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<CompanyLocation>> getAll() {
        return ResponseEntity.ok(locationRepository.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<CompanyLocation> getById(@PathVariable Integer id) {
        CompanyLocation loc = locationRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Location not found: " + id));
        return ResponseEntity.ok(loc);
    }

    /**
     * PATCH /api/v1/company-locations/{id}/coordinates
     * Updates the lat/lng/radius of an existing location.
     * Body: { "latitude": 28.1234567, "longitude": 77.1234567, "radiusMeters": 3000 }
     */
    @PatchMapping("/{id}/coordinates")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<CompanyLocation> updateCoordinates(
            @PathVariable Integer id,
            @RequestBody Map<String, Object> body) {

        CompanyLocation loc = locationRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Location not found: " + id));

        if (body.containsKey("latitude")) {
            loc.setLatitude(new BigDecimal(body.get("latitude").toString()));
        }
        if (body.containsKey("longitude")) {
            loc.setLongitude(new BigDecimal(body.get("longitude").toString()));
        }
        if (body.containsKey("radiusMeters")) {
            loc.setAllowedRadiusMeters(Integer.parseInt(body.get("radiusMeters").toString()));
        }
        if (body.containsKey("locationName")) {
            loc.setLocationName(body.get("locationName").toString());
        }
        if (body.containsKey("address")) {
            loc.setAddress(body.get("address").toString());
        }
        if (body.containsKey("weekendDays")) {
            loc.setWeekendDays(body.get("weekendDays").toString());
        }

        CompanyLocation saved = locationRepository.save(loc);
        return ResponseEntity.ok(saved);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<CompanyLocation> create(@RequestBody CompanyLocation location) {
        CompanyLocation saved = locationRepository.save(location);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<CompanyLocation> update(@PathVariable Integer id, @RequestBody CompanyLocation body) {
        CompanyLocation loc = locationRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Location not found: " + id));

        loc.setLocationName(body.getLocationName());
        loc.setAddress(body.getAddress());
        loc.setLatitude(body.getLatitude());
        loc.setLongitude(body.getLongitude());
        loc.setAllowedRadiusMeters(body.getAllowedRadiusMeters());
        loc.setWeekendDays(body.getWeekendDays());
        loc.setActive(body.isActive());

        CompanyLocation saved = locationRepository.save(loc);
        return ResponseEntity.ok(saved);
    }
}
