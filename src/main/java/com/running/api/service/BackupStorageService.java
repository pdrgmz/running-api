package com.running.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

@Service
public class BackupStorageService {

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
            String sanitizedId = activityId.replaceAll("[:.]", "-");
            String filename = "backup_" + sanitizedId + ".tcx";
            Path destinationFile = this.rootLocation.resolve(Paths.get(filename)).normalize().toAbsolutePath();

            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            return destinationFile.toString();
        } catch (IOException e) {
            throw new RuntimeException("Error guardando el archivo físico de respaldo", e);
        }
    }

    public Path getRootLocation() {
        return rootLocation;
    }
}

