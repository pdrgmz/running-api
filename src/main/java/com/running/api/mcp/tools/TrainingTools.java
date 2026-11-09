package com.running.api.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.running.api.dto.PlanGenerationJobResponseDto;
import com.running.api.dto.TrainingContextDto;
import com.running.api.dto.TrainingPlanResponseDto;
import com.running.api.dto.WeeklyPlanPayload;
import com.running.api.exception.ForbiddenOperationException;
import com.running.api.exception.PlanAlreadyExistsException;
import com.running.api.exception.PlanJobStateException;
import com.running.api.exception.PlanValidationException;
import com.running.api.exception.ResourceNotFoundException;
import com.running.api.mcp.McpToolResult;
import com.running.api.security.CurrentUserService;
import com.running.api.security.JwtService;
import com.running.api.security.UserPrincipal;
import com.running.api.service.TrainingContextService;
import com.running.api.service.TrainingPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Herramientas MCP que cierran el ciclo de generación: contexto de
 * entrenamiento, guardado del plan y reporte de fallo del job.
 * <p>
 * El usuario se resuelve siempre del token autenticado; nunca se acepta un
 * {@code userId} como parámetro.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrainingTools {

    private final TrainingContextService contextService;
    private final TrainingPlanService trainingPlanService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public McpToolResult getTrainingContext(JsonNode args) {
        try {
            currentUserService.requireScope(JwtService.SCOPE_TRAINING_READ);
            Long userId = currentUserService.requireUserId();
            String rawWeekStart = text(args, "weekStart");
            LocalDate weekStart = trainingPlanService.parseWeekStart(rawWeekStart);
            TrainingContextDto context = contextService.getContext(userId, weekStart);
            return McpToolResult.success(objectMapper.writeValueAsString(context));
        } catch (ForbiddenOperationException ex) {
            return errorResult("FORBIDDEN", ex.getMessage(), null);
        } catch (PlanValidationException ex) {
            return validationError(ex);
        } catch (Exception ex) {
            return errorResult("TRAINING_CONTEXT_ERROR", ex.getMessage(), null);
        }
    }

    public McpToolResult saveWeeklyPlan(JsonNode args) {
        try {
            currentUserService.requireScope(JwtService.SCOPE_TRAINING_WRITE);
            UserPrincipal principal = currentUserService.requirePrincipal();
            Long userId = principal.id();

            String requestId = resolveRequestId(args, principal);
            LocalDate weekStart = trainingPlanService.parseWeekStart(text(args, "weekStart"));
            boolean replace = args.path("replace").asBoolean(false);
            WeeklyPlanPayload payload = trainingPlanService.parsePlanJson(text(args, "planJson"));

            TrainingPlanResponseDto plan = trainingPlanService.saveWeeklyPlan(
                    userId, requestId, weekStart, replace, payload);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("ok", true);
            result.put("planId", plan.planId());
            result.put("weekStart", plan.weekStart().toString());
            result.put("version", plan.version());
            result.put("status", plan.status().name());
            result.put("requestId", plan.requestId());
            result.set("plan", objectMapper.valueToTree(plan));
            return McpToolResult.success(objectMapper.writeValueAsString(result));
        } catch (PlanValidationException ex) {
            return validationError(ex);
        } catch (PlanAlreadyExistsException ex) {
            return errorResult("PLAN_EXISTS", ex.getMessage(),
                    "Usa replace=true si quieres archivar el plan actual y crear una versión nueva.");
        } catch (PlanJobStateException ex) {
            return errorResult("JOB_CONFLICT", ex.getMessage(), null);
        } catch (ResourceNotFoundException ex) {
            return errorResult("JOB_NOT_FOUND", ex.getMessage(),
                    "El requestId debe ser el job creado por la API; no inventes uno.");
        } catch (ForbiddenOperationException ex) {
            return errorResult("FORBIDDEN", ex.getMessage(), null);
        } catch (Exception ex) {
            log.error("[MCP] Error guardando plan semanal: {}", ex.getMessage(), ex);
            return errorResult("SAVE_PLAN_ERROR", ex.getMessage(), null);
        }
    }

    public McpToolResult failGenerationJob(JsonNode args) {
        try {
            currentUserService.requireScope(JwtService.SCOPE_TRAINING_WRITE);
            UserPrincipal principal = currentUserService.requirePrincipal();
            String requestId = resolveRequestId(args, principal);
            String error = text(args, "error");
            if (error == null || error.isBlank()) {
                error = "n8n reportó un fallo sin detalle";
            }
            PlanGenerationJobResponseDto job = trainingPlanService.failJob(
                    principal.id(), requestId, error);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("ok", true);
            result.put("requestId", job.requestId());
            result.put("status", job.status().name());
            return McpToolResult.success(objectMapper.writeValueAsString(result));
        } catch (ForbiddenOperationException ex) {
            return errorResult("FORBIDDEN", ex.getMessage(), null);
        } catch (PlanJobStateException ex) {
            return errorResult("JOB_CONFLICT", ex.getMessage(), null);
        } catch (ResourceNotFoundException ex) {
            return errorResult("JOB_NOT_FOUND", ex.getMessage(), null);
        } catch (Exception ex) {
            log.error("[MCP] Error marcando job como fallido: {}", ex.getMessage(), ex);
            return errorResult("FAIL_JOB_ERROR", ex.getMessage(), null);
        }
    }

    private String resolveRequestId(JsonNode args, UserPrincipal principal) {
        String fromArgs = text(args, "requestId");
        String fromToken = principal.requestId();
        if (fromToken != null && !fromToken.isBlank()) {
            if (fromArgs != null && !fromArgs.isBlank() && !fromToken.equals(fromArgs)) {
                throw new PlanJobStateException(
                        "requestId del argumento no coincide con el requestId del token");
            }
            return fromToken;
        }
        return fromArgs;
    }

    private McpToolResult validationError(PlanValidationException ex) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("ok", false);
        result.put("code", "PLAN_INVALID");
        result.put("message", ex.getMessage());
        result.set("violations", objectMapper.valueToTree(ex.getViolations()));
        result.put("hint", "Corrige las violaciones y vuelve a llamar a save_weekly_plan con el mismo requestId.");
        return McpToolResult.error(toJson(result));
    }

    private McpToolResult errorResult(String code, String message, String hint) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("ok", false);
        result.put("code", code);
        result.put("message", message == null ? "Error desconocido" : message);
        if (hint != null) {
            result.put("hint", hint);
        }
        return McpToolResult.error(toJson(result));
    }

    private String toJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return "{\"ok\":false,\"code\":\"SERIALIZATION_ERROR\"}";
        }
    }

    private static String text(JsonNode args, String field) {
        if (args == null) {
            return null;
        }
        JsonNode value = args.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.isTextual() ? value.asText() : value.toString();
    }
}
