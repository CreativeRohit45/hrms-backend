package com.coresync.hrms.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveImpactBulkPreviewRequest {
    @NotEmpty
    private List<Integer> leaveRequestIds;
}
