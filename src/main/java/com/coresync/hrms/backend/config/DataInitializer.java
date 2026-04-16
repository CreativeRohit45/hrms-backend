package com.coresync.hrms.backend.config;

import com.coresync.hrms.backend.entity.Holiday;
import com.coresync.hrms.backend.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final HolidayRepository holidayRepository;

    @Override
    public void run(String... args) {
        if (holidayRepository.count() == 0) {
            log.info("[DataInitializer] Seeding initial holidays...");
            List<Holiday> holidays = List.of(
                Holiday.builder().name("Dr. Ambedkar Jayanti").holidayDate(LocalDate.of(2026, 4, 14)).description("Birthday of Dr. B.R. Ambedkar").build(),
                Holiday.builder().name("May Day").holidayDate(LocalDate.of(2026, 5, 1)).description("International Workers' Day").build(),
                Holiday.builder().name("Independence Day").holidayDate(LocalDate.of(2026, 8, 15)).description("Indian Independence Day").build(),
                Holiday.builder().name("Gandhi Jayanti").holidayDate(LocalDate.of(2026, 10, 2)).description("Birthday of Mahatma Gandhi").build(),
                Holiday.builder().name("Diwali").holidayDate(LocalDate.of(2026, 11, 5)).description("Festival of Lights").build()
            );
            holidayRepository.saveAll(holidays);
            log.info("[DataInitializer] Seeded {} holidays.", holidays.size());
        }
    }
}
