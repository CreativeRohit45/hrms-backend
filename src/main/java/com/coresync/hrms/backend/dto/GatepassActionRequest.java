// src/main/java/com/coresync/hrms/backend/dto/GatepassActionRequest.java
package com.coresync.hrms.backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GatepassActionRequest {
    @Size(max = 500)
    private String rejectionReason;
}