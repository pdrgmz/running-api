# Monitoreo - Running API

Stack de monitoreo self-hosted estilo New Relic para la Running API.

## Stack

| Componente | Puerto | Descripción |
|------------|--------|-------------|
| **Prometheus** | 9090 | Recolección y almacenamiento de métricas |
| **Grafana** | 3001 | Visualización y dashboards |
| **Running API** | 8080 | Aplicación con actuator + micrometer |

## Inicio Rápido

```bash
# Levantar Prometheus y Grafana
docker-compose -f docker-compose.monitoring.yml up -d

# Verificar que estén corriendo
docker-compose -f docker-compose.monitoring.yml ps
```

## Acceso

| Servicio | URL | Credenciales |
|----------|-----|--------------|
| Grafana | http://localhost:3001 | admin / admin |
| Prometheus | http://localhost:9090 | - |
| API Metrics | http://localhost:8080/actuator/prometheus | - |
| API Health | http://localhost:8080/actuator/health | - |

## Métricas Disponibles

### Uploads
- `running_api_uploads_total` - Total de uploads por tipo (tcx, gpx, fit) y estado (success, failure)

### Parseo
- `running_api_parse_time` - Tiempo de parseo por tipo de archivo
- `running_api_parse_errors_total` - Errores de parseo por tipo y error

### Actividades
- `running_api_activities_total` - Total de actividades creadas por fuente
- `running_api_activities_deleted_total` - Total de actividades eliminadas

### API
- `running_api_requests` - Duración de llamadas API por endpoint, método y status

### Storage
- `running_api_storage_total` - Operaciones de almacenamiento por tipo y estado

### JVM (automáticas)
- `jvm_memory_used_bytes` - Uso de memoria heap/non-heap
- `jvm_threads_live_threads` - Threads activos
- `jvm_gc_pause_seconds` - Pausas GC

## Endpoints de Actuator

| Endpoint | Descripción |
|----------|-------------|
| `/actuator/health` | Estado de la aplicación |
| `/actuator/metrics` | Lista de métricas disponibles |
| `/actuator/prometheus` | Métricas en formato Prometheus |
| `/actuator/info` | Información de la aplicación |

## Comandos Útiles

```bash
# Ver métricas en formato Prometheus
curl http://localhost:8080/actuator/prometheus

# Ver estado de salud
curl http://localhost:8080/actuator/health

# Recargar configuración de Prometheus
curl -X POST http://localhost:9090/-/reload

# Detener monitoreo
docker-compose -f docker-compose.monitoring.yml down

# Ver logs de Prometheus
docker logs running-api-prometheus

# Ver logs de Grafana
docker logs running-api-grafana
```

## Estructura de Archivos

```
monitoring/
├── prometheus/
│   └── prometheus.yml          # Configuración de Prometheus
├── grafana/
│   ├── datasources/
│   │   └── prometheus.yml      # Datasource de Grafana
│   └── dashboards/
│       └── running-api.json    # Dashboard principal
└── README.md                   # Este archivo
```

## Notas

- Las métricas se almacenan en Prometheus por 30 días
- El dashboard de Grafana se carga automáticamente al iniciar
- Para producción, cambiar las credenciales de Grafana en `docker-compose.monitoring.yml`
- El endpoint de Prometheus usa `host.docker.internal` para acceder a la API desde Docker
