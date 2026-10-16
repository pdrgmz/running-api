# MCP Server - Running API

Este proyecto incluye un **servidor MCP (Model Context Protocol)** que permite a LLMs (Claude, GPT, etc.) consultar y analizar tus datos de carreras.

---

## Autenticación

El endpoint `/mcp` está protegido con **JWT**. Todas las peticiones deben incluir la cabecera:

```
Authorization: Bearer <token>
```

El token se obtiene con `POST /api/v1/auth/login` (o `/register`). Las herramientas MCP
operan sobre las actividades del **usuario dueño del token**, por lo que cada usuario solo
ve sus propios datos.

```bash
# Obtener el token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"runner@example.com","password":"password123"}'
```

---

## ¿Qué es MCP?

MCP es un protocolo estándar que permite a modelos de lenguaje conectarse a APIs y datos externos. Tu API expone **herramientas** que el LLM puede llamar para obtener información.

---

## Herramientas Disponibles

| Herramienta | Descripción | Parámetros |
|-------------|-------------|------------|
| `get_activity_by_id` | Obtiene una carrera específica por ID | `id` (string, requerido) |
| `list_activities` | Lista todas las carreras registradas | - |
| `get_recent_activities` | Obtiene las últimas 10 carreras | - |
| `get_summary_stats` | Resumen estadístico global | - |
| `get_personal_records` | Marcas personales (1k, 5k, 10k, 15k, 21k, 42k) | - |
| `get_summary_by_period` | Estadísticas por período (mes, año, semana) | `period` (string, requerido) |
| `get_splits` | Splits por kilómetro de una carrera | `activityId` (string, requerido) |
| `get_hr_zones` | Distribución de zonas de frecuencia cardíaca | `activityId` (string, requerido) |
| `get_training_load` | Carga de entrenamiento (TRIMP, HRSS) | `activityId` (string, requerido) |
| `get_vo2max` | Estimación VO2Max y predicciones de carrera | `activityId` (string, requerido) |
| `get_acwr_status` | Estado ACWR (riesgo de lesión) | - |

---

## Protocolo MCP

El servidor implementa el protocolo MCP versión **2024-11-05** con soporte para:

- **Transporte**: HTTP con JSON-RPC 2.0
- **SSE (Server-Sent Events)**: Para streaming de respuestas
- **Sesiones**: Mediante header `Mcp-Session-Id`
- **Notificaciones**: Soporte para `notifications/initialized`

---

## Endpoints

| Endpoint | Descripción |
|----------|-------------|
| `POST /mcp` | Endpoint principal MCP (JSON-RPC). Requiere `Authorization: Bearer <token>` |
| `GET /mcp` | Endpoint SSE (requiere `Mcp-Session-Id` y `Authorization: Bearer <token>`) |

---

## Cómo usar con Claude Desktop

### 1. Instalar Claude Desktop

Descargar de: https://claude.ai/download

### 2. Configurar MCP Server

Editar `claude_desktop_config.json`:

**Windows:**
```
%APPDATA%\Claude\claude_desktop_config.json
```

**Mac:**
```
~/Library/Application Support/Claude/claude_desktop_config.json
```

**Linux:**
```
~/.config/Claude/claude_desktop_config.json
```

### 3. Agregar configuración

```json
{
  "mcpServers": {
    "running-api": {
      "url": "http://localhost:8080/mcp",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "Bearer <token>"
      }
    }
  }
}
```

### 4. Reiniciar Claude Desktop

Reiniciar la aplicación para que cargue la configuración.

---

## Cómo usar con OpenCode

### 1. Agregar MCP Server a OpenCode

Editar `opencode.json`:

```json
{
  "mcpServers": {
    "running-api": {
      "url": "http://localhost:8080/mcp",
      "headers": {
        "Authorization": "Bearer <token>"
      }
    }
  }
}
```

### 2. Reiniciar OpenCode

Reiniciar para que cargue la configuración.

---

## Ejemplos de Uso

### Inicialización del protocolo

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {}
}
```

### Listar herramientas

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list",
  "params": {}
}
```

### Llamar una herramienta

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "get_summary_stats",
    "arguments": {}
  }
}
```

### Con parámetros

```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "tools/call",
  "params": {
    "name": "get_activity_by_id",
    "arguments": {
      "id": "2024-01-15T08:30:00"
    }
  }
}
```

---

## Desarrollo

### Levantar el servidor MCP

```bash
# Con Maven
mvn spring-boot:run

# Con Docker
docker-compose up -d
```

### Verificar que funcione

```bash
# MCP initialize
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {}
  }'

# MCP tools/list
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -H "Mcp-Session-Id: <session-id>" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
  }'

# MCP tools/call
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -H "Mcp-Session-Id: <session-id>" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "get_summary_stats",
      "arguments": {}
    }
  }'
```

---

## Estructura de Archivos

```
src/main/java/com/running/api/mcp/
├── McpServerController.java          # Controlador principal MCP
└── tools/
    ├── ActivityTools.java            # Herramientas de actividades
    ├── StatsTools.java               # Herramientas de estadísticas
    └── AnalyticsTools.java           # Herramientas de analíticas
```

---

## Notas

- El servidor MCP usa **SSE (Server-Sent Events)** para streaming
- Todas las herramientas retornan texto formateado para fácil lectura por el LLM
- El endpoint `/mcp` acepta POST y GET
- Las herramientas son **solo lectura** (no modifican datos)
- Se requiere el header `Mcp-Session-Id` después de la inicialización
- Se requiere `Authorization: Bearer <token>` en todas las peticiones (JWT)
- Las herramientas operan **solo sobre los datos del usuario dueño del token**
- El protocolo implementado es **2024-11-05**
