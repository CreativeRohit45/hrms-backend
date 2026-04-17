// src/main/java/com/coresync/hrms/backend/config/SecurityConfig.java
package com.coresync.hrms.backend.config;

import java.util.List;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.coresync.hrms.backend.security.CustomUserDetailsService;
import com.coresync.hrms.backend.security.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults()) // MUST HAVE THIS TO USE THE BEAN BELOW
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 1. Public Endpoints
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/v1/auth/login")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/v1/system/info")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/error")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health")).permitAll()

                // 2. Personal Self-Service (High Priority)
                .requestMatchers(
                    AntPathRequestMatcher.antMatcher("/api/v1/dashboard/**"),
                    AntPathRequestMatcher.antMatcher("/api/v1/employees/me"),
                    AntPathRequestMatcher.antMatcher("/api/v1/attendance/my-logs"),
                    AntPathRequestMatcher.antMatcher("/api/v1/payroll/my"),
                    AntPathRequestMatcher.antMatcher("/api/v1/payroll/payslip/**"),
                    AntPathRequestMatcher.antMatcher("/api/v1/gatepasses/my"),
                    AntPathRequestMatcher.antMatcher("/api/v1/leaves/my-requests"), // Keeping for backward compatibility
                    AntPathRequestMatcher.antMatcher("/api/v1/attendance/punch-in"),
                    AntPathRequestMatcher.antMatcher("/api/v1/attendance/punch-out"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/v1/attendance/corrections/**"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/holidays"),
                    AntPathRequestMatcher.antMatcher("/api/v1/leaves"),
                    AntPathRequestMatcher.antMatcher("/api/v1/gatepasses/**") // Match all gatepass sub-endpoints for the user
                ).authenticated()

                // 3. Managerial Approvals & Retrieval
                .requestMatchers(
                    AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/leaves/pending"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/gatepasses/pending"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/attendance/pending-corrections"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/attendance/roster"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.PUT, "/api/v1/gatepasses/*/approve"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.PUT, "/api/v1/gatepasses/*/reject"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.PUT, "/api/v1/leaves/*/approve"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.PUT, "/api/v1/leaves/*/reject"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/v1/gatepasses/*/approve"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/v1/gatepasses/*/reject"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.PUT, "/api/v1/attendance/corrections/*/approve"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.PUT, "/api/v1/attendance/corrections/*/reject"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.PUT, "/api/v1/attendance/*/approve-overtime")
                ).hasAnyRole("DEPARTMENT_MANAGER", "HR_ADMIN", "SUPER_ADMIN")
                
                // 4. Administrative Endpoints
                .requestMatchers(
                    AntPathRequestMatcher.antMatcher("/api/v1/employees/**"),
                    AntPathRequestMatcher.antMatcher("/api/v1/attendance/corrections/**"),
                    // Using more specific matchers to exclude '/my' from the admin block
                    AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/v1/payroll/run"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/v1/payroll/run-bulk"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/v1/payroll/lock"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/payroll/company-payroll"),
                    AntPathRequestMatcher.antMatcher("/api/v1/reports/**")
                ).hasAnyRole("HR_ADMIN", "SUPER_ADMIN")
                
                // 5. Settings Visibility (HR can see them, but only Super Admin manages them)
                .requestMatchers(
                    AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/company-locations/**"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/shifts/**"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/departments/**")
                ).hasAnyRole("HR_ADMIN", "SUPER_ADMIN")
                
                .requestMatchers(
                    AntPathRequestMatcher.antMatcher("/api/v1/company-locations/**"),
                    AntPathRequestMatcher.antMatcher("/api/v1/shifts/**"),
                    AntPathRequestMatcher.antMatcher("/api/v1/departments/**")
                ).hasRole("SUPER_ADMIN")
                
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Session expired or invalid token\"}");
                })
            );

        return http.build();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
