package com.coresync.hrms.backend.controller;

import com.coresync.hrms.backend.service.SystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        return ResponseEntity.ok(systemService.getSystemInfo());
    }
}
