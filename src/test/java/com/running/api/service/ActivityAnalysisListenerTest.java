package com.running.api.service;

import com.running.api.event.ActivityCreatedEvent;
import com.running.api.exception.AnalysisException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityAnalysisListenerTest {

    private static final Long USER_ID = 7L;
    private static final String ACTIVITY_ID = "activity-1";
    private static final ActivityCreatedEvent EVENT =
            new ActivityCreatedEvent(USER_ID, ACTIVITY_ID);

    @Mock
    private ActivityAnalysisGenerationService generationService;

    @Mock
    private N8nActivityAnalysisWebhookClient webhookClient;

    @Test
    void triggersAnalysisWhenEnabledAndConfigured() {
        when(webhookClient.isConfigured()).thenReturn(true);
        ActivityAnalysisListener listener =
                new ActivityAnalysisListener(generationService, webhookClient, true);

        listener.onActivityCreated(EVENT);

        verify(generationService).analyzeNow(USER_ID, ACTIVITY_ID, false);
    }

    @Test
    void skipsAnalysisWhenDisabled() {
        ActivityAnalysisListener listener =
                new ActivityAnalysisListener(generationService, webhookClient, false);

        listener.onActivityCreated(EVENT);

        verify(generationService, never()).analyzeNow(USER_ID, ACTIVITY_ID, false);
    }

    @Test
    void skipsAnalysisWhenWebhookNotConfigured() {
        when(webhookClient.isConfigured()).thenReturn(false);
        ActivityAnalysisListener listener =
                new ActivityAnalysisListener(generationService, webhookClient, true);

        listener.onActivityCreated(EVENT);

        verify(generationService, never()).analyzeNow(USER_ID, ACTIVITY_ID, false);
    }

    @Test
    void doesNotPropagateAnalysisFailures() {
        when(webhookClient.isConfigured()).thenReturn(true);
        when(generationService.analyzeNow(USER_ID, ACTIVITY_ID, false))
                .thenThrow(new AnalysisException("n8n caído"));
        ActivityAnalysisListener listener =
                new ActivityAnalysisListener(generationService, webhookClient, true);

        assertThatCode(() -> listener.onActivityCreated(EVENT)).doesNotThrowAnyException();
    }
}
