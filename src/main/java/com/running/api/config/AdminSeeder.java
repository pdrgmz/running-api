package com.running.api.config;

import com.running.api.model.Activity;
import com.running.api.model.User;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.UserRepository;
import com.running.api.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Crea (si procede) el usuario administrador configurado y le asigna las
 * actividades históricas que aún no tienen dueño (migración a multi-usuario).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final PasswordEncoder passwordEncoder;
    private final StatsService statsService;

    @Value("${app.auth.admin-email:}")
    private String adminEmail;

    @Value("${app.auth.admin-password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.info("Sembrado de admin deshabilitado (app.auth.admin-email/password vacíos)");
            return;
        }

        String email = adminEmail.trim().toLowerCase(Locale.ROOT);
        User admin = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            User created = userRepository.save(User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(adminPassword))
                    .role("ADMIN")
                    .build());
            log.info("Usuario administrador creado: {}", email);
            return created;
        });

        List<Activity> orphans = activityRepository.findByUserIdIsNull();
        if (!orphans.isEmpty()) {
            orphans.forEach(activity -> activity.setUserId(admin.getId()));
            activityRepository.saveAll(orphans);
            log.info("Asignadas {} actividades sin dueño al administrador {}", orphans.size(), email);
            statsService.recalculateStatsAndRecords(admin.getId());
        }
    }
}
