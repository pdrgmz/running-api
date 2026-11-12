# Running

> API REST de analítica de carreras y procesamiento de archivos TCX, GPX y FIT

## Descripción

**Running** es una aplicación backend construida con **Spring Boot 3.2** y **Java 21** que permite importar, almacenar y analizar actividades de carrera desde archivos **TCX**, **GPX** y **FIT** (formatos estándar de Garmin y otros dispositivos GPS). Ofrece métricas fisiológicas avanzadas, estadísticas acumuladas, récords personales y exportación geoespacial.

## Tabla de Contenidos

- [Stack Tecnológico](#stack-tecnológico)
- [Funcionalidades](#funcionalidades)
- [Estructura del Proyecto](#estructura-del-proyecto)
- [Configuración](#configuración)
- [Despliegue con Docker](#despliegue-con-docker)
- [Autenticación](#autenticación)
- [Endpoints](#endpoints)
- [Modelos de Datos](#modelos-de-datos)
- [Pruebas y Calidad de Código](#pruebas-y-calidad-de-código)
- [Documentación de la API](#documentación-de-la-api)

---

## Stack Tecnológico

| Capa | Tecnología |
|------|-----------|
| **Lenguaje** | Java 21 |
| **Framework** | Spring Boot 3.2.3 |
| **ORM** | Spring Data JPA + Hibernate |
| **Base de datos** | PostgreSQL 16 |
| **Parser XML** | Jackson XML (`jackson-dataformat-xml`) |
| **Documentación API** | OpenAPI 3 / Swagger UI (springdoc 2.3.0) |
| **Utilidades** | Lombok |
| **Contenedorización** | Docker + Docker Compose |

---

## Funcionalidades

### Autenticación y Multiusuario
- Registro y login con **JWT** (HS256) y contraseñas cifradas con BCrypt
- Token de acceso + **refresh token** para renovación
- **Claves de API** (`X-Api-Key`) para automatizaciones (n8n, scripts, cron), sin gestión de tokens
- Aislamiento total por usuario: actividades, estadísticas, récords y analítica
- Servidor **MCP** protegido con el mismo JWT

### Gestión de Archivos
- Importación individual de archivos `.tcx`, `.gpx` y `.fit`
- Importación masiva mediante archivos `.zip` con múltiples archivos (TCX, GPX y FIT)
- Exportación de datos en formato JSON comprimido en ZIP
- Exportación de archivos físicos de respaldo en ZIP
- Respaldo automático de archivos importados en disco
- Almacenamiento de metadatos extraídos de cada formato en campo `extraData`

### Actividades
- Listado paginado y ordenado de carreras
- Consulta de detalle por ID
- Trackpoints con muestreo opcional (`low`, `medium`, `high`)
- Completado de telemetría: rellena latitud, longitud, altitud, FC, cadencia y velocidad
  interpolando con los puntos vecinos
- Si falta altitud, un cron la completa con el modelo digital de elevaciones de Open-Meteo
  a partir de las coordenadas GPS, respetando la cuota del proveedor
- Eliminación individual o total de la base de datos

### Estadísticas y Récords
- Resumen global acumulado (distancia, tiempo, desnivel, calorías)
- Filtrado por período (`THIS_MONTH`, `THIS_YEAR`, año específico)
- Marcas personales (PB) calculadas con ventana móvil de telemetría:
  - 1k, 5k, 10k, 15k, 21k (media maratón), 42k (maratón)

### Analítica Deportiva Avanzada
- **Splits por kilómetro** — Desglose de ritmo y frecuencia cardíaca por km
- **Zonas de frecuencia cardíaca** — Distribución del tiempo en 5 zonas
- **Desacople aeróbico (Cardiac Drift)** — Análisis de deriva cardíaca
- **Carga de entrenamiento** — TRIMP / HRSS / Esfuerzo
- **VO2Max y VAM** — Estimaciones fisiológicas
- **PMC (Performance Management Chart)** — CTL, ATL, TSB con histórico
- **Número de Eddington** — Métrica de consistencia de carreras
- **ACWR** — Acute:Chronic Workload Ratio 7:28 con evaluación de riesgo de lesión

### Recomendación Diaria con IA
- **Generación diaria automática** por la mañana en la zona horaria del atleta, o manual bajo demanda
- **n8n agnóstico al usuario**: la API firma un JWT efímero (`aud=mcp`) y lo entrega al webhook
- **Contexto del atleta por MCP** (`get_training_context`): resumen, ACWR, últimas 4 semanas,
  actividades recientes, actividad del día, marcas personales y recomendaciones previas
- **La API es la fuente de verdad**: valida y persiste la recomendación (`save_daily_recommendation`)
  antes de que exista
- **Una recomendación por día con histórico**: se puede consultar hoy, por fecha o en un rango
- **Los días de descanso se representan con `REST`**: la recomendación siempre incluye una sesión
- **Preferencias por atleta**: activación, zona horaria, hora local de generación y notas
- **Idempotencia**: regenerar sobrescribe el día; los reintentos de n8n devuelven la misma recomendación
- Workflow de n8n listo para importar en
  [`docs/n8n/daily-workout-recommendation.json`](docs/n8n/daily-workout-recommendation.json)

### Exportación Geoespacial
- **GeoJSON** — FeatureCollection con la ruta GPS
- **GPX 1.1** — Exportación estandarizada con descarga de archivo

---

## Estructura del Proyecto

```
src/main/java/com/running/api/
├── config/
│   ├── AdminSeeder.java         # Crea el admin y le asigna actividades sin dueño
│   ├── AsyncConfig.java         # Executors @Async (taskExecutor, zipExecutor)
│   ├── CorsConfig.java          # Configuración CORS
│   └── OpenApiConfig.java       # Configuración OpenAPI/Swagger
├── controller/
│   ├── ActivityController.java  # CRUD de actividades
│   ├── AnalyticsController.java # Métricas avanzadas
│   ├── ApiKeyController.java    # Gestión de claves de API (X-Api-Key)
│   ├── AuthController.java      # Registro, login, refresh y /me
│   ├── FilesController.java     # Importación/exportación TCX/GPX/FIT
│   ├── GeoController.java       # Exportación GeoJSON/GPX
│   ├── StatsController.java     # Estadísticas y récords
│   └── TrainingController.java  # Recomendaciones diarias, jobs y preferencias
├── dto/
│   ├── AcwrStatusDto.java
│   ├── ApiKeyCreatedResponseDto.java  # Clave recién creada (valor visible una sola vez)
│   ├── ApiKeyResponseDto.java   # Metadatos de claves de API (sin valor)
│   ├── AuthResponseDto.java     # Tokens + datos del usuario
│   ├── BulkUploadStatusDto.java
│   ├── CardiacDriftResponseDto.java
│   ├── CreateApiKeyRequestDto.java # Nombre y vigencia de la clave
│   ├── DaytimeStatsDto.java
│   ├── EddingtonStatsDto.java
│   ├── ErrorResponseDto.java
│   ├── FileErrorDto.java
│   ├── FileUploadResponseDto.java
│   ├── FitActivityDTO.java
│   ├── GeoJsonDto.java
│   ├── HrZoneDetailDto.java
│   ├── HrZonesMapDto.java
│   ├── HrZonesResponseDto.java
│   ├── LoginRequestDto.java     # Credenciales de login
│   ├── PmcPointDto.java
│   ├── RacePredictionsDto.java
│   ├── RecordResponseDto.java
│   ├── RefreshTokenRequestDto.java
│   ├── RegisterRequestDto.java  # Datos de registro
│   ├── SplitDto.java
│   ├── StreakStatsDto.java
│   ├── SummaryStatsDto.java
│   ├── TrainingLoadResponseDto.java
│   ├── UserResponseDto.java     # Datos públicos del usuario
│   ├── Vo2MaxVamResponseDto.java
│   ├── WeekdayStatsDto.java
│   ├── DailyRecommendationResponseDto.java / DailyRecommendationSummaryDto.java
│   ├── TrainingPreferencesDto.java / UpdateTrainingPreferencesRequestDto.java
│   ├── GenerateDailyRecommendationRequestDto.java / GenerateDailyRecommendationResponseDto.java
│   ├── DailyGenerationJobResponseDto.java / ValidationViolationDto.java
│   ├── TrainingContextDto.java  # Contexto enviado al LLM vía MCP
│   └── DailyRecommendationPayload.java # Contrato JSON de save_daily_recommendation
├── exception/
│   ├── DuplicateEmailException.java
│   ├── ForbiddenOperationException.java
│   ├── GlobalExceptionHandler.java
│   ├── InvalidCredentialsException.java
│   ├── ResourceNotFoundException.java
│   ├── RecommendationValidationException.java
│   ├── RecommendationAlreadyExistsException.java
│   ├── GenerationJobStateException.java
│   ├── GenerationException.java
│   └── GenerationNotConfiguredException.java
├── mapper/
│   └── ActivityMapper.java      # Mapeo FitActivityDTO -> Activity
├── mcp/
│   ├── McpServerController.java # Servidor MCP (JSON-RPC)
│   ├── McpToolResult.java       # Resultado con isError para el agente
│   └── tools/
│       ├── ActivityTools.java
│       ├── AnalyticsTools.java
│       ├── StatsTools.java
│       └── TrainingTools.java   # Contexto, recomendación diaria y fallo de job
├── model/
│   ├── Activity.java            # Entidad principal de carrera
│   ├── ActivityTrackpoint.java  # Puntos de telemetría segundo a segundo
│   ├── ApiKey.java              # Clave de API (hash SHA-256, caducidad y último uso)
│   ├── GlobalSummaryStats.java  # Estadísticas globales acumuladas (por usuario)
│   ├── PersonalRecord.java      # Récords personales por distancia (por usuario)
│   ├── DailyRecommendation.java # Recomendación diaria validada (una por día)
│   ├── DailyGenerationJob.java  # Job de generación (requestId, estado, recomendación)
│   ├── TrainingPreferences.java # Activación, zona horaria y hora de generación
│   ├── WorkoutType.java / TrainingJobStatus.java / TrainingJobSource.java
│   └── User.java                # Usuario (email + hash de contraseña + rol)
├── repository/
│   ├── ActivityRepository.java
│   ├── ActivityTrackpointRepository.java
│   ├── ApiKeyRepository.java
│   ├── GlobalSummaryStatsRepository.java
│   ├── PersonalRecordRepository.java
│   ├── DailyRecommendationRepository.java
│   ├── DailyGenerationJobRepository.java
│   ├── TrainingPreferencesRepository.java
│   ├── UserRepository.java
│   └── projection/
│       └── DailyTrimpProjection.java
├── security/
│   ├── ApiKeyAuthenticationFilter.java # Valida la cabecera X-Api-Key
│   ├── CurrentUserService.java       # Resuelve el userId del SecurityContext
│   ├── JwtAuthenticationFilter.java  # Valida el Bearer token en cada petición
│   ├── JwtService.java               # Emisión/validación de tokens JWT
│   ├── SecurityConfig.java           # Cadena de filtros, rutas públicas y 401/403 JSON
│   └── UserPrincipal.java            # Principal autenticado (id, email, rol, scopes, aud, requestId)
├── service/
│   ├── ActivityService.java
│   ├── AnalyticsService.java
│   ├── ApiKeyService.java        # Creación, listado, revocación y validación de claves
│   ├── AuthService.java         # Registro, login y refresh (BCrypt)
│   ├── BackupStorageService.java
│   ├── BulkUploadStatusTracker.java
│   ├── FileService.java
│   ├── FitParserService.java
│   ├── GeoService.java
│   ├── GpxParserService.java
│   ├── StatsService.java
│   ├── DailyRecommendationService.java  # Validación, persistencia e histórico diario
│   ├── TrainingContextService.java      # Contexto del atleta para el LLM
│   ├── DailyRecommendationGenerationService.java # Job + JWT efímero + webhook n8n
│   ├── DailyRecommendationScheduler.java # Barrido horario y reconciliación de jobs
│   ├── N8nDailyWebhookClient.java       # Cliente HTTP del webhook de n8n
│   ├── TcxParserService.java
│   ├── TrackpointCompletionService.java # Completa huecos de telemetría al importar
│   ├── ElevationProvider.java           # Puerto de proveedores de elevación
│   └── OpenMeteoElevationProvider.java  # DEM real vía Open-Meteo
├── util/
│   ├── AltitudeUtils.java        # Altitud válida/ausente (distingue el 0 real del pendiente)
│   ├── ElevationAccumulator.java # Desnivel D+/D- con histéresis (filtro de ruido GPS)
│   ├── GeoUtils.java             # Distancia Haversine
│   ├── RunningMetrics.java       # TRIMP / VAM / VO2Max / detección de deporte
│   ├── TrainingTimeUtils.java    # Zonas horarias y hora local de generación
│   └── SeriesInterpolation.java  # Relleno lineal de series con huecos
└── RunningApiApplication.java
```

---

## Configuración

### Variables de Entorno

| Variable | Valor por defecto | Descripción |
|----------|-------------------|-------------|
| `APP_PORT` | `8080` | Puerto expuesto en el host |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173,...` | Orígenes permitidos para CORS |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/running_api` | URL de conexión a PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `runninging` | Credenciales de PostgreSQL |
| `APP_STORAGE_LOCATION` | `./storage` | Ruta de respaldo de archivos de actividad |
| `APP_USER_MAX_HR` | `185` | Frecuencia cardíaca máxima para zonas FC |
| `APP_ELEVATION_MIN_THRESHOLD_METERS` | `0.1` | Umbral de histéresis para filtrar ruido GPS |
| `APP_ELEVATION_EXTERNAL_ENABLED` | `true` | Consultar un DEM externo cuando toda la altitud sea 0/nula |
| `APP_ELEVATION_API_URL` | `https://api.open-meteo.com/v1/elevation` | Endpoint del proveedor de elevaciones |
| `APP_ELEVATION_API_KEY` | *(vacío)* | API key opcional; si se define, usa el endpoint de cliente |
| `APP_ELEVATION_BATCH_SIZE` | `50` | Coordenadas por petición al proveedor |
| `APP_ELEVATION_TIMEOUT_SECONDS` | `10` | Timeout de las peticiones de elevación |
| `APP_ELEVATION_MIN_INTERVAL_MILLIS` | `100` | Espera mínima entre peticiones al proveedor |
| `APP_ELEVATION_MAX_CALLS_PER_MINUTE` | `500` | Presupuesto de coordenadas por minuto (límite gratuito: 600) |
| `APP_ELEVATION_MAX_RETRIES` | `2` | Reintentos ante 429 o errores 5xx |
| `APP_ELEVATION_RETRY_BACKOFF_MILLIS` | `2000` | Backoff inicial entre reintentos |
| `APP_ELEVATION_MAX_RETRY_AFTER_SECONDS` | `60` | Espera máxima que se respeta de `Retry-After` |
| `APP_ELEVATION_BACKFILL_ENABLED` | `true` | Activar el cron de completado de altitudes |
| `APP_ELEVATION_BACKFILL_CRON` | `0 */15 * * * *` | Ejecución cada 15 minutos |
| `APP_ELEVATION_BACKFILL_MAX_CALLS_PER_RUN` | `80` | Coordenadas máximas por ejecución |
| `APP_ELEVATION_BACKFILL_MAX_ACTIVITIES_PER_RUN` | `50` | Actividades máximas a revisar por ejecución |
| `APP_ELEVATION_BACKFILL_SAMPLE_DISTANCE_METERS` | `90` | Separación mínima entre coordenadas consultadas |
| `APP_JWT_SECRET` | *(clave de desarrollo)* | Clave de firma de los JWT (HS256). **Obligatorio cambiarla en producción** |
| `APP_JWT_EXPIRATION_MINUTES` | `1440` | Vigencia del token de acceso en minutos (24 h) |
| `APP_JWT_REFRESH_EXPIRATION_MINUTES` | `10080` | Vigencia del refresh token en minutos (7 días) |
| `APP_ADMIN_EMAIL` | `admin@running.local` | Email del usuario administrador sembrado al arrancar |
| `APP_ADMIN_PASSWORD` | `Admin1234!` | Contraseña inicial del administrador (solo se aplica al crearlo) |
| `APP_TRAINING_DAILY_ENABLED` | `false` | Activa el cron diario (la generación manual funciona igualmente) |
| `APP_TRAINING_DAILY_CRON` | `0 0 * * * *` | Barrido horario; procesa a cada atleta cuando su hora local alcanza su hora de generación |
| `APP_TRAINING_DAILY_DEFAULT_GENERATION_HOUR` | `6` | Hora local (0-23) por defecto si el atleta no configura otra |
| `APP_TRAINING_DAILY_N8N_WEBHOOK_URL` | *(vacío)* | URL del webhook de n8n. Sin ella, `POST /recommendations/generate` responde `503` |
| `APP_TRAINING_DAILY_MCP_URL` | `http://localhost:8080/mcp` | URL del MCP **tal como la ve n8n** (se envía en el body del webhook). Debe ser resoluble desde n8n |
| `APP_TRAINING_DAILY_TOKEN_TTL_MINUTES` | `45` | Vigencia del JWT efímero entregado a n8n |
| `APP_TRAINING_DAILY_JOB_TIMEOUT_MINUTES` | `60` | Tras este tiempo, un job `PENDING`/`RUNNING` pasa a `TIMEOUT` |
| `APP_TRAINING_DAILY_MAX_ATTEMPTS` | `3` | Reintentos del webhook de n8n al crear un job programado |
| `APP_TRAINING_DAILY_RETRY_BACKOFF_MILLIS` | `2000` | Backoff entre reintentos del webhook |
| `APP_TRAINING_DAILY_RECONCILE_CRON` | `0 */10 * * * *` | Cron de reconciliación de jobs caducados |
| `APP_TRAINING_DAILY_WEBHOOK_TIMEOUT_SECONDS` | `20` | Timeout de la llamada de la API al webhook (no espera al LLM) |

> `APP_TRAINING_DAILY_MCP_URL` se resuelve **desde el contenedor/host de n8n**, no desde la API. Si
> n8n no comparte red con la API, `http://localhost:8080/mcp` y `http://running-api:8080/mcp` no
> funcionan: usa la URL pública (por ejemplo `https://running-api.pdrgmz.uk/mcp`) o
> `http://host.docker.internal:8080/mcp` si ambos están en la misma máquina.

### Archivo `application.yml`

```yaml
server:
  port: 8080
  forward-headers-strategy: framework

spring:
  autoconfigure:
    # Esta API usa JWT: desactiva el usuario "user" por defecto y su password generado
    exclude:
      - org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
  datasource:
    url: jdbc:postgresql://localhost:5433/running_api
    username: runninging
    password: runninging
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 100MB

app:
  storage:
    location: ./storage
  user:
    max-hr: 185
  elevation:
    min-threshold-meters: 0.1
    api-key: ${APP_ELEVATION_API_KEY:}
    backfill:
      enabled: true
      cron: 0 */15 * * * *
      max-calls-per-run: 80
      sample-distance-meters: 90
  cors:
    allowed-origins: http://localhost:3000,http://localhost:5173
  openapi:
    server-url: https://running-api.pdrgmz.uk
  auth:
    jwt-secret: ${APP_JWT_SECRET:cambia-esta-clave-secreta-de-desarrollo-minimo-32-bytes}
    jwt-expiration-minutes: ${APP_JWT_EXPIRATION_MINUTES:1440}
    refresh-expiration-minutes: ${APP_JWT_REFRESH_EXPIRATION_MINUTES:10080}
    admin-email: ${APP_ADMIN_EMAIL:admin@running.local}
    admin-password: ${APP_ADMIN_PASSWORD:Admin1234!}
  training:
    daily:
      enabled: ${APP_TRAINING_DAILY_ENABLED:false}
      cron: ${APP_TRAINING_DAILY_CRON:0 0 * * * *}
      default-generation-hour: ${APP_TRAINING_DAILY_DEFAULT_GENERATION_HOUR:6}
      n8n-webhook-url: ${APP_TRAINING_DAILY_N8N_WEBHOOK_URL:}
      mcp-url: ${APP_TRAINING_DAILY_MCP_URL:http://localhost:8080/mcp}
      token-ttl-minutes: ${APP_TRAINING_DAILY_TOKEN_TTL_MINUTES:45}
      job-timeout-minutes: ${APP_TRAINING_DAILY_JOB_TIMEOUT_MINUTES:60}
      reconcile-cron: ${APP_TRAINING_DAILY_RECONCILE_CRON:0 */10 * * * *}
      webhook-timeout-seconds: ${APP_TRAINING_DAILY_WEBHOOK_TIMEOUT_SECONDS:20}
```

---

## Despliegue con Docker

### Requisitos
- Docker
- Docker Compose

### Comandos

```bash
# Construir y levantar el contenedor
docker-compose up -d --build

# Ver logs
docker-compose logs -f

# Detener
docker-compose down
```

### Volúmenes

| Volumen | Contenido |
|---------|-----------|
| `postgres_data` | Datos de PostgreSQL |
| `running_api_storage` | Archivos de actividad de respaldo |

---

## Autenticación

La API está protegida con **JWT** (HS256). Salvo los endpoints públicos de registro/login/refresh,
todas las rutas —incluido el servidor **MCP**— requieren una cabecera:

```
Authorization: Bearer <token>
```

Como alternativa para automatizaciones, se admiten **claves de API** en la cabecera
`X-Api-Key` (ver [Claves de API](#claves-de-api-automatizaciones)).

Cada usuario **solo puede ver y modificar sus propias actividades**, estadísticas, récords y
analítica. No existe acceso cruzado entre usuarios.

### Flujo típico

1. **Registro** (`POST /api/v1/auth/register`) o **login** (`POST /api/v1/auth/login`).
   La respuesta incluye el `token` de acceso y un `refreshToken`.
2. Usar el `token` en `Authorization: Bearer <token>` en cada petición.
3. Cuando el token de acceso expire, renovarlo con `POST /api/v1/auth/refresh`
   enviando el `refreshToken`.

### Respuesta de autenticación

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 86400,
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshExpiresInSeconds": 604800,
  "user": { "id": 1, "email": "runner@example.com", "role": "USER" }
}
```

### Ejemplos

```bash
# Registro
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"runner@example.com","password":"password123"}'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"runner@example.com","password":"password123"}'

# Renovar el token de acceso
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'

# Consultar el usuario autenticado
curl http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer <token>"
```

### Tokens

| Tipo | Claim `type` | Vigencia por defecto | Uso |
|------|--------------|----------------------|-----|
| **Acceso** | `ACCESS` | 24 h (`APP_JWT_EXPIRATION_MINUTES`) | Cabecera `Authorization: Bearer` |
| **Refresco** | `REFRESH` | 7 días (`APP_JWT_REFRESH_EXPIRATION_MINUTES`) | Solo en `POST /api/v1/auth/refresh` |
| **Generación diaria** | `GENERATION` | 45 min (`APP_TRAINING_DAILY_TOKEN_TTL_MINUTES`) | Solo en `/mcp`, `aud=mcp`, scopes `training:read training:write` |

Un refresh token **no** sirve como token de acceso ni viceversa. El refresh no requiere
cabecera `Authorization`: se autentica con el propio refresh token.

El token de generación es **efímero y de mínimo privilegio**: lo emite la API para un job
concreto, viaja como `Authorization` hacia n8n y solo se acepta en `/mcp`. Incluye un claim
`requestId` que la API usa para correlacionar el guardado; n8n nunca recibe credenciales
permanentes ni el `userId`.

### Claves de API (automatizaciones)

Para integraciones máquina a máquina (n8n, scripts, cron, CI) sin gestionar la caducidad
del JWT, cada usuario puede crear **claves de API** que se envían en la cabecera:

```
X-Api-Key: rk_Xk3pQ8aBcDeFgHiJkLmNoPqRsTuVwXyZ...
```

- La clave actúa **como el usuario que la crea**: mismos datos y permisos.
- El valor completo (`rk_` + 43 caracteres aleatorios) **solo se muestra al crearla**;
  la API guarda únicamente su hash SHA-256.
- Caducidad opcional (`expiresInDays`, de 1 a 3650 días); si se omite, la clave no caduca.
- El listado muestra `lastUsedAt` y la clave puede revocarse en cualquier momento
  (deja de autenticar inmediatamente).

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `POST` | `/api/v1/auth/api-keys` | Crear una clave (requiere autenticación) |
| `GET` | `/api/v1/auth/api-keys` | Listar metadatos de las claves propias |
| `DELETE` | `/api/v1/auth/api-keys/{id}` | Revocar una clave propia |

```bash
# Crear una clave (con JWT)
curl -X POST http://localhost:8080/api/v1/auth/api-keys \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"n8n","expiresInDays":365}'

# Usarla en cualquier endpoint protegido
curl -X POST http://localhost:8080/api/v1/files/upload \
  -H "X-Api-Key: rk_Xk3pQ8aB..." \
  -F "file=@actividad.gpx"

# Revocarla
curl -X DELETE http://localhost:8080/api/v1/auth/api-keys/1 \
  -H "Authorization: Bearer <token>"
```

> ⚠️ Trata la clave como una contraseña: da acceso completo a la cuenta. En n8n guárdala en un
> credential o variable de entorno, nunca dentro del propio workflow.

### Usuario administrador

Al arrancar, si no existe, se crea un usuario administrador (`APP_ADMIN_EMAIL` /
`APP_ADMIN_PASSWORD`) y se le asignan las actividades históricas que aún no tengan dueño
(útil al actualizar una base de datos previa a la incorporación de la autenticación).

> ⚠️ La contraseña del administrador solo se aplica **al crear** el usuario. Cambiar
> `APP_ADMIN_PASSWORD` después no actualiza la contraseña existente.

---

## Endpoints

> Salvo los marcados como **públicos**, todos los endpoints requieren
> `Authorization: Bearer <token>`. Cada usuario accede únicamente a sus propios datos.

### Autenticación (`/api/v1/auth`)

| Método | Path | Público | Descripción |
|--------|------|---------|-------------|
| `POST` | `/register` | ✅ | Registrar un usuario y devolver sus tokens |
| `POST` | `/login` | ✅ | Iniciar sesión y obtener los tokens |
| `POST` | `/refresh` | ✅ | Renovar el token de acceso con un refresh token |
| `GET` | `/me` | ❌ | Datos del usuario autenticado |

### Claves de API (`/api/v1/auth/api-keys`)

| Método | Path | Descripción |
|--------|------|-------------|
| `POST` | `/` | Crear una clave de API (el valor solo se muestra una vez) |
| `GET` | `/` | Listar las claves propias (nombre, prefijo, fechas; sin valores) |
| `DELETE` | `/{id}` | Revocar una clave propia |

> Se gestionan con JWT o con otra clave válida. Una clave revocada deja de autenticar de inmediato.
> En endpoints protegidos se usa como alternativa a `Authorization`: `X-Api-Key: rk_...`.

### Archivos (`/api/v1/files`)

| Método | Path | Descripción |
|--------|------|-------------|
| `POST` | `/upload` | Importar un archivo individual (TCX, GPX o FIT) |
| `POST` | `/upload-bulk` | Importar un ZIP con múltiples archivos (TCX, GPX y FIT) |
| `GET` | `/upload-status/{uploadId}` | Consultar el estado de una carga masiva |
| `GET` | `/upload-status/{uploadId}/stream` | Stream SSE del progreso de la carga |
| `GET` | `/export-bulk` | Exportar datos JSON en ZIP |
| `GET` | `/export-backup-zip` | Exportar archivos físicos de respaldo en ZIP |

### Actividades (`/api/v1/activities`)

| Método | Path | Descripción |
|--------|------|-------------|
| `GET` | `/` | Listar actividades (paginado) |
| `GET` | `/{id}` | Obtener actividad por ID |
| `GET` | `/{id}/trackpoints` | Obtener trackpoints (con `?resolution=`) |
| `DELETE` | `/{id}` | Eliminar actividad por ID |
| `DELETE` | `/` | Vaciar toda la base (asíncrono; devuelve `202` con un `deleteId`) |
| `GET` | `/delete-status/{deleteId}` | Consultar el progreso del vaciado |
| `POST` | `/recalculate-metrics` | Recalcular training load, VO2Max y VAM de todas las actividades con la FC máx del atleta |

### Actividades globales (`/api/v1/activities`)

| Método | Path | Descripción |
|--------|------|-------------|
| `GET` | `/elevation-coverage` | Porcentaje de trackpoints con altitud y pendientes |

#### Vaciado asíncrono

`DELETE /api/v1/activities` no bloquea: encola el borrado y responde `202 Accepted` con un
objeto de estado.

```json
{
  "deleteId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "PENDING",
  "totalActivities": 0,
  "deletedActivities": 0,
  "progressPercent": 0.0,
  "message": "Vaciado en cola"
}
```

El progreso se consulta con `GET /api/v1/activities/delete-status/{deleteId}` (`PENDING`,
`PROCESSING`, `COMPLETED`, `FAILED`). El borrado es todo o nada: si algo falla, la transacción
hace rollback y el estado queda en `FAILED` con el error. Solo se conserva el último vaciado de
cada usuario.

#### Backfill de altitudes

La importación individual y masiva completa los huecos de la telemetría antes de guardar la
actividad:

- **Latitud/longitud**: interpolación lineal entre los puntos GPS válidos vecinos.
- **FC, cadencia y velocidad**: se descartan las lecturas fuera de rango fisiológico y se
  interpolan los huecos con los vecinos.
- **Altitud**: solo se interpola entre altitudes existentes; **no consulta** proveedores externos
  durante la importación.
- **Distancia acumulada**: se descartan retrocesos y saltos que implicarían más de 15 m/s.

La altitud que falta (o vale 0) y no se puede interpolar la completa un cron
(`ElevationBackfillService`) que se ejecuta cada 15 minutos y:

- Busca actividades con algún trackpoint sin altitud útil y coordenadas GPS válidas.
- También detecta actividades con **altitud constante sospechosa**: el mismo valor exacto en al
  menos el 90% de los trackpoints registrados por el dispositivo (con un mínimo de 10 puntos).
  Esas altitudes se tratan como ausentes para recalcularlas; las ya calculadas por el backfill
  no cuentan, así que una actividad deja de estar pendiente en cuanto se recalcula.
- Procesa primero las actividades más antiguas, mezclando ambos motivos.
- Envía **solo trackpoints sin altitud o con altitud plana sospechosa**, separados al menos 90 m entre sí.
- Interpola el resto entre las coordenadas consultadas y recalcula D+/D- y las estadísticas
  globales del usuario.
- No extrapola el final de la actividad: si queda tramo pendiente, el siguiente lote continúa
  desde ahí.

Con el presupuesto por defecto (80 coordenadas cada 15 minutos) el uso queda así:

| Ventana | Límite Open-Meteo | Uso con 80/15 min | Margen |
|---------|------------------:|------------------:|-------:|
| Minuto | 600 | 80 por ejecución | 520 |
| Hora | 5.000 | 320 (4 × 80) | 4.680 |
| Día | 10.000 | 7.680 (96 × 80) | 2.320 |
| Mes | 300.000 | ~230.400 | ~69.600 |

Ese margen deja cuota para reintentos y picos puntuales. Si necesitas
acelerar el backfill, sube `APP_ELEVATION_BACKFILL_MAX_CALLS_PER_RUN`, pero mantén el uso diario
por debajo de 10.000: por ejemplo, 100 cada 15 minutos son 9.600/día y ya dejan muy poco margen.
Si defines la expresión cron por entorno, entrecomíllala: `APP_ELEVATION_BACKFILL_CRON="0 */15 * * * *"`.

El cron está pensado para **una sola instancia**. Si despliegas varias réplicas, activa el
backfill solo en una o usa un lock distribuido (por ejemplo, ShedLock).

Open-Meteo cuenta una llamada por coordenada. Si el proveedor responde 429, el cliente respeta
`Retry-After` y reintenta con backoff; el cron consumirá como máximo su presupuesto por
ejecución y el resto queda para la siguiente.

#### Cobertura de altitudes

`GET /api/v1/activities/elevation-coverage` devuelve el progreso global del usuario autenticado:

```bash
curl http://localhost:8080/api/v1/activities/elevation-coverage \
  -H "Authorization: Bearer <token>"
```

```json
{
  "totalTrackpoints": 202901,
  "trackpointsWithElevation": 187280,
  "trackpointsMissingElevation": 15621,
  "trackpointsWithFlatElevation": 3200,
  "withElevationPercent": 92.3,
  "missingElevationPercent": 7.7,
  "flatElevationPercent": 1.58
}
```

Una altitud nula o 0 se considera pendiente, igual que en el cron, salvo que haya sido
calculada por el backfill (por ejemplo, un 0 real devuelto por el modelo digital). También
se consideran pendientes los trackpoints que comparten una **altitud constante sospechosa**
(el mismo valor exacto en al menos el 90% de los trackpoints del dispositivo, con un mínimo
de 10), que se informan aparte en `trackpointsWithFlatElevation`/`flatElevationPercent`.
Los porcentajes `withElevationPercent` y `missingElevationPercent` ya excluyen esas altitudes.
Es útil para comprobar cómo avanza el backfill sin tener que consultar la base de datos.

#### API key de Open-Meteo

El plan gratuito no necesita clave. Si contratas un plan de pago, define:

```bash
APP_ELEVATION_API_KEY=tu_clave
```

La aplicación cambia automáticamente de `api.open-meteo.com` a
`customer-api.open-meteo.com` cuando la URL sigue siendo la gratuita por defecto, y envía la
clave como parámetro `apikey`. La clave nunca se escribe en los logs. Con un plan de pago puedes
subir `APP_ELEVATION_MAX_CALLS_PER_MINUTE` y `APP_ELEVATION_BACKFILL_MAX_CALLS_PER_RUN` según tu
contrato; el resto de límites (horario, diario y mensual) sigue aplicando.

### Estadísticas (`/api/v1/stats`)

| Método | Path | Descripción |
|--------|------|-------------|
| `GET` | `/summary` | Resumen acumulado (con `?period=` o `?year=`) |
| `GET` | `/records` | Marcas personales (PB) |
| `POST` | `/recalculate` | Recalcular estadísticas globales y récords desde cero |

### Analítica (`/api/v1/analytics`)

| Método | Path | Descripción |
|--------|------|-------------|
| `GET` | `/{id}/splits` | Splits por kilómetro |
| `GET` | `/{id}/hr-zones` | Zonas de frecuencia cardíaca (referenciadas a la FC máx del atleta, no al pico de la actividad) |
| `GET` | `/{id}/cardiac-drift` | Desacople aeróbico |
| `GET` | `/{id}/training-load` | Carga de entrenamiento |
| `GET` | `/{id}/vo2max-vam` | VO2Max y VAM |
| `GET` | `/pmc` | Histórico PMC (CTL, ATL, TSB) |
| `GET` | `/eddington` | Número de Eddington |
| `GET` | `/acwr/current` | Estado actual ACWR |

### Geoespacial (`/api/v1/geo`)

| Método | Path | Descripción |
|--------|------|-------------|
| `GET` | `/{id}/geojson` | Exportar ruta en GeoJSON |
| `GET` | `/{id}/gpx` | Exportar ruta en GPX 1.1 |

### Entrenamientos (`/api/v1/training`)

| Método | Path | Descripción |
|--------|------|-------------|
| `GET` | `/recommendations/today` | Recomendación de hoy en la zona del atleta; `204` si no existe |
| `GET` | `/recommendations` | Histórico, con filtro opcional `?from=` y `?to=`; sin filtros devuelve las últimas 30 |
| `GET` | `/recommendations/{id}` | Recomendación por ID |
| `GET` | `/recommendations/date/{date}` | Recomendación de una fecha concreta |
| `DELETE` | `/recommendations/{id}` | Eliminar una recomendación y los jobs que la referencian |
| `DELETE` | `/recommendations/date/{date}` | Eliminar la recomendación de una fecha |
| `POST` | `/recommendations/generate` | Encolar la generación (`202`). Body opcional: `date`, `replace` |
| `GET` | `/generation-jobs` | Últimos jobs de generación del usuario |
| `GET` | `/generation-jobs/{requestId}` | Estado de un job (`PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `TIMEOUT`) |
| `DELETE` | `/generation-jobs/{requestId}` | Eliminar un job (`?force=true` para `RUNNING`); no borra la recomendación |
| `DELETE` | `/generation-jobs?status=PENDING` | Eliminar todos los jobs del usuario con ese estado; devuelve `{deleted}` |
| `GET` | `/preferences` | Preferencias (`configured=false` si no hay fila) |
| `PUT` | `/preferences` | Activar generación, zona horaria, hora local y notas |

#### Generar la recomendación de un día

```bash
# Encolar la recomendación de hoy (zona horaria del atleta)
curl -X POST http://localhost:8080/api/v1/training/recommendations/generate \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{}'

# Regenerar un día concreto sobrescribiendo la recomendación existente
curl -X POST http://localhost:8080/api/v1/training/recommendations/generate \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"date":"2026-11-11","replace":true}'

# Consultar el job y, cuando termine, la recomendación resultante
curl http://localhost:8080/api/v1/training/generation-jobs/<requestId> \
  -H "Authorization: Bearer <token>"
curl http://localhost:8080/api/v1/training/recommendations/today \
  -H "Authorization: Bearer <token>"
```

#### Histórico y limpieza

```bash
# Últimas 30 recomendaciones
curl http://localhost:8080/api/v1/training/recommendations \
  -H "Authorization: Bearer <token>"

# Rango de fechas
curl "http://localhost:8080/api/v1/training/recommendations?from=2026-11-01&to=2026-11-30" \
  -H "Authorization: Bearer <token>"

# Recomendación de un día
curl http://localhost:8080/api/v1/training/recommendations/date/2026-11-11 \
  -H "Authorization: Bearer <token>"
```

Si un job se quedó en `PENDING` (n8n no respondió), bórralo para desbloquear ese día y poder volver
a lanzar la generación:

```bash
# Un job concreto
curl -X DELETE http://localhost:8080/api/v1/training/generation-jobs/<requestId> \
  -H "Authorization: Bearer <token>"

# Todos los PENDING del usuario (devuelve {"deleted": n})
curl -X DELETE "http://localhost:8080/api/v1/training/generation-jobs?status=PENDING" \
  -H "Authorization: Bearer <token>"
```

Los jobs `RUNNING` requieren `?force=true`, porque n8n podría estar guardando la recomendación en ese
momento. Borrar un job **no elimina la recomendación** ya persistida; solo quita el registro de
correlación. Si n8n llama después a `save_daily_recommendation` con un `requestId` borrado, recibirá
"job no encontrado".

Para regenerar un día **conservando el histórico de otros días**, usa `replace=true`: la
recomendación de esa fecha se sobrescribe y el resto se mantiene. Para eliminarla por completo:

```bash
curl -i -X DELETE http://localhost:8080/api/v1/training/recommendations/date/2026-11-11 \
  -H "Authorization: Bearer <token>"
```

Flujo interno:

1. `POST /recommendations/generate` crea un `DailyGenerationJob` (`PENDING`) y responde `202`.
2. La API firma un JWT efímero (`aud=mcp`, `training:read training:write`, `requestId`) y llama al
   webhook de n8n con `{requestId, date, replace, mcpUrl}`.
3. El **AI Agent** de n8n llama a `get_training_context` a través del **MCP Client Tool** (usando el
   JWT del job), analiza el estado del atleta y n8n envía la recomendación a
   `save_daily_recommendation`.
4. La API valida la recomendación, la persiste y marca el job `COMPLETED` en la misma transacción.
5. Si n8n no puede terminar, llama a `fail_generation_job`; si nunca responde, el cron de
   reconciliación lo marca `TIMEOUT`.

Reglas destacadas:

- La fecha de la recomendación debe coincidir con la fecha del job.
- Sin `replace=true`, una recomendación ya existente devuelve `409`; con `replace=true` se sobrescribe.
- Los días de descanso se representan con `type=REST` y sin distancia, duración ni ritmo.
- Las sesiones que no son `REST` deben indicar al menos distancia o duración.
- Los reintentos con el mismo `requestId` son idempotentes: devuelven la recomendación ya guardada.

#### Preferencias

```bash
curl -X PUT http://localhost:8080/api/v1/training/preferences \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": true,
    "timezone": "Europe/Madrid",
    "generationHour": 6,
    "notes": "Prefiero entrenar por la mañana"
  }'
```

Sin fila de preferencias **no se genera ninguna recomendación automática**: el cron solo procesa
usuarios con `enabled=true`. La generación manual sigue funcionando aunque
`APP_TRAINING_DAILY_ENABLED=false`, siempre que el webhook de n8n esté configurado.

El cron se ejecuta cada hora y procesa a cada atleta cuando su hora local alcanza
`generationHour`. Así la recomendación se genera por la mañana en la zona horaria del usuario, no a
una hora fija del servidor. Si la API estuvo caída a esa hora, la genera en el siguiente barrido del
día; solo se encola un job por día y usuario.

### Workflow de n8n

El workflow listo para importar está en
[`docs/n8n/daily-workout-recommendation.json`](docs/n8n/daily-workout-recommendation.json). Es
**agnóstico al usuario**: recibe el JWT en `Authorization`, lee `mcpUrl` del body y usa ese mismo
token para las llamadas MCP.

Arquitectura:

- **AI Agent** de n8n con un **OpenAI Chat Model**.
- **MCP Client Tool** conectado al agente, autenticado con el JWT efímero del job. El nodo no admite
  credenciales dinámicas por ejecución, así que el token viaja como query param
  `access_token` **solo en `/mcp`** (la API no lo acepta en rutas REST).
- El agente llama a `get_training_context`, analiza actividades y estado, y n8n envía de forma
  determinista la recomendación a `save_daily_recommendation` con el mismo JWT.

Nodos principales:

1. **Webhook API** — `POST /webhook/training-daily-generate`, responde de inmediato (`onReceived`).
2. **Preparar llamadas MCP** — extrae el token, `requestId`, `date`, `mcpUrl` y construye el
   endpoint del MCP con `access_token`.
3. **AI Agent · recomendación** — recibe la fecha y las instrucciones del entrenador.
4. **MCP Client Tool** — expone `get_training_context` al agente con el JWT del job.
5. **OpenAI Chat Model** — genera la recomendación; `gpt-4.1-mini` por defecto.
6. **Extraer recomendación JSON** — limpia la respuesta del agente y la convierte en
   `recommendationJson`.
7. **MCP · guardar recomendación** — `tools/call` → `save_daily_recommendation` con el JSON como
   string.
8. **Telegram · recomendación** — envía la sesión, el análisis y las notas.
9. **Rama de error** — `fail_generation_job` + aviso por Telegram.

Requisitos tras importar:

| Elemento | Obligatorio | Descripción |
|----------|-------------|-------------|
| Credencial **OpenAI** | ✅ | Selecciónala en el nodo `OpenAI Chat Model`. Puede apuntar a cualquier API compatible con OpenAI |
| `TELEGRAM_CHAT_ID` | ✅ | Chat de destino de los mensajes |
| Credencial **Telegram** | ✅ | Selecciónala en los dos nodos de Telegram |
| `RUNNING_API_MCP_URL` | ❌ | Fallback si el body del webhook no incluye `mcpUrl` |

El modelo se configura en el nodo `OpenAI Chat Model` (`gpt-4.1-mini` por defecto). La URL del
webhook de n8n debe configurarse en `APP_TRAINING_DAILY_N8N_WEBHOOK_URL`, por ejemplo
`https://n8n.example.com/webhook/training-daily-generate`.

> El token efímero viaja en la URL del MCP Client Tool. Es un token de corta duración, con
> `aud=mcp`, scopes de entrenamiento y ligado a un `requestId`; aun así, evita registrar las URLs
> completas en proxies o balanceadores. Si tu versión de n8n permite credenciales dinámicas,
> usa la cabecera `Authorization` en lugar del query param.

> Los cron de generación y reconciliación están pensados para **una sola instancia** de la API.
> Si despliegas varias réplicas, activa la generación solo en una o usa un lock distribuido.
> Las tablas nuevas (`daily_recommendations`, `daily_recommendation_jobs`) se crean con
> `ddl-auto: update`; este proyecto no usa Flyway. Las tablas del antiguo plan semanal
> (`training_plans`, `planned_workouts`, `plan_generation_jobs`) ya no se usan y pueden eliminarse
> manualmente.

### MCP (`/mcp`)

| Método | Path | Descripción |
|--------|------|-------------|
| `POST` | `/mcp` | Endpoint principal MCP (JSON-RPC 2.0). Requiere `Authorization: Bearer <token>` o `?access_token=<token>` |
| `GET` | `/mcp` | Endpoint SSE (requiere cabecera `Mcp-Session-Id` y `Authorization: Bearer <token>`) |

> El servidor MCP se autentica con el **mismo JWT** que la API REST. Las herramientas MCP
> operan sobre las actividades del usuario dueño del token. Desde la versión 1.8.0 incluye
> `get_training_context`, `save_daily_recommendation` y `fail_generation_job`; `tools/list` y
> `tools/call` son *stateless* (no requieren `Mcp-Session-Id`), pensados para ser llamados desde n8n.

---

## Modelos de Datos

### User
Usuario de la aplicación. Es el propietario de las actividades, récords y estadísticas.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | Long | ID autoincremental |
| `email` | String | Email único (identificador de login) |
| `password` | String | Hash BCrypt de la contraseña |
| `role` | String | Rol del usuario (`USER`, `ADMIN`) |
| `createdAt` | LocalDateTime | Fecha de alta |

### Activity
Entidad principal que representa una carrera con sus métricas consolidadas.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | String | ID único (ISO Timestamp o UUID) |
| `userId` | Long | ID del usuario propietario (indexado) |
| `name` | String | Nombre de la carrera |
| `startTime` | LocalDateTime | Fecha/hora de inicio |
| `distanceMeters` | Double | Distancia total (m) |
| `totalTimeSeconds` | Double | Tiempo total (s) |
| `avgSpeed` / `maxSpeed` | Double | Velocidad media/máxima (m/s) |
| `avgHeartRate` / `maxHeartRate` | Integer | FC media/máxima (bpm) |
| `avgCadence` / `maxCadence` | Integer | Cadencia media/máxima (spm) |
| `avgPaceMinPerKm` | Double | Ritmo medio (min/km) |
| `trainingLoad` | Double | Carga de entrenamiento (TRIMP/HRSS) |
| `vo2MaxEstimated` | Double | VO2Max estimado (mL/kg/min) |
| `vamKmH` | Double | VAM estimada (km/h) |
| `totalCalories` | Integer | Calorías totales |
| `elevationGain` / `elevationLoss` | Double | Desnivel +/- (m) |
| `backupFilePath` | String | Ruta del archivo de respaldo |
| `sourceFileType` | String | Tipo de archivo fuente (tcx, gpx, fit) |
| `extraData` | String | Datos adicionales en JSON del formato de origen |
| `trackpoints` | List | Puntos de telemetría (LAZY) |

### ActivityTrackpoint
Punto de telemetría grabado segundo a segundo.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | Long | ID autoincremental |
| `timestamp` | LocalDateTime | Marca temporal |
| `latitude` / `longitude` | Double | Coordenadas GPS |
| `altitudeMeters` | Double | Altitud (m) |
| `distanceMeters` | Double | Distancia acumulada (m) |
| `heartRate` | Integer | FC (bpm) |
| `cadence` | Integer | Cadencia (spm) |
| `speed` | Double | Velocidad (m/s) |

### PersonalRecord
Marcas personales por distancia. Único por usuario y categoría (`user_id`, `distance_category`).

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `userId` | Long | ID del usuario propietario |
| `distanceCategory` | String | Categoría (1k, 5k, 10k, 15k, 21k, 42k) |
| `targetDistanceMeters` | Double | Distancia objetivo (m) |
| `bestTimeSeconds` | Double | Mejor tiempo (s) |
| `activityId` | String | ID de la actividad donde se logró |
| `activityDate` | LocalDateTime | Fecha de la actividad |
| `avgSpeed` | Double | Velocidad media |
| `avgPaceMinPerKm` | Double | Ritmo medio |

### GlobalSummaryStats
Estadísticas globales acumuladas. Una fila por usuario (`user_id` único).

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | Long | ID autoincremental |
| `userId` | Long | ID del usuario propietario (único) |
| `totalActivities` | Long | Total de actividades |
| `totalDistanceMeters` | Double | Distancia total (m) |
| `totalTimeSeconds` | Double | Tiempo total (s) |
| `totalElevationGain` | Double | Desnivel positivo total (m) |
| `totalCalories` | Long | Calorías totales |
| `maxHeartRateGlobal` | Integer | FC máxima histórica |

### DailyRecommendation
Recomendación de entrenamiento para un día concreto, generada por IA y validada por la API. Única
por `user_id` y `recommendation_date`; `request_id` identifica el job que la produjo. Al regenerar
el mismo día se sobrescribe la fila y se conserva el histórico de los días anteriores.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | String | ID único (UUID) |
| `userId` | Long | Propietario |
| `recommendationDate` | LocalDate | Día recomendado |
| `requestId` | String | Job de generación que la produjo |
| `summary` / `analysis` / `coachNotes` | String | Resumen, justificación con datos y consejos |
| `type` | WorkoutType | `EASY`, `RECOVERY`, `LONG_RUN`, `TEMPO`, `INTERVALS`, `HILLS`, `RACE_PACE`, `RACE`, `CROSS_TRAINING`, `REST`, `OTHER` |
| `title` / `description` | String | Título y detalle de la sesión |
| `targetDistanceMeters` / `targetDurationSeconds` | Double | Objetivos de distancia y tiempo |
| `targetPaceMinPerKm` | Double | Ritmo objetivo (min/km) |
| `targetHrZone` | Integer | Zona de FC (1-5) |
| `targetLoad` | Double | Carga objetivo |
| `mainSet` | String | Series, repeticiones y recuperaciones |
| `model` / `promptVersion` | String | Trazabilidad del LLM |
| `generatedAt` | LocalDateTime | Momento de generación |

### TrainingPreferences
Preferencias del atleta. Una fila por usuario (`user_id` único); sin fila no hay generación
automática.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `userId` | Long | Propietario (único) |
| `enabled` | Boolean | Activa el cron diario |
| `timezone` | String | Zona IANA (`Europe/Madrid` por defecto) |
| `generationHour` | Integer | Hora local (0-23) de generación (`6` por defecto) |
| `notes` | String | Restricciones para el LLM |

### DailyGenerationJob
Correlación entre la petición, el JWT efímero y la recomendación guardada. El `id` es el
`requestId`.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | String | `requestId` (UUID) |
| `userId` / `recommendationDate` | | Propietario y día objetivo |
| `status` | TrainingJobStatus | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `TIMEOUT` |
| `source` | TrainingJobSource | `SCHEDULED` o `MANUAL` |
| `attempts` | Integer | Intentos de guardado |
| `recommendationId` | String | Recomendación resultante |
| `lastError` | String | Último error reportado |
| `createdAt` / `startedAt` / `completedAt` | LocalDateTime | Trazabilidad |

---

## Pruebas y Calidad de Código

### Pruebas

La carpeta `src/test/java` contiene pruebas unitarias (JUnit 5 + AssertJ) de las utilidades
compartidas, los parsers GPX/TCX y el mapeador de FIT:

```bash
mvn test
```

Cubren, entre otros: cálculo Haversine, acumulación de desnivel con histéresis, métricas
fisiológicas (TRIMP, VAM, VO2Max), detección de deporte y parseo de XML sintético GPX/TCX.

### Calidad de código (opcional)

El profile **`quality`** agrupa **Spotless** (organización de imports, eliminación de imports
sin usar, espacios finales) y **Checkstyle**:

```bash
# Aplica formateo y orden de imports
mvn -Pquality spotless:apply

# Verifica formato y ejecuta Checkstyle (fase verify)
mvn -Pquality verify
```

> No está activo por defecto para que `mvn compile`/`mvn test` funcionen sin conexión.
> La primera ejecución del profile descarga los plugins desde el repositorio Maven.

---

## Documentación de la API

Una vez iniciada la aplicación, la documentación interactiva de Swagger UI está disponible en:

```
http://localhost:8080/swagger-ui.html
```

Y la especificación OpenAPI en formato JSON:

```
http://localhost:8080/v3/api-docs
```

Swagger UI incluye los esquemas de seguridad **Bearer** y **API Key** (`X-Api-Key`). Para probar endpoints protegidos:
haz login en `/api/v1/auth/login`, copia el `token` y pulsa **Authorize** introduciendo
`Bearer <token>`; o bien crea una clave en `/api/v1/auth/api-keys` e introdúcela en el campo de API Key.

---

## Licencia

Proyecto personal de análisis deportivo.
