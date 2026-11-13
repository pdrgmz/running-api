package com.running.api.service;

import com.running.api.event.ActivityCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Lanza el análisis de una actividad recién creada.
 * <p>
 * El listener se ejecuta <strong>después del commit</strong> para que n8n pueda
 * leer la actividad desde el MCP, y de forma asíncrona para no bloquear la
 * importación. Si el webhook de n8n no está configurado o falla, la importación
 * no se ve afectada: solo se registra un aviso.
 */
@Slf4j
@Component
public class ActivityAnalysisListener {

    private final ActivityAnalysisGenerationService generationService;
    private final N8nActivityAnalysisWebhookClient webhookClient;
    private final boolean autoAnalyzeOnImport;

    public ActivityAnalysisListener(
            ActivityAnalysisGenerationService generationService,
            N8nActivityAnalysisWebhookClient webhookClient,
            @Value("${app.training.activity-analysis.auto-analyze-on-import:true}") boolean autoAnalyzeOnImport) {
        this.generationService = generationService;
        this.webhookClient = webhookClient;
        this.autoAnalyzeOnImport = autoAnalyzeOnImport;
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onActivityCreated(ActivityCreatedEvent event) {
        if (!autoAnalyzeOnImport) {
            log.debug("Análisis automático desactivado (auto-analyze-on-import=false)");
            return;
        }
        if (!webhookClient.isConfigured()) {
            log.debug("Análisis automático omitido para {}: webhook de n8n no configurado",
                    event.activityId());
            return;
        }
        try {
            generationService.analyzeNow(event.userId(), event.activityId(), false);
            log.info("Análisis automático encolado para la actividad {} (usuario {})",
                    event.activityId(), event.userId());
        } catch (Exception ex) {
            // Un fallo del análisis nunca debe tumbar la importación.
            log.warn("No se pudo encolar el análisis automático de la actividad {}: {}",
                    event.activityId(), ex.getMessage());
        }
    }
}
