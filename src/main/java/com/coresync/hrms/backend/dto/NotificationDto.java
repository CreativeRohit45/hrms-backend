package com.coresync.hrms.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class NotificationDto {
    private Long id;
    private String title;
    private String message;
    private boolean isRead;
    private LocalDateTime createdAt;
}
