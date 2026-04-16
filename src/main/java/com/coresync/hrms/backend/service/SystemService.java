package com.coresync.hrms.backend.service;

import com.coresync.hrms.backend.entity.SystemSettings;
import com.coresync.hrms.backend.repository.SystemSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemService {

    private final SystemSettingsRepository systemSettingsRepository;

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("serverTime", LocalDateTime.now());
        info.put("payrollLockDate", getPayrollLockDate());
        return info;
    }

    public LocalDate getPayrollLockDate() {
        return systemSettingsRepository.findBySettingKey("PAYROLL_LOCK_DATE")
            .map(s -> LocalDate.parse(s.getSettingValue()))
            .orElse(LocalDate.of(1970, 1, 1)); // Default to epoch so nothing is locked
    }
}
