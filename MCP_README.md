# MCP Server - Running API

Este proyecto incluye un **servidor MCP (Model Context Protocol)** que permite a LLMs (Claude, GPT, etc.) consultar y analizar tus datos de carreras.

---

## Autenticación

El endpoint `/mcp` está protegido con **JWT**. Todas las peticiones deben incluir la cabecera:

```
Authorization: Bearer <token>
```

Como alternativa —recomendada para integraciones de larga duración— puedes usar una **clave de
API** creada en `POST /api/v1/auth/api-keys`, sin necesidad de renovar tokens:

```
X-Api-Key: rk_Xk3pQ8aBcDeFgHiJkLmNoPqRsTuVwXyZ...
```

El token JWT (o la clave) se obtiene con `POST /api/v1/auth/login` (o `/register`). Las herramientas MCP
operan sobre las actividades del **usuario dueño del token**, por lo que cada usuario solo
ve sus propios datos.

```bash
# Obtener el token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"runner@example.com","password":"password123"}'
```

### Tokens de generación de planes

Además del JWT de acceso y la API key, el servidor acepta un **JWT efímero de generación de plan**
que emite la propia API para el workflow de n8n:

- `type=PLAN_GENERATION`, `aud=mcp`, `requestId=<job>`
- Scopes `training:read training:write`
- Vigencia corta (45 min por defecto)
- **Solo válido en `/mcp`**: no sirve para llamar a la API REST
- En `/mcp` se puede enviar como `Authorization: Bearer <token>` o como query param
  `?access_token=<token>` (necesario para el nodo *MCP Client Tool* de n8n)

Las credenciales históricas (JWT de acceso y `X-Api-Key`) no llevan scopes y conservan el acceso
completo del usuario. Cuando un token sí lleva scopes, `save_weekly_plan` y `fail_generation_job`
exigen `training:write`, y `get_training_context` exige `training:read`.

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
| `get_training_context` | Contexto completo del atleta para planificar la semana (JSON) | `weekStart` (string, opcional) |
| `save_weekly_plan` | Valida y guarda el plan semanal generado por el LLM | `weekStart` (string, requerido), `planJson` (string, requerido), `replace` (boolean, opcional), `requestId` (string, opcional) |
| `fail_generation_job` | Marca el job como `FAILED` cuando no se puede completar | `error` (string, requerido), `requestId` (string, opcional) |

Las tres últimas son las que cierran el ciclo de **generación de planes**; el resto son de consulta.

---

## Protocolo MCP

El servidor implementa el protocolo MCP versión **2024-11-05** con soporte para:

- **Transporte**: HTTP con JSON-RPC 2.0
- **SSE (Server-Sent Events)**: Para streaming de respuestas
- **Sesiones**: Mediante header `Mcp-Session-Id` (`initialize` la crea)
- **Stateless**: `tools/list` y `tools/call` no requieren sesión; basta el token
- **Notificaciones**: Soporte para `notifications/initialized`
- **Errores de herramienta**: el resultado incluye `isError` (`true` cuando el agente debe corregir
  la llamada; no es un error de transporte JSON-RPC)

---

## Endpoints

| Endpoint | Descripción |
|----------|-------------|
| `POST /mcp` | Endpoint principal MCP (JSON-RPC). Requiere `Authorization: Bearer <token>` o `?access_token=<token>` |
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

### Generación de planes

`get_training_context` devuelve un JSON con el resumen del atleta, ACWR, últimas 4 semanas,
actividades recientes, marcas personales, preferencias y plan actual:

```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "method": "tools/call",
  "params": {
    "name": "get_training_context",
    "arguments": {
      "weekStart": "2026-11-16"
    }
  }
}
```

`save_weekly_plan` recibe el plan como **string JSON** en `planJson` (el esquema de la herramienta es
plano porque MCP no anida objetos en `arguments` de forma portable):

```json
{
  "jsonrpc": "2.0",
  "id": 6,
  "method": "tools/call",
  "params": {
    "name": "save_weekly_plan",
    "arguments": {
      "weekStart": "2026-11-16",
      "replace": false,
      "planJson": "{\"summary\":\"Semana de carga moderada\",\"coachNotes\":\"Baja el jueves si el ACWR sube\",\"targetWeeklyDistanceMeters\":24000,\"targetWeeklyLoad\":220,\"workouts\":[{\"date\":\"2026-11-16\",\"type\":\"EASY\",\"title\":\"Rodaje suave\",\"description\":\"Z2\",\"targetDistanceMeters\":8000,\"targetDurationSeconds\":2880,\"targetPaceMinPerKm\":5.4,\"targetHrZone\":2,\"targetLoad\":60},{\"date\":\"2026-11-18\",\"type\":\"TEMPO\",\"title\":\"Tempo\",\"mainSet\":\"3x10' a ritmo umbral\",\"targetDistanceMeters\":10000,\"targetPaceMinPerKm\":4.6,\"targetHrZone\":4}]}"
    }
  }
}
```

Contrato de `planJson`:

| Campo | Tipo | Obligatorio |
|-------|------|-------------|
| `summary` | string | No |
| `coachNotes` | string | No |
| `targetWeeklyDistanceMeters` | number | No |
| `targetWeeklyLoad` | number | No |
| `model` / `promptVersion` | string | No |
| `workouts` | array | Sí (al menos 1) |
| `workouts[].date` | `YYYY-MM-DD` | Sí |
| `workouts[].type` | enum | Sí |
| `workouts[].title` | string | Sí |
| `workouts[].description` | string | No |
| `workouts[].targetDistanceMeters` | number | No |
| `workouts[].targetDurationSeconds` | number | No |
| `workouts[].targetPaceMinPerKm` | number (2-15) | No |
| `workouts[].targetHrZone` | integer (1-5) | No |
| `workouts[].targetLoad` | number | No |
| `workouts[].mainSet` | string | No |

Tipos válidos: `EASY`, `RECOVERY`, `LONG_RUN`, `TEMPO`, `INTERVALS`, `HILLS`, `RACE_PACE`, `RACE`,
`CROSS_TRAINING`, `OTHER`. `REST` **no** es válido: los días de descanso se omiten.

Respuesta correcta (`isError=false`):

```json
{
  "result": {
    "isError": false,
    "content": [
      {
        "type": "text",
        "text": "{\"ok\":true,\"planId\":\"...\",\"weekStart\":\"2026-11-16\",\"version\":1,\"status\":\"ACTIVE\",\"plan\":{...}}"
      }
    ]
  }
}
```

Si el plan incumple las reglas, la respuesta es `isError=true` con las violaciones para que el agente
corrija y reintente **con el mismo `requestId`**:

```json
{
  "result": {
    "isError": true,
    "content": [
      {
        "type": "text",
        "text": "{\"ok\":false,\"code\":\"PLAN_INVALID\",\"violations\":[{\"code\":\"PACE_OUT_OF_RANGE\",\"message\":\"...\"}],\"hint\":\"Corrige las violaciones y vuelve a llamar a save_weekly_plan con el mismo requestId.\"}"
      }
    ]
  }
}
```

`fail_generation_job` cierra el job cuando n8n no puede terminar:

```json
{
  "jsonrpc": "2.0",
  "id": 7,
  "method": "tools/call",
  "params": {
    "name": "fail_generation_job",
    "arguments": {
      "error": "El LLM devolvió un JSON inválido tras 2 intentos"
    }
  }
}
```

> Con el token de generación, `requestId` sale del JWT. Si además se envía como argumento, debe
> coincidir; de lo contrario la herramienta devuelve `JOB_CONFLICT`.

### Integración con n8n

El workflow de ejemplo está en [`docs/n8n/weekly-training-plan.json`](docs/n8n/weekly-training-plan.json).
La API lo llama con un JWT efímero y el body `{requestId, weekStart, replace, mcpUrl}`. El workflow
usa un **AI Agent** de n8n con un **MCP Client Tool** conectado al servidor:

1. El agente llama a `get_training_context` → contexto del atleta en JSON.
2. El LLM (OpenAI Chat Model) genera el plan JSON.
3. n8n envía el JSON a `save_weekly_plan` → validación y persistencia.
4. `fail_generation_job` (solo en la rama de error).

El nodo **MCP Client Tool** no permite credenciales dinámicas por ejecución, por lo que el token del
job viaja como query param `access_token` en la URL del MCP:

```
POST https://tu-api/mcp?access_token=<jwt>
```

La API acepta este parámetro **solo en `/mcp`**; las rutas REST siguen exigiendo la cabecera
`Authorization`. Como alternativa puedes usar la cabecera `Authorization: Bearer <token>` con
`curl` o con un nodo HTTP Request.

> La URL del MCP (`APP_TRAINING_PLAN_MCP_URL`) se resuelve **desde n8n**, no desde la API. Si n8n
> corre en otro host o contenedor, `localhost` o el nombre de servicio de Docker (`running-api`)
> no son accesibles: usa la URL pública o `host.docker.internal`. Comprueba la conectividad desde
> la máquina de n8n con `curl -i "<mcpUrl>?access_token=<token>"`; un `401` significa que llega y
> solo falta un token válido, mientras que `fetch failed` / timeout es un problema de red o DNS.

No se necesita sesión MCP ni credenciales por usuario dentro de n8n.

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

# MCP tools/list (stateless: no requiere Mcp-Session-Id)
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
  }'

# MCP tools/call (stateless)
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
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
├── McpToolResult.java                # Resultado + isError
└── tools/
    ├── ActivityTools.java            # Herramientas de actividades
    ├── StatsTools.java               # Herramientas de estadísticas
    ├── AnalyticsTools.java           # Herramientas de analíticas
    └── TrainingTools.java            # Contexto, guardado de plan y fallo de job
```

---

## Notas

- El servidor MCP usa **SSE (Server-Sent Events)** para streaming
- Las herramientas de consulta retornan texto formateado; las de planificación retornan JSON
- El endpoint `/mcp` acepta POST y GET
- Las herramientas son de lectura **salvo** `save_weekly_plan` y `fail_generation_job`, que escriben
  el plan y el estado del job del usuario autenticado
- `tools/list` y `tools/call` son **stateless**: no requieren `Mcp-Session-Id` (solo `initialize` crea sesión)
- Se requiere `Authorization: Bearer <token>` (JWT) o `X-Api-Key: rk_...` en todas las peticiones
- Los tokens con scopes exigen `training:read`/`training:write`; los tokens sin scopes conservan acceso completo
- Las herramientas operan **solo sobre los datos del usuario dueño del token**
- El protocolo implementado es **2024-11-05**
