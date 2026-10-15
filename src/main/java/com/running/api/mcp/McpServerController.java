package com.running.api.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.running.api.mcp.tools.ActivityTools;
import com.running.api.mcp.tools.AnalyticsTools;
import com.running.api.mcp.tools.StatsTools;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpServerController {

    private final ObjectMapper objectMapper;
    private final ActivityTools activityTools;
    private final StatsTools statsTools;
    private final AnalyticsTools analyticsTools;

    private final Map<String, McpTool> tools = new LinkedHashMap<>();
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutos

    @PostConstruct
    public void init() {
        registerTool("get_activity_by_id", "Obtiene una carrera específica por ID",
                Map.of("id", new McpParam("string", "ID de la actividad")),
                this::getActivityById);

        registerTool("list_activities", "Lista todas las carreras registradas",
                Map.of(),
                args -> listActivities());

        registerTool("get_recent_activities", "Obtiene las últimas 10 carreras",
                Map.of(),
                args -> getRecentActivities());

        registerTool("get_summary_stats", "Resumen estadístico global",
                Map.of(),
                args -> getSummaryStats());

        registerTool("get_personal_records", "Marcas personales (1k, 5k, 10k, 15k, 21k, 42k)",
                Map.of(),
                args -> getPersonalRecords());

        registerTool("get_summary_by_period", "Estadísticas por período (THIS_MONTH, THIS_YEAR, 2024, etc.)",
                Map.of("period", new McpParam("string", "Período a consultar")),
                this::getSummaryByPeriod);

        registerTool("get_splits", "Splits por kilómetro de una carrera",
                Map.of("activityId", new McpParam("string", "ID de la actividad")),
                this::getSplits);

        registerTool("get_hr_zones", "Zonas de frecuencia cardíaca de una carrera",
                Map.of("activityId", new McpParam("string", "ID de la actividad")),
                this::getHrZones);

        registerTool("get_training_load", "Carga de entrenamiento (TRIMP, HRSS)",
                Map.of("activityId", new McpParam("string", "ID de la actividad")),
                this::getTrainingLoad);

        registerTool("get_vo2max", "VO2Max y predicciones de carrera",
                Map.of("activityId", new McpParam("string", "ID de la actividad")),
                this::getVo2Max);

        registerTool("get_acwr_status", "Estado ACWR (riesgo de lesión)",
                Map.of(),
                args -> getAcwrStatus());
    }

    private void registerTool(String name, String description,
            Map<String, McpParam> params,
            ToolFunction function) {
        tools.put(name, new McpTool(name, description, params, function));
    }

    @Scheduled(fixedRate = 60000) // Limpiar sesiones cada minuto
    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (Iterator<Map.Entry<String, SessionInfo>> it = sessions.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, SessionInfo> entry = it.next();
            if (now - entry.getValue().lastAccessed() > SESSION_TIMEOUT_MS) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("[MCP] Limpiadas {} sesiones expiradas", removed);
        }
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void handleGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String sessionId = request.getHeader("Mcp-Session-Id");
        if (sessionId == null || !sessions.containsKey(sessionId)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Missing or invalid Mcp-Session-Id header");
            return;
        }

        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        try {
            response.getWriter().write(":ok\n\n");
            response.getWriter().flush();
        } catch (IOException e) {
            log.debug("[MCP] SSE connection closed for session: {}", sessionId);
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode handleRequest(@RequestBody JsonNode request,
            @RequestHeader(value = "Mcp-Session-Id", required = false) String sessionId,
            HttpServletResponse response) {
        String method = request.path("method").asText();
        JsonNode params = request.path("params");
        int id = request.path("id").asInt(0);

        log.debug("[MCP] Método recibido: {} (session: {})", method, sessionId);

        // Handle notifications (no response expected)
        if (method.startsWith("notifications/")) {
            log.debug("[MCP] Notificación recibida: {}", method);
            response.setStatus(HttpServletResponse.SC_ACCEPTED);
            return null;
        }

        // Validar sesión para métodos que no son initialize
        if (!"initialize".equals(method) && !"ping".equals(method)) {
            if (sessionId == null || !sessions.containsKey(sessionId)) {
                return errorResponse(id, -32001, "Sesión no válida o expirada. Llame a initialize primero.");
            }
        }

        JsonNode result = switch (method) {
            case "initialize" -> initialize(id, response);
            case "ping" -> ping(id);
            case "tools/list" -> toolsList(id);
            case "tools/call" -> callTool(params, id);
            case "resources/list" -> resourcesList(id);
            case "resources/read" -> resourceRead(params, id);
            default -> errorResponse(id, -32601, "Método no encontrado: " + method);
        };

        // Actualizar último acceso y set header de sesión
        if ("initialize".equals(method)) {
            String newSessionId = UUID.randomUUID().toString();
            response.setHeader("Mcp-Session-Id", newSessionId);
            sessions.put(newSessionId, new SessionInfo(newSessionId, System.currentTimeMillis()));
            log.debug("[MCP] Nueva sesión creada: {}", newSessionId);
        } else if (sessionId != null && sessions.containsKey(sessionId)) {
            SessionInfo info = sessions.get(sessionId);
            sessions.put(sessionId, new SessionInfo(sessionId, System.currentTimeMillis()));
        }

        return result;
    }

    private JsonNode initialize(int id, HttpServletResponse httpResponse) {
        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.put("protocolVersion", PROTOCOL_VERSION);
        resultNode.putObject("capabilities")
                .putObject("tools")
                .put("listChanged", false);
        resultNode.putObject("serverInfo")
                .put("name", "running-api")
                .put("version", "1.0.0");

        ObjectNode responseNode = objectMapper.createObjectNode();
        responseNode.put("jsonrpc", "2.0");
        responseNode.put("id", id);
        responseNode.set("result", resultNode);
        return responseNode;
    }

    private JsonNode ping(int id) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.putObject("result");
        return response;
    }

    private JsonNode toolsList(int id) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        var toolsArray = response.putObject("result").putArray("tools");
        tools.values().forEach(tool -> {
            ObjectNode toolNode = toolsArray.addObject();
            toolNode.put("name", tool.name());
            toolNode.put("description", tool.description());
            ObjectNode schema = toolNode.putObject("inputSchema");
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            tool.params().forEach((paramName, param) -> {
                ObjectNode paramNode = properties.putObject(paramName);
                paramNode.put("type", param.type());
                paramNode.put("description", param.description());
            });
        });
        return response;
    }

    private JsonNode callTool(JsonNode params, int id) {
        String toolName = params.path("name").asText();
        JsonNode arguments = params.path("arguments");

        McpTool tool = tools.get(toolName);
        if (tool == null) {
            return errorResponse(id, -32602, "Herramienta no encontrada: " + toolName);
        }

        // Validate required parameters
        for (Map.Entry<String, McpParam> entry : tool.params().entrySet()) {
            if (!arguments.has(entry.getKey()) || arguments.path(entry.getKey()).asText().isBlank()) {
                return errorResponse(id, -32602, "Parámetro requerido faltante: " + entry.getKey());
            }
        }

        try {
            String result = tool.function().execute(arguments);
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.putObject("result")
                    .withArray("content")
                    .addObject()
                    .put("type", "text")
                    .put("text", result);
            return response;
        } catch (Exception e) {
            log.error("[MCP] Error ejecutando herramienta {}: {}", toolName, e.getMessage(), e);
            return errorResponse(id, -32603, "Error ejecutando herramienta: " + e.getMessage());
        }
    }

    private JsonNode resourcesList(int id) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.putObject("result").putArray("resources");
        return response;
    }

    private JsonNode resourceRead(JsonNode params, int id) {
        return errorResponse(id, -32602, "Recursos no soportados por este servidor");
    }

    private JsonNode errorResponse(int id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return response;
    }

    private String getActivityById(JsonNode args) {
        return activityTools.getActivityById(args.path("id").asText());
    }

    private String listActivities() {
        return activityTools.listActivities();
    }

    private String getRecentActivities() {
        return activityTools.getRecentActivities();
    }

    private String getSummaryStats() {
        return statsTools.getSummaryStats();
    }

    private String getPersonalRecords() {
        return statsTools.getPersonalRecords();
    }

    private String getSummaryByPeriod(JsonNode args) {
        return statsTools.getSummaryByPeriod(args.path("period").asText());
    }

    private String getSplits(JsonNode args) {
        return analyticsTools.getSplits(args.path("activityId").asText());
    }

    private String getHrZones(JsonNode args) {
        return analyticsTools.getHrZones(args.path("activityId").asText());
    }

    private String getTrainingLoad(JsonNode args) {
        return analyticsTools.getTrainingLoad(args.path("activityId").asText());
    }

    private String getVo2Max(JsonNode args) {
        return analyticsTools.getVo2Max(args.path("activityId").asText());
    }

    private String getAcwrStatus() {
        return analyticsTools.getAcwrStatus();
    }

    private record McpTool(String name, String description,
            Map<String, McpParam> params, ToolFunction function) {
    }

    private record McpParam(String type, String description) {
    }

    private record SessionInfo(String sessionId, long lastAccessed) {
    }

    @FunctionalInterface
    private interface ToolFunction {
        String execute(JsonNode arguments);
    }
}
