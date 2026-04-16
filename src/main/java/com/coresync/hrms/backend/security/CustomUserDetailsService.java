// src/main/java/com/coresync/hrms/backend/security/CustomUserDetailsService.java
package com.coresync.hrms.backend.security;

import com.coresync.hrms.backend.entity.Employee;
import com.coresync.hrms.backend.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final EmployeeRepository employeeRepository;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {

        Employee employee = employeeRepository.findByEmployeeCode(identifier)
            .or(() -> employeeRepository.findByEmail(identifier))
            .orElseThrow(() -> {
                log.warn("[UserDetailsService] No employee found for identifier: '{}'", identifier);
                return new UsernameNotFoundException("Employee not found: " + identifier);
            });

        return User.builder()
            .username(employee.getEmployeeCode()) // JWT subject = code, not email
            .password(employee.getPasswordHash())
            .authorities(List.of(
                new SimpleGrantedAuthority("ROLE_" + employee.getRole().name())
            ))
            .accountExpired(false)
            .accountLocked(employee.getStatus() == com.coresync.hrms.backend.enums.EmployeeStatus.TERMINATED)
            .credentialsExpired(false)
            .disabled(employee.getStatus() == com.coresync.hrms.backend.enums.EmployeeStatus.RESIGNED)
            .build();
    }
}