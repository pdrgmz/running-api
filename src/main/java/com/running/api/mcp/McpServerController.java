package com.running.api.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.running.api.mcp.tools.ActivityAnalysisTools;
import com.running.api.mcp.tools.ActivityTools;
import com.running.api.mcp.tools.AnalyticsTools;
import com.running.api.mcp.tools.StatsTools;
import com.running.api.mcp.tools.TrainingTools;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@Tag(name = "MCP (Model Context Protocol)",
     description = "Servidor MCP sobre JSON-RPC 2.0 para integrar las métricas de carrera con LLMs. "
             + "Requiere el mismo JWT que la API REST y opera solo sobre los datos del usuario autenticado.")
public class McpServerController {

    private final ObjectMapper objectMapper;
    private final ActivityTools activityTools;
    private final StatsTools statsTools;
    private final AnalyticsTools analyticsTools;
    private final TrainingTools trainingTools;
    private final ActivityAnalysisTools activityAnalysisTools;

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

        registerTool("get_training_context",
                "Contexto completo del atleta para recomendar el entrenamiento del día: resumen, ACWR, "
                        + "actividades recientes, actividad de la fecha, últimas 4 semanas, marcas personales, "
                        + "recomendación de ese día e histórico reciente. Devuelve JSON.",
                Map.of("date", new McpParam("string",
                        "Día a recomendar (YYYY-MM-DD). Opcional; por defecto hoy en la zona del atleta.",
                        false)),
                this::getTrainingContext);

        registerTool("save_daily_recommendation",
                "Valida y guarda la recomendación diaria generada por el LLM. El usuario sale del JWT. "
                        + "Devuelve JSON con ok/recommendationId o, si isError=true, las violaciones a corregir. "
                        + "El parámetro recommendationJson es un string con el objeto de la recomendación: "
                        + "{\"summary\":\"Rodaje suave de 45 min en Z2\",\"analysis\":\"ACWR estable y poca carga "
                        + "los últimos días\",\"coachNotes\":\"Mantén la respiración cómoda\",\"workout\":"
                        + "{\"type\":\"EASY\",\"title\":\"Rodaje suave\",\"description\":\"45 min en Z2\","
                        + "\"targetDistanceMeters\":8000,\"targetDurationSeconds\":2700,"
                        + "\"targetPaceMinPerKm\":5.6,\"targetHrZone\":2,\"targetLoad\":50}}. "
                        + "En días de descanso usa {\"type\":\"REST\",\"title\":\"Descanso\"} sin distancia ni "
                        + "duración. Tipos válidos: EASY, RECOVERY, LONG_RUN, TEMPO, INTERVALS, HILLS, "
                        + "RACE_PACE, RACE, CROSS_TRAINING, REST, OTHER.",
                Map.of(
                        "date", new McpParam("string",
                                "Día de la recomendación (YYYY-MM-DD). Debe coincidir con el job.", true),
                        "recommendationJson", new McpParam("string",
                                "JSON de la recomendación como string", true),
                        "replace", new McpParam("boolean",
                                "Si ya existe una recomendación para ese día, sobrescríbela.", false),
                        "requestId", new McpParam("string",
                                "Solo si el token no lo incluye; normalmente se toma del JWT.", false)),
                this::saveDailyRecommendation);

        registerTool("fail_generation_job",
                "Marca el job de generación como FAILED cuando n8n no puede completar la recomendación.",
                Map.of(
                        "error", new McpParam("string", "Descripción del fallo", true),
                        "requestId", new McpParam("string",
                                "Solo si el token no lo incluye; normalmente se toma del JWT.", false)),
                this::failGenerationJob);

        registerTool("save_activity_analysis",
                "Valida y guarda el análisis de una actividad generado por el LLM en la propia actividad. "
                        + "El usuario sale del JWT. Devuelve JSON con ok/requestId o, si isError=true, el error. "
                        + "El parámetro analysisJson es un string con el análisis: puede ser texto libre o un "
                        + "objeto JSON con las conclusiones, puntos fuertes, áreas de mejora y recomendaciones.",
                Map.of(
                        "activityId", new McpParam("string",
                                "ID de la actividad analizada. Debe coincidir con el job.", true),
                        "analysisJson", new McpParam("string",
                                "Análisis como string (texto libre o JSON serializado)", true),
                        "requestId", new McpParam("string",
                                "Solo si el token no lo incluye; normalmente se toma del JWT.", false)),
                this::saveActivityAnalysis);

        registerTool("fail_activity_analysis_job",
                "Marca el job de análisis de actividad como FAILED cuando n8n no puede completarlo.",
                Map.of(
                        "error", new McpParam("string", "Descripción del fallo", true),
                        "requestId", new McpParam("string",
                                "Solo si el token no lo incluye; normalmente se toma del JWT.", false)),
                this::failActivityAnalysisJob);
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
    @Operation(summary = "Abrir el stream SSE de una sesión MCP",
               description = "Endpoint Server-Sent Events para el transporte MCP. Requiere la cabecera Mcp-Session-Id de una sesión inicializada y un JWT válido.")
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
    @Operation(summary = "Endpoint principal MCP (JSON-RPC 2.0)",
               description = "Recibe peticiones JSON-RPC 2.0. Métodos soportados: initialize, ping, tools/list y tools/call. "
                       + "tools/list y tools/call son stateless: no requieren Mcp-Session-Id, solo el token. "
                       + "initialize devuelve opcionalmente una sesión para SSE.")
    public JsonNode handleRequest(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Petición JSON-RPC 2.0 (jsonrpc, id, method y params)", required = true)
            @RequestBody JsonNode request,
            @RequestHeader(value = "Mcp-Session-Id", required = false) String sessionId,
            HttpServletResponse response) {
        String method = request.path("method").asText();
        JsonNode params = request.path("params");
        // JSON-RPC permite que el id sea número o cadena: se conserva tal cual para devolverlo.
        JsonNode id = request.path("id");

        log.debug("[MCP] Método recibido: {} (session: {})", method, sessionId);

        // Handle notifications (no response expected)
        if (method.startsWith("notifications/")) {
            log.debug("[MCP] Notificación recibida: {}", method);
            response.setStatus(HttpServletResponse.SC_ACCEPTED);
            return null;
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
            sessions.put(sessionId, new SessionInfo(sessionId, System.currentTimeMillis()));
        }

        return result;
    }

    private JsonNode initialize(JsonNode id, HttpServletResponse httpResponse) {
        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.put("protocolVersion", PROTOCOL_VERSION);
        resultNode.putObject("capabilities")
                .putObject("tools")
                .put("listChanged", false);
        resultNode.putObject("serverInfo")
                .put("name", "running-api")
                .put("version", "1.0.0");

        ObjectNode responseNode = baseResponse(id);
        responseNode.set("result", resultNode);
        return responseNode;
    }

    private JsonNode ping(JsonNode id) {
        ObjectNode response = baseResponse(id);
        response.putObject("result");
        return response;
    }

    private JsonNode toolsList(JsonNode id) {
        ObjectNode response = baseResponse(id);
        var toolsArray = response.putObject("result").putArray("tools");
        tools.values().forEach(tool -> {
            ObjectNode toolNode = toolsArray.addObject();
            toolNode.put("name", tool.name());
            toolNode.put("description", tool.description());
            ObjectNode schema = toolNode.putObject("inputSchema");
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            ArrayNode required = objectMapper.createArrayNode();
            tool.params().forEach((paramName, param) -> {
                ObjectNode paramNode = properties.putObject(paramName);
                paramNode.put("type", param.type());
                paramNode.put("description", param.description());
                if (param.required()) {
                    required.add(paramName);
                }
            });
            if (required.size() > 0) {
                schema.set("required", required);
            }
        });
        return response;
    }

    private JsonNode callTool(JsonNode params, JsonNode id) {
        String toolName = params.path("name").asText();
        JsonNode arguments = params.path("arguments");
        if (arguments == null || arguments.isMissingNode() || arguments.isNull()) {
            arguments = objectMapper.createObjectNode();
        }

        McpTool tool = tools.get(toolName);
        if (tool == null) {
            return errorResponse(id, -32602, "Herramienta no encontrada: " + toolName);
        }

        // Validar solo los parámetros marcados como requeridos. Los booleanos,
        // objetos y arrays no se validan con asText(), que los convertiría en "".
        for (Map.Entry<String, McpParam> entry : tool.params().entrySet()) {
            if (!entry.getValue().required()) {
                continue;
            }
            JsonNode value = arguments.get(entry.getKey());
            if (value == null || value.isNull()
                    || (value.isTextual() && value.asText().isBlank())) {
                return errorResponse(id, -32602, "Parámetro requerido faltante: " + entry.getKey());
            }
        }

        try {
            McpToolResult result = tool.function().execute(arguments);
            ObjectNode response = baseResponse(id);
            ObjectNode resultNode = response.putObject("result");
            resultNode.put("isError", result.isError());
            resultNode.putArray("content")
                    .addObject()
                    .put("type", "text")
                    .put("text", result.text());
            return response;
        } catch (Exception e) {
            log.error("[MCP] Error ejecutando herramienta {}: {}", toolName, e.getMessage(), e);
            return errorResponse(id, -32603, "Error ejecutando herramienta: " + e.getMessage());
        }
    }

    private JsonNode resourcesList(JsonNode id) {
        ObjectNode response = baseResponse(id);
        response.putObject("result").putArray("resources");
        return response;
    }

    private JsonNode resourceRead(JsonNode params, JsonNode id) {
        return errorResponse(id, -32602, "Recursos no soportados por este servidor");
    }

    private JsonNode errorResponse(JsonNode id, int code, String message) {
        ObjectNode response = baseResponse(id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return response;
    }

    /** Respuesta JSON-RPC con el id original (número, cadena o nulo) y el jsonrpc ya rellenados. */
    private ObjectNode baseResponse(JsonNode id) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null || id.isMissingNode() ? NullNode.getInstance() : id);
        return response;
    }

    private McpToolResult getActivityById(JsonNode args) {
        return McpToolResult.success(activityTools.getActivityById(args.path("id").asText()));
    }

    private McpToolResult listActivities() {
        return McpToolResult.success(activityTools.listActivities());
    }

    private McpToolResult getRecentActivities() {
        return McpToolResult.success(activityTools.getRecentActivities());
    }

    private McpToolResult getSummaryStats() {
        return McpToolResult.success(statsTools.getSummaryStats());
    }

    private McpToolResult getPersonalRecords() {
        return McpToolResult.success(statsTools.getPersonalRecords());
    }

    private McpToolResult getSummaryByPeriod(JsonNode args) {
        return McpToolResult.success(statsTools.getSummaryByPeriod(args.path("period").asText()));
    }

    private McpToolResult getSplits(JsonNode args) {
        return McpToolResult.success(analyticsTools.getSplits(args.path("activityId").asText()));
    }

    private McpToolResult getHrZones(JsonNode args) {
        return McpToolResult.success(analyticsTools.getHrZones(args.path("activityId").asText()));
    }

    private McpToolResult getTrainingLoad(JsonNode args) {
        return McpToolResult.success(analyticsTools.getTrainingLoad(args.path("activityId").asText()));
    }

    private McpToolResult getVo2Max(JsonNode args) {
        return McpToolResult.success(analyticsTools.getVo2Max(args.path("activityId").asText()));
    }

    private McpToolResult getAcwrStatus() {
        return McpToolResult.success(analyticsTools.getAcwrStatus());
    }

    private McpToolResult getTrainingContext(JsonNode args) {
        return trainingTools.getTrainingContext(args);
    }

    private McpToolResult saveDailyRecommendation(JsonNode args) {
        return trainingTools.saveDailyRecommendation(args);
    }

    private McpToolResult failGenerationJob(JsonNode args) {
        return trainingTools.failGenerationJob(args);
    }

    private McpToolResult saveActivityAnalysis(JsonNode args) {
        return activityAnalysisTools.saveActivityAnalysis(args);
    }

    private McpToolResult failActivityAnalysisJob(JsonNode args) {
        return activityAnalysisTools.failActivityAnalysisJob(args);
    }

    private record McpTool(String name, String description,
            Map<String, McpParam> params, ToolFunction function) {
    }

    private record McpParam(String type, String description, boolean required) {
        private McpParam(String type, String description) {
            this(type, description, true);
        }
    }

    private record SessionInfo(String sessionId, long lastAccessed) {
    }

    @FunctionalInterface
    private interface ToolFunction {
        McpToolResult execute(JsonNode arguments);
    }
}
