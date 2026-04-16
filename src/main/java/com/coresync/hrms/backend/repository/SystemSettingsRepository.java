package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Integer> {
    Optional<SystemSettings> findBySettingKey(String settingKey);
}
