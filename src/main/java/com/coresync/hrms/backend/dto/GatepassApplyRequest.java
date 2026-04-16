// src/main/java/com/coresync/hrms/backend/dto/GatepassApplyRequest.java
package com.coresync.hrms.backend.dto;

import com.coresync.hrms.backend.enums.GatepassType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class GatepassApplyRequest {
    @NotNull(message = "Requested out time is required")
    @Future(message = "Requested out time must be in the future")
    private LocalDateTime requestedOutTime;

    @NotNull(message = "Requested in time is required")
    @Future(message = "Requested in time must be in the future")
    private LocalDateTime requestedInTime;

    @NotNull(message = "Gatepass type is required")
    private GatepassType gatepassType;

    @NotBlank(message = "Reason is required")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
