package com.coresync.hrms.backend.enums;

public enum LeaveTransactionType {
    ACCRUAL,              // Monthly or lump-sum credit
    DEDUCTION,            // Applied for leave (escrow)
    REFUND,               // Rejected, cancelled, or revoked leave
    CARRY_FORWARD,        // Year-end carry-over from previous year
    EXPIRY,               // Year-end expiry of unused balance
    MANUAL_ADJUSTMENT     // HR manual credit/debit
}
