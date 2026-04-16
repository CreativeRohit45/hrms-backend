package com.coresync.hrms.backend.controller;

import com.coresync.hrms.backend.entity.Holiday;
import com.coresync.hrms.backend.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/holidays")
@RequiredArgsConstructor
@Slf4j
public class HolidayController {

    private final HolidayRepository holidayRepository;

    @GetMapping
    public ResponseEntity<List<Holiday>> getAllHolidays() {
        return ResponseEntity.ok(holidayRepository.findAll());
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> uploadHolidays(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File is empty"));
        }

        try (InputStream in = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(in)) {

            Sheet sheet = workbook.getSheetAt(0);
            List<Holiday> holidaysToSave = new ArrayList<>();

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Skip header

                Cell dateCell = row.getCell(0);
                Cell nameCell = row.getCell(1);
                Cell descCell = row.getCell(2);

                if (nameCell == null || nameCell.getCellType() == CellType.BLANK) continue;

                LocalDate holidayDate = null;
                if (dateCell != null) {
                    if (dateCell.getCellType() == CellType.NUMERIC) {
                        holidayDate = dateCell.getLocalDateTimeCellValue().toLocalDate();
                    } else if (dateCell.getCellType() == CellType.STRING) {
                        try {
                            holidayDate = LocalDate.parse(dateCell.getStringCellValue().trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        } catch (DateTimeParseException e) {
                            try {
                                holidayDate = LocalDate.parse(dateCell.getStringCellValue().trim(), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                            } catch (DateTimeParseException ignore) {}
                        }
                    }
                }

                if (holidayDate == null) continue;

                String name = nameCell.getStringCellValue();
                String desc = descCell != null && descCell.getCellType() == CellType.STRING ? descCell.getStringCellValue() : "";

                Holiday holiday = Holiday.builder()
                        .holidayDate(holidayDate)
                        .name(name)
                        .description(desc)
                        .build();

                holidaysToSave.add(holiday);
            }

            holidayRepository.saveAll(holidaysToSave);
            return ResponseEntity.ok(Map.of("message", "Successfully uploaded " + holidaysToSave.size() + " holidays"));

        } catch (Exception e) {
            log.error("Error parsing Excel file", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Failed to parse Excel file: " + e.getMessage()));
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Holiday> create(@RequestBody Holiday holiday) {
        Holiday saved = holidayRepository.save(holiday);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Holiday> update(@PathVariable Integer id, @RequestBody Holiday body) {
        Holiday existing = holidayRepository.findById(id)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Holiday not found: " + id));
        existing.setName(body.getName());
        existing.setHolidayDate(body.getHolidayDate());
        existing.setDescription(body.getDescription());
        Holiday saved = holidayRepository.save(existing);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        if (!holidayRepository.existsById(id)) {
            throw new jakarta.persistence.EntityNotFoundException("Holiday not found: " + id);
        }
        holidayRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
