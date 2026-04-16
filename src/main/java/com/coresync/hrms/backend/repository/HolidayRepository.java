package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Integer> {

    @Query("SELECT h FROM Holiday h WHERE h.holidayDate >= :date ORDER BY h.holidayDate ASC")
    List<Holiday> findUpcomingHolidays(@Param("date") LocalDate date);

    boolean existsByHolidayDate(LocalDate holidayDate);

    @Query("SELECT COUNT(h) > 0 FROM Holiday h WHERE h.holidayDate = :date AND (h.location IS NULL OR h.location.id = :locationId)")
    boolean existsByHolidayDateAndLocation(@Param("date") LocalDate date, @Param("locationId") Integer locationId);

    /**
     * Find holidays in a date range that apply to a given location (or are global).
     * Used by leave day calculation to exclude holidays.
     */
    @Query("SELECT h FROM Holiday h WHERE h.holidayDate BETWEEN :start AND :end " +
           "AND (h.location IS NULL OR h.location.id = :locationId)")
    List<Holiday> findByDateRangeAndLocation(
        @Param("start") LocalDate start,
        @Param("end") LocalDate end,
        @Param("locationId") Integer locationId
    );

    List<Holiday> findByHolidayDateBetween(LocalDate start, LocalDate end);
}
