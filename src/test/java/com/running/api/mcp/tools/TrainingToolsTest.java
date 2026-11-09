package com.running.api.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.running.api.dto.PlanViolationDto;
import com.running.api.dto.TrainingPlanResponseDto;
import com.running.api.dto.WeeklyPlanPayload;
import com.running.api.exception.ForbiddenOperationException;
import com.running.api.exception.PlanValidationException;
import com.running.api.mcp.McpToolResult;
import com.running.api.model.PlanStatus;
import com.running.api.security.CurrentUserService;
import com.running.api.security.JwtService;
import com.running.api.security.UserPrincipal;
import com.running.api.service.TrainingContextService;
import com.running.api.service.TrainingPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingToolsTest {

    private static final Long USER_ID = 5L;
    private static final String REQUEST_ID = "job-123";
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    @Mock
    private TrainingContextService contextService;
    @Mock
    private TrainingPlanService trainingPlanService;
    @Mock
    private CurrentUserService currentUserService;

    private TrainingTools tools;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        tools = new TrainingTools(contextService, trainingPlanService, currentUserService, objectMapper);
    }

    @Test
    void saveWeeklyPlanUsesRequestIdFromTokenAndReturnsSavedPlan() throws Exception {
        UserPrincipal principal = new UserPrincipal(USER_ID, "runner@example.com", "USER",
                Set.of(JwtService.SCOPE_TRAINING_READ, JwtService.SCOPE_TRAINING_WRITE),
                "mcp", REQUEST_ID);
        when(currentUserService.requirePrincipal()).thenReturn(principal);
        when(trainingPlanService.parseWeekStart("2026-11-16")).thenReturn(MONDAY);
        WeeklyPlanPayload payload = new WeeklyPlanPayload(
                "Resumen", null, 8000.0, null, List.of(), null, null);
        when(trainingPlanService.parsePlanJson("{}")).thenReturn(payload);
        TrainingPlanResponseDto saved = new TrainingPlanResponseDto(
                "plan-1", USER_ID, MONDAY, MONDAY.plusDays(6), PlanStatus.ACTIVE, 1,
                REQUEST_ID, "Resumen", null, 8000.0, null, null, null, null, List.of());
        when(trainingPlanService.saveWeeklyPlan(
                eq(USER_ID), eq(REQUEST_ID), eq(MONDAY), anyBoolean(), any(WeeklyPlanPayload.class)))
                .thenReturn(saved);

        ObjectNode args = objectMapper.createObjectNode()
                .put("weekStart", "2026-11-16")
                .put("planJson", "{}");

        McpToolResult result = tools.saveWeeklyPlan(args);

        assertThat(result.isError()).isFalse();
        assertThat(result.text()).contains("\"ok\":true", "\"planId\":\"plan-1\"");
        verify(trainingPlanService).saveWeeklyPlan(
                eq(USER_ID), eq(REQUEST_ID), eq(MONDAY), eq(false), eq(payload));
    }

    @Test
    void saveWeeklyPlanReturnsValidationViolationsAsToolError() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "runner@example.com", "USER",
                Set.of(JwtService.SCOPE_TRAINING_WRITE), "mcp", REQUEST_ID);
        when(currentUserService.requirePrincipal()).thenReturn(principal);
        when(trainingPlanService.parseWeekStart("2026-11-16")).thenReturn(MONDAY);
        when(trainingPlanService.parsePlanJson("bad")).thenThrow(new PlanValidationException(
                List.of(new PlanViolationDto("NO_WORKOUTS", "El plan debe incluir sesiones"))));

        ObjectNode args = objectMapper.createObjectNode()
                .put("weekStart", "2026-11-16")
                .put("planJson", "bad");

        McpToolResult result = tools.saveWeeklyPlan(args);

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains("PLAN_INVALID", "NO_WORKOUTS");
    }

    @Test
    void saveWeeklyPlanRejectsTokenWithoutWriteScope() {
        doThrow(new ForbiddenOperationException("scope requerido"))
                .when(currentUserService).requireScope(JwtService.SCOPE_TRAINING_WRITE);
        ObjectNode args = objectMapper.createObjectNode()
                .put("weekStart", "2026-11-16")
                .put("planJson", "{}");

        McpToolResult result = tools.saveWeeklyPlan(args);

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains("FORBIDDEN");
    }

    @Test
    void saveWeeklyPlanRejectsRequestIdDifferentFromToken() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "runner@example.com", "USER",
                Set.of(JwtService.SCOPE_TRAINING_WRITE), "mcp", REQUEST_ID);
        when(currentUserService.requirePrincipal()).thenReturn(principal);

        ObjectNode args = objectMapper.createObjectNode()
                .put("weekStart", "2026-11-16")
                .put("planJson", "{}")
                .put("requestId", "otro-job");

        McpToolResult result = tools.saveWeeklyPlan(args);

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains("JOB_CONFLICT");
    }
}
