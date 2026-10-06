# Multi-stage build para compilar y empaquetar en una imagen liviana

# --- Etapa 1: Build con Maven y Java 21 ---
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copiar configuración de Maven y descargar dependencias (para aprovechar caché)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar el código fuente y compilar el JAR omitiendo tests
COPY src ./src
RUN mvn package -DskipTests

# --- Etapa 2: Imagen de Ejecución (JRE 21) ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Crear directorios para persistencia de SQLite y Storage dentro del contenedor
RUN mkdir -p /app/data /app/storage

# Copiar el JAR generado desde la etapa de construcción
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# Variables por defecto para SQLite y almacenamiento
ENV SPRING_DATASOURCE_URL=jdbc:sqlite:/app/data/activities.db?busy_timeout=30000&journal_mode=WAL

ENTRYPOINT ["java", "-jar", "app.jar"]