package com.coresync.hrms.backend.exception;

import lombok.Getter;

@Getter
public class InsufficientBalanceException extends RuntimeException {

    private final double available;
    private final double requested;
    private final String leaveTypeCode;

    public InsufficientBalanceException(double available, double requested, String leaveTypeCode) {
        super(String.format("Insufficient %s balance. Available: %.1f days, Requested: %.1f days.",
            leaveTypeCode, available, requested));
        this.available = available;
        this.requested = requested;
        this.leaveTypeCode = leaveTypeCode;
    }
}
