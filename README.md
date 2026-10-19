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
- **ACWR** — Acute:Chronic Workload Ratio con evaluación de riesgo de lesión

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
│   └── StatsController.java     # Estadísticas y récords
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
│   └── WeekdayStatsDto.java
├── exception/
│   ├── DuplicateEmailException.java
│   ├── GlobalExceptionHandler.java
│   ├── InvalidCredentialsException.java
│   └── ResourceNotFoundException.java
├── mapper/
│   └── ActivityMapper.java      # Mapeo FitActivityDTO -> Activity
├── mcp/
│   ├── McpServerController.java # Servidor MCP (JSON-RPC)
│   └── tools/
│       ├── ActivityTools.java
│       ├── AnalyticsTools.java
│       └── StatsTools.java
├── model/
│   ├── Activity.java            # Entidad principal de carrera
│   ├── ActivityTrackpoint.java  # Puntos de telemetría segundo a segundo
│   ├── ApiKey.java              # Clave de API (hash SHA-256, caducidad y último uso)
│   ├── GlobalSummaryStats.java  # Estadísticas globales acumuladas (por usuario)
│   ├── PersonalRecord.java      # Récords personales por distancia (por usuario)
│   └── User.java                # Usuario (email + hash de contraseña + rol)
├── repository/
│   ├── ActivityRepository.java
│   ├── ActivityTrackpointRepository.java
│   ├── ApiKeyRepository.java
│   ├── GlobalSummaryStatsRepository.java
│   ├── PersonalRecordRepository.java
│   ├── UserRepository.java
│   └── projection/
│       └── DailyTrimpProjection.java
├── security/
│   ├── ApiKeyAuthenticationFilter.java # Valida la cabecera X-Api-Key
│   ├── CurrentUserService.java       # Resuelve el userId del SecurityContext
│   ├── JwtAuthenticationFilter.java  # Valida el Bearer token en cada petición
│   ├── JwtService.java               # Emisión/validación de tokens JWT
│   ├── SecurityConfig.java           # Cadena de filtros, rutas públicas y 401/403 JSON
│   └── UserPrincipal.java            # Principal autenticado (id, email, rol)
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
│   └── TcxParserService.java
├── util/
│   ├── ElevationAccumulator.java # Desnivel D+/D- con histéresis (filtro de ruido)
│   ├── GeoUtils.java             # Distancia Haversine
│   └── RunningMetrics.java       # TRIMP / VAM / VO2Max / detección de deporte
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
| `APP_JWT_SECRET` | *(clave de desarrollo)* | Clave de firma de los JWT (HS256). **Obligatorio cambiarla en producción** |
| `APP_JWT_EXPIRATION_MINUTES` | `1440` | Vigencia del token de acceso en minutos (24 h) |
| `APP_JWT_REFRESH_EXPIRATION_MINUTES` | `10080` | Vigencia del refresh token en minutos (7 días) |
| `APP_ADMIN_EMAIL` | `admin@running.local` | Email del usuario administrador sembrado al arrancar |
| `APP_ADMIN_PASSWORD` | `Admin1234!` | Contraseña inicial del administrador (solo se aplica al crearlo) |

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

Un refresh token **no** sirve como token de acceso ni viceversa. El refresh no requiere
cabecera `Authorization`: se autentica con el propio refresh token.

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
| `DELETE` | `/` | Vaciar toda la base de datos |

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
| `GET` | `/{id}/hr-zones` | Zonas de frecuencia cardíaca |
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

### MCP (`/mcp`)

| Método | Path | Descripción |
|--------|------|-------------|
| `POST` | `/mcp` | Endpoint principal MCP (JSON-RPC 2.0). Requiere `Authorization: Bearer <token>` |
| `GET` | `/mcp` | Endpoint SSE (requiere cabecera `Mcp-Session-Id` y `Authorization: Bearer <token>`) |

> El servidor MCP se autentica con el **mismo JWT** que la API REST. Las herramientas MCP
> operan sobre las actividades del usuario dueño del token.

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
