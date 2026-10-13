# Brasil SaaS ERP — multi-stage
# Frontend: Vite (outDir ../dist relativo ao package = /dist no builder)
# Backend: Maven package → JAR

# ---------- Frontend ----------
FROM node:22-alpine AS frontend-builder
WORKDIR /app

# package.json + lock na raiz do contexto de build do frontend
COPY src/main/resources/static/react/package.json src/main/resources/static/react/package-lock.json ./

# Precisa de devDependencies (vite, plugin-react) para o build
RUN npm ci

COPY src/main/resources/static/react/ ./

# vite.config.js: outDir: '../dist' → sobe um nível → /dist
RUN npm run build && ls -la /dist && ls -la /dist/assets | head

# ---------- Backend ----------
FROM eclipse-temurin:25-jdk-jammy AS backend-builder
WORKDIR /app

COPY pom.xml ./
# cache de deps Maven
RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/* \
    && mvn -B -q dependency:go-offline || true

COPY src ./src

# Injeta o dist do frontend no classpath estático antes do package
COPY --from=frontend-builder /dist ./src/main/resources/static/dist

RUN mvn -B clean package -DskipTests \
    && ls -la target/*.jar

# ---------- Runtime ----------
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

RUN useradd -r -m -U -d /home/brasil-saas -s /bin/false brasilsaas \
    && mkdir -p /app/uploads /app/logs \
    && chown -R brasilsaas:brasilsaas /app

# JAR Spring Boot (fat jar)
COPY --from=backend-builder /app/target/*.jar /app/app.jar

# Healthcheck precisa de curl
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && chown brasilsaas:brasilsaas /app/app.jar

USER brasilsaas
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
