package com.running.api.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomHealthIndicator implements HealthIndicator {

    private final String storagePath = "./storage";

    @Override
    public Health health() {
        try {
            // Verificar que el directorio de almacenamiento existe y es escribible
            File storageDir = new File(storagePath);
            if (!storageDir.exists()) {
                boolean created = storageDir.mkdirs();
                if (!created) {
                    log.error("[HEALTH] No se pudo crear el directorio de almacenamiento: {}", storagePath);
                    return Health.down()
                            .withDetail("storage", "Directorio no existe y no se pudo crear")
                            .withDetail("path", storagePath)
                            .build();
                }
            }

            if (!storageDir.canWrite()) {
                log.error("[HEALTH] El directorio de almacenamiento no es escribible: {}", storagePath);
                return Health.down()
                        .withDetail("storage", "Directorio no es escribible")
                        .withDetail("path", storagePath)
                        .build();
            }

            // Verificar espacio en disco
            long freeSpace = storageDir.getFreeSpace();
            long totalSpace = storageDir.getTotalSpace();
            double freeSpaceGb = freeSpace / (1024.0 * 1024.0 * 1024.0);

            if (freeSpaceGb < 1.0) {
                log.warn("[HEALTH] Espacio en disco bajo: {} GB disponibles", String.format("%.2f", freeSpaceGb));
                return Health.down()
                        .withDetail("storage", "Espacio en disco bajo")
                        .withDetail("freeSpaceGb", String.format("%.2f", freeSpaceGb))
                        .build();
            }

            log.debug("[HEALTH] Almacenamiento OK: {} GB disponibles", String.format("%.2f", freeSpaceGb));
            return Health.up()
                    .withDetail("storage", "OK")
                    .withDetail("freeSpaceGb", String.format("%.2f", freeSpaceGb))
                    .withDetail("totalSpaceGb", String.format("%.2f", totalSpace / (1024.0 * 1024.0 * 1024.0)))
                    .build();

        } catch (Exception e) {
            log.error("[HEALTH] Error verificando almacenamiento: {}", e.getMessage(), e);
            return Health.down()
                    .withDetail("storage", "Error: " + e.getMessage())
                    .build();
        }
    }
}
