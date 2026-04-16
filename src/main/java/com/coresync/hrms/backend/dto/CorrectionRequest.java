package com.coresync.hrms.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CorrectionRequest {
    @NotNull(message = "Requested punch-in time is required")
    private LocalDateTime requestedPunchInTime;

    @NotNull(message = "Requested punch-out time is required")
    private LocalDateTime requestedPunchOutTime;

    @NotBlank(message = "A reason is required")
    private String reason;
}