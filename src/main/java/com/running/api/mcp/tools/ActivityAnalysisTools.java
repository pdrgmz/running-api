package com.running.api.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.running.api.exception.AnalysisJobStateException;
import com.running.api.exception.ForbiddenOperationException;
import com.running.api.exception.ResourceNotFoundException;
import com.running.api.mcp.McpToolResult;
import com.running.api.model.ActivityAnalysisJob;
import com.running.api.security.CurrentUserService;
import com.running.api.security.JwtService;
import com.running.api.security.UserPrincipal;
import com.running.api.service.ActivityAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Herramientas MCP que cierran el ciclo del análisis de actividad: guardar el
 * análisis generado por n8n y reportar el fallo del job.
 * <p>
 * El usuario se resuelve siempre del token autenticado; nunca se acepta un
 * {@code userId} como parámetro.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityAnalysisTools {

    private final ActivityAnalysisService analysisService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public McpToolResult saveActivityAnalysis(JsonNode args) {
        try {
            currentUserService.requireScope(JwtService.SCOPE_ACTIVITY_WRITE);
            UserPrincipal principal = currentUserService.requirePrincipal();

            String requestId = resolveRequestId(args, principal);
            String activityId = text(args, "activityId");
            String analysisJson = text(args, "analysisJson");

            if (activityId == null || activityId.isBlank()) {
                return errorResult("ACTIVITY_ID_REQUIRED",
                        "El parámetro activityId es obligatorio", null);
            }
            if (analysisJson == null || analysisJson.isBlank()) {
                return errorResult("ANALYSIS_JSON_REQUIRED",
                        "El parámetro analysisJson es obligatorio", null);
            }

            ActivityAnalysisJob job = analysisService.saveAnalysis(
                    principal, requestId, activityId, analysisJson);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("ok", true);
            result.put("requestId", job.getId());
            result.put("activityId", job.getActivityId());
            result.put("status", job.getStatus().name());
            return McpToolResult.success(objectMapper.writeValueAsString(result));
        } catch (AnalysisJobStateException ex) {
            return errorResult("JOB_CONFLICT", ex.getMessage(), null);
        } catch (ResourceNotFoundException ex) {
            return errorResult("JOB_NOT_FOUND", ex.getMessage(),
                    "El requestId debe ser el job creado por la API; no inventes uno.");
        } catch (ForbiddenOperationException ex) {
            return errorResult("FORBIDDEN", ex.getMessage(), null);
        } catch (Exception ex) {
            log.error("[MCP] Error guardando el análisis de la actividad: {}", ex.getMessage(), ex);
            return errorResult("SAVE_ANALYSIS_ERROR", ex.getMessage(), null);
        }
    }

    public McpToolResult failActivityAnalysisJob(JsonNode args) {
        try {
            currentUserService.requireScope(JwtService.SCOPE_ACTIVITY_WRITE);
            UserPrincipal principal = currentUserService.requirePrincipal();
            String requestId = resolveRequestId(args, principal);
            String error = text(args, "error");
            if (error == null || error.isBlank()) {
                error = "n8n reportó un fallo sin detalle";
            }
            ActivityAnalysisJob job = analysisService.failJob(principal, requestId, error);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("ok", true);
            result.put("requestId", job.getId());
            result.put("status", job.getStatus().name());
            return McpToolResult.success(objectMapper.writeValueAsString(result));
        } catch (ForbiddenOperationException ex) {
            return errorResult("FORBIDDEN", ex.getMessage(), null);
        } catch (AnalysisJobStateException ex) {
            return errorResult("JOB_CONFLICT", ex.getMessage(), null);
        } catch (ResourceNotFoundException ex) {
            return errorResult("JOB_NOT_FOUND", ex.getMessage(), null);
        } catch (Exception ex) {
            log.error("[MCP] Error marcando el job de análisis como fallido: {}", ex.getMessage(), ex);
            return errorResult("FAIL_JOB_ERROR", ex.getMessage(), null);
        }
    }

    private String resolveRequestId(JsonNode args, UserPrincipal principal) {
        String fromArgs = text(args, "requestId");
        String fromToken = principal.requestId();
        if (fromToken != null && !fromToken.isBlank()) {
            if (fromArgs != null && !fromArgs.isBlank() && !fromToken.equals(fromArgs)) {
                throw new AnalysisJobStateException(
                        "requestId del argumento no coincide con el requestId del token");
            }
            return fromToken;
        }
        return fromArgs;
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
