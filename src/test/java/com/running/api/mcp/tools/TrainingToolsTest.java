package com.running.api.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.running.api.dto.DailyGenerationJobResponseDto;
import com.running.api.dto.DailyRecommendationPayload;
import com.running.api.dto.DailyRecommendationPayload.WorkoutPayload;
import com.running.api.dto.DailyRecommendationResponseDto;
import com.running.api.dto.TrainingContextDto;
import com.running.api.dto.ValidationViolationDto;
import com.running.api.exception.RecommendationValidationException;
import com.running.api.mcp.McpToolResult;
import com.running.api.model.TrainingJobStatus;
import com.running.api.model.WorkoutType;
import com.running.api.security.CurrentUserService;
import com.running.api.security.JwtService;
import com.running.api.security.UserPrincipal;
import com.running.api.service.DailyRecommendationService;
import com.running.api.service.TrainingContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingToolsTest {

    private static final Long USER_ID = 7L;
    private static final String REQUEST_ID = "request-123";
    private static final LocalDate DATE = LocalDate.of(2026, 11, 11);

    @Mock
    private TrainingContextService contextService;

    @Mock
    private DailyRecommendationService recommendationService;

    @Mock
    private CurrentUserService currentUserService;

    private TrainingTools tools;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        tools = new TrainingTools(contextService, recommendationService, currentUserService, objectMapper);
    }

    @Test
    void getTrainingContextReturnsJson() throws Exception {
        when(currentUserService.requireUserId()).thenReturn(USER_ID);
        when(contextService.getContext(USER_ID, null)).thenReturn(context());

        McpToolResult result = tools.getTrainingContext(objectMapper.createObjectNode());

        assertThat(result.isError()).isFalse();
        assertThat(result.text()).contains("Europe/Madrid");
    }

    @Test
    void saveDailyRecommendationUsesTheTokenRequestId() throws Exception {
        when(currentUserService.requirePrincipal()).thenReturn(principal(REQUEST_ID));
        when(recommendationService.parseDate("2026-11-11")).thenReturn(DATE);
        when(recommendationService.parseRecommendationJson(any())).thenReturn(payload());
        when(recommendationService.saveDailyRecommendation(
                eq(USER_ID), eq(REQUEST_ID), eq(DATE), eq(false), any(DailyRecommendationPayload.class)))
                .thenReturn(response());

        ObjectNode args = objectMapper.createObjectNode();
        args.put("date", "2026-11-11");
        args.put("recommendationJson", "{}");

        McpToolResult result = tools.saveDailyRecommendation(args);

        assertThat(result.isError()).isFalse();
        assertThat(result.text()).contains("\"ok\":true");
        assertThat(result.text()).contains("rec-1");
    }

    @Test
    void saveDailyRecommendationReturnsValidationViolations() throws Exception {
        when(currentUserService.requirePrincipal()).thenReturn(principal(REQUEST_ID));
        when(recommendationService.parseDate("2026-11-11")).thenReturn(DATE);
        when(recommendationService.parseRecommendationJson(any())).thenThrow(
                new RecommendationValidationException(List.of(
                        new ValidationViolationDto("WORKOUT_REQUIRED", "Falta workout"))));

        ObjectNode args = objectMapper.createObjectNode();
        args.put("date", "2026-11-11");
        args.put("recommendationJson", "{}");

        McpToolResult result = tools.saveDailyRecommendation(args);

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains("RECOMMENDATION_INVALID");
        assertThat(result.text()).contains("WORKOUT_REQUIRED");
    }

    @Test
    void saveDailyRecommendationRejectsRequestIdMismatch() {
        when(currentUserService.requirePrincipal()).thenReturn(principal(REQUEST_ID));

        ObjectNode args = objectMapper.createObjectNode();
        args.put("requestId", "otro-request");

        McpToolResult result = tools.saveDailyRecommendation(args);

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains("JOB_CONFLICT");
    }

    @Test
    void failGenerationJobReturnsTheUpdatedStatus() throws Exception {
        when(currentUserService.requirePrincipal()).thenReturn(principal(REQUEST_ID));
        when(recommendationService.failJob(USER_ID, REQUEST_ID, "fallo"))
                .thenReturn(new DailyGenerationJobResponseDto(
                        REQUEST_ID, USER_ID, DATE, TrainingJobStatus.FAILED, "MANUAL",
                        1, null, "fallo", LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()));

        ObjectNode args = objectMapper.createObjectNode();
        args.put("error", "fallo");

        McpToolResult result = tools.failGenerationJob(args);

        assertThat(result.isError()).isFalse();
        assertThat(result.text()).contains("FAILED");
    }

    private UserPrincipal principal(String requestId) {
        return new UserPrincipal(USER_ID, "runner@example.com", "USER",
                Set.of(JwtService.SCOPE_TRAINING_READ, JwtService.SCOPE_TRAINING_WRITE),
                "mcp", requestId);
    }

    private TrainingContextDto context() {
        return new TrainingContextDto(
                DATE, DATE, "Europe/Madrid", null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), null, List.of());
    }

    private DailyRecommendationPayload payload() {
        return new DailyRecommendationPayload(
                "Rodaje suave", "ACWR estable", "Notas",
                new WorkoutPayload(WorkoutType.EASY, "Rodaje suave", "45 min",
                        8000.0, 2700.0, 5.6, 2, 50.0, "45 min"),
                "daily-recommendation-v1");
    }

    private DailyRecommendationResponseDto response() {
        return new DailyRecommendationResponseDto(
                "rec-1", USER_ID, DATE, REQUEST_ID,
                "Rodaje suave", "ACWR estable", "Notas",
                new DailyRecommendationResponseDto.WorkoutDto(
                        WorkoutType.EASY, "Rodaje suave", "45 min",
                        8000.0, 2700.0, 5.6, 2, 50.0, "45 min"),
                "daily-recommendation-v1", LocalDateTime.now());
    }
}
