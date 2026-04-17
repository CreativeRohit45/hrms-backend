package com.coresync.hrms.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DashboardStatsDTO {
    // Leave Stats (aggregated from LeaveBalance table)
    private BigDecimal leavesTaken;
    private BigDecimal leavesRemaining;
    private BigDecimal totalLeaveQuota;
    private List<LeaveBalanceDetail> leaveBalances;

    // Attendance Summary (Current Month)
    private long presentDays;
    private long lateDays;
    private long absentDays;
    private long leaveDays;
    private Long totalWorkedMinutes;

    // Current Session (for Punch In/Out button)
    private AttendanceSessionDTO currentSession;

    // Today's completion state
    private boolean todayCompleted;
    private Integer todayTotalMinutes;

    // Company Settings
    private String weekendDays;

    // Lists
    private List<HolidayDTO> upcomingHolidays;
    private List<AttendanceLogDTO> recentLogs;
    private List<LocalDate> allHolidays;

    @Data
    @Builder
    public static class AttendanceSessionDTO {
        private Long id;
        private LocalDateTime punchInTime;
        private boolean active;
    }

    @Data
    @Builder
    public static class HolidayDTO {
        private String name;
        private LocalDate date;
        private String description;
    }

    @Data
    @Builder
    public static class AttendanceLogDTO {
        private LocalDate date;
        private String status;
        private LocalDateTime punchIn;
        private LocalDateTime punchOut;
    }

    @Data
    @Builder
    public static class LeaveBalanceDetail {
        private String leaveTypeName;
        private String leaveTypeCode;
        private double allocated;
        private double used;
        private double balance;
    }
}
