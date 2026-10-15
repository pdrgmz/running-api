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
│   ├── AsyncConfig.java         # Executors @Async (taskExecutor, zipExecutor)
│   ├── CorsConfig.java          # Configuración CORS
│   └── OpenApiConfig.java       # Configuración OpenAPI/Swagger
├── controller/
│   ├── ActivityController.java  # CRUD de actividades
│   ├── AnalyticsController.java # Métricas avanzadas
│   ├── FilesController.java     # Importación/exportación TCX/GPX/FIT
│   ├── GeoController.java       # Exportación GeoJSON/GPX
│   └── StatsController.java     # Estadísticas y récords
├── dto/
│   ├── AcwrStatusDto.java
│   ├── BulkUploadStatusDto.java
│   ├── CardiacDriftResponseDto.java
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
│   ├── PmcPointDto.java
│   ├── RacePredictionsDto.java
│   ├── RecordResponseDto.java
│   ├── SplitDto.java
│   ├── StreakStatsDto.java
│   ├── SummaryStatsDto.java
│   ├── TrainingLoadResponseDto.java
│   ├── Vo2MaxVamResponseDto.java
│   └── WeekdayStatsDto.java
├── exception/
│   ├── GlobalExceptionHandler.java
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
│   ├── GlobalSummaryStats.java  # Estadísticas globales acumuladas
│   └── PersonalRecord.java      # Récords personales por distancia
├── repository/
│   ├── ActivityRepository.java
│   ├── ActivityTrackpointRepository.java
│   ├── GlobalSummaryStatsRepository.java
│   ├── PersonalRecordRepository.java
│   └── projection/
│       └── DailyTrimpProjection.java
├── service/
│   ├── ActivityService.java
│   ├── AnalyticsService.java
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

### Archivo `application.yml`

```yaml
server:
  port: 8080
  forward-headers-strategy: framework

spring:
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

## Endpoints

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
| `POST` | `/mcp` | Endpoint principal MCP (JSON-RPC 2.0) |
| `GET` | `/mcp` | Endpoint SSE (requiere cabecera `Mcp-Session-Id`) |

---

## Modelos de Datos

### Activity
Entidad principal que representa una carrera con sus métricas consolidadas.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | String | ID único (ISO Timestamp o UUID) |
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
Marcas personales por distancia.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `distanceCategory` | String | Categoría (1k, 5k, 10k, 15k, 21k, 42k) |
| `targetDistanceMeters` | Double | Distancia objetivo (m) |
| `bestTimeSeconds` | Double | Mejor tiempo (s) |
| `activityId` | String | ID de la actividad donde se logró |
| `activityDate` | LocalDateTime | Fecha de la actividad |
| `avgSpeed` | Double | Velocidad media |
| `avgPaceMinPerKm` | Double | Ritmo medio |

### GlobalSummaryStats
Estadísticas globales acumuladas (fila única con `id=1`).

| Campo | Tipo | Descripción |
|-------|------|-------------|
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

---

## Licencia

Proyecto personal de análisis deportivo.
