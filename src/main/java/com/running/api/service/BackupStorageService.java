package com.running.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

@Slf4j
@Service
public class BackupStorageService {

    private static final Pattern UNSAFE_CHARS = Pattern.compile("[:.]");

    private final Path rootLocation;

    public BackupStorageService(@Value("${app.storage.location}") String storageLocation) {
        this.rootLocation = Paths.get(storageLocation);
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Error al crear el directorio de respaldo físico", e);
        }
    }

    public String store(MultipartFile file, String activityId) {
        try (InputStream inputStream = file.getInputStream()) {
            return store(inputStream, activityId);
        } catch (IOException e) {
            throw new RuntimeException("Error guardando el archivo físico de respaldo", e);
        }
    }

    public String store(InputStream inputStream, String activityId) {
        try {
            String sanitizedId = UNSAFE_CHARS.matcher(activityId).replaceAll("-");
            String filename = "backup_" + sanitizedId + ".tcx";
            Path destinationFile = this.rootLocation.resolve(filename).normalize().toAbsolutePath();

            if (!destinationFile.startsWith(this.rootLocation.normalize().toAbsolutePath())) {
                throw new SecurityException("Ruta de archivo inválida: " + filename);
            }

            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            return destinationFile.toString();
        } catch (IOException e) {
            throw new RuntimeException("Error guardando el archivo físico de respaldo", e);
        }
    }

    public Path getRootLocation() {
        return rootLocation;
    }

    public void clearStorage() {
        try {
            if (Files.exists(rootLocation)) {
                try (var stream = Files.walk(rootLocation)) {
                    stream.filter(Files::isRegularFile).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("No se pudo eliminar el archivo: {}", path, e);
                        }
                    });
                }
            }
        } catch (IOException e) {
            log.error("Error al limpiar el almacenamiento", e);
        }
    }
}
