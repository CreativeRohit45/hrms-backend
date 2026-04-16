// src/main/java/com/coresync/hrms/backend/dto/PunchInRequest.java
package com.coresync.hrms.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PunchInRequest {
    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0",  message = "Invalid latitude")
    @DecimalMax(value = "90.0",   message = "Invalid latitude")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Invalid longitude")
    @DecimalMax(value = "180.0",  message = "Invalid longitude")
    private Double longitude;
}