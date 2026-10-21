package com.running.api.service;

import com.running.api.dto.ActivityDeletionStatusDto;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Estado en memoria de los vaciados de actividades lanzados en segundo plano.
 * <p>
 * Cada vaciado se identifica con un {@code deleteId} con el que se consulta su progreso.
 * Solo se conserva el último vaciado de cada usuario: al lanzar uno nuevo se descarta el
 * anterior para no acumular estados.
 */
@Service
public class ActivityDeletionStatusTracker {

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_PROCESSING = "PROCESSING";
    static final String STATUS_COMPLETED = "COMPLETED";
    static final String STATUS_FAILED = "FAILED";

    private final ConcurrentHashMap<String, ActivityDeletionStatusDto> deletions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> latestByUser = new ConcurrentHashMap<>();

    /**
     * Registra un vaciado nuevo y devuelve su estado inicial.
     */
    public ActivityDeletionStatusDto create(Long userId) {
        String deleteId = UUID.randomUUID().toString();
        ActivityDeletionStatusDto status = ActivityDeletionStatusDto.builder()
                .deleteId(deleteId)
                .status(STATUS_PENDING)
                .totalActivities(0L)
                .deletedActivities(0L)
                .progressPercent(0.0)
                .message("Vaciado en cola")
                .build();

        deletions.put(deleteId, status);

        String previous = latestByUser.put(userId, deleteId);
        if (previous != null) {
            deletions.remove(previous);
        }
        return snapshot(status);
    }

    /**
     * Devuelve una copia del estado del vaciado, o {@code null} si no existe o fue
     * reemplazado por uno más reciente del mismo usuario.
     */
    public ActivityDeletionStatusDto getStatus(String deleteId) {
        ActivityDeletionStatusDto status = deleteId == null ? null : deletions.get(deleteId);
        return status == null ? null : snapshot(status);
    }

    public void markProcessing(String deleteId, long totalActivities) {
        update(deleteId, status -> {
            status.setStatus(STATUS_PROCESSING);
            status.setTotalActivities(totalActivities);
            status.setMessage("Vaciado en proceso");
            recalculatePercent(status);
        });
    }

    public void updateProgress(String deleteId, long deletedActivities) {
        update(deleteId, status -> {
            status.setDeletedActivities(deletedActivities);
            recalculatePercent(status);
        });
    }

    public void markCompleted(String deleteId, long deletedActivities) {
        update(deleteId, status -> {
            status.setStatus(STATUS_COMPLETED);
            status.setDeletedActivities(deletedActivities);
            status.setMessage("Actividades y estadísticas del usuario vaciadas con éxito");
            recalculatePercent(status);
        });
    }

    public void markFailed(String deleteId, String error) {
        update(deleteId, status -> {
            status.setStatus(STATUS_FAILED);
            status.setMessage("Vaciado fallido");
            status.setError(error);
        });
    }

    private void update(String deleteId, Consumer<ActivityDeletionStatusDto> mutation) {
        ActivityDeletionStatusDto status = deleteId == null ? null : deletions.get(deleteId);
        if (status == null) {
            return;
        }
        // Las mutaciones se serializan sobre el propio DTO: el borrado corre en otro hilo
        // mientras la API consulta el estado.
        synchronized (status) {
            mutation.accept(status);
        }
    }

    private static void recalculatePercent(ActivityDeletionStatusDto status) {
        if (status.getTotalActivities() <= 0) {
            status.setProgressPercent(0.0);
            return;
        }
        double percent = status.getDeletedActivities() * 100.0 / status.getTotalActivities();
        status.setProgressPercent(Math.round(percent * 100.0) / 100.0);
    }

    private static ActivityDeletionStatusDto snapshot(ActivityDeletionStatusDto status) {
        synchronized (status) {
            return ActivityDeletionStatusDto.builder()
                    .deleteId(status.getDeleteId())
                    .status(status.getStatus())
                    .totalActivities(status.getTotalActivities())
                    .deletedActivities(status.getDeletedActivities())
                    .progressPercent(status.getProgressPercent())
                    .message(status.getMessage())
                    .error(status.getError())
                    .build();
        }
    }
}
