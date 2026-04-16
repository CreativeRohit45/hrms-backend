// src/main/java/com/coresync/hrms/backend/exception/LocationVerificationException.java
package com.coresync.hrms.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class LocationVerificationException extends RuntimeException {

    private final double distanceMeters;
    private final double allowedRadiusMeters;

    public LocationVerificationException(double distanceMeters, double allowedRadiusMeters) {
        super(String.format(
            "Punch rejected: You are %.1f meters from the office. Allowed radius is %.0f meters.",
            distanceMeters, allowedRadiusMeters
        ));
        this.distanceMeters    = distanceMeters;
        this.allowedRadiusMeters = allowedRadiusMeters;
    }

    public double getDistanceMeters()     { return distanceMeters; }
    public double getAllowedRadiusMeters() { return allowedRadiusMeters; }
}