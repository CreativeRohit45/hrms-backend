package com.coresync.hrms.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitialLeaveBalanceDTO {
    private Integer leaveTypeId;
    private Double balance;
}
