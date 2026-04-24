package com.coresync.hrms.backend.controller;

import com.coresync.hrms.backend.dto.NotificationDto;
import com.coresync.hrms.backend.entity.Employee;
import com.coresync.hrms.backend.repository.EmployeeRepository;
import com.coresync.hrms.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final EmployeeRepository employeeRepository;

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getMyNotifications(Authentication auth) {
        Employee employee = employeeRepository.findByEmployeeCode(auth.getName())
            .orElseThrow(() -> new RuntimeException("Employee not found"));

        List<NotificationDto> notifications = notificationRepository.findByEmployeeIdOrderByCreatedAtDesc(employee.getId())
            .stream()
            .map(n -> NotificationDto.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build())
            .collect(Collectors.toList());

        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(Authentication auth) {
        Employee employee = employeeRepository.findByEmployeeCode(auth.getName())
            .orElseThrow(() -> new RuntimeException("Employee not found"));
        return ResponseEntity.ok(notificationRepository.countByEmployeeIdAndIsReadFalse(employee.getId()));
    }

    @PutMapping("/mark-all-read")
    @Transactional
    public ResponseEntity<Void> markAllAsRead(Authentication auth) {
        Employee employee = employeeRepository.findByEmployeeCode(auth.getName())
            .orElseThrow(() -> new RuntimeException("Employee not found"));
        notificationRepository.markAllAsReadByEmployeeId(employee.getId());
        return ResponseEntity.ok().build();
    }
}
