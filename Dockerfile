# ==========================================
# Stage 1: Build & Layer Extraction
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Cache Maven wrapper and dependencies
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B || true

# Copy source code and build production jar
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# Extract layered components for optimal Docker cache invalidation
RUN java -Djarmode=tools -jar target/gatekeeper-*.jar extract --layers --launcher --destination /build/extracted

# ==========================================
# Stage 2: Minimal Production Runtime
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runner

# Install curl for container health checks
RUN apk add --no-cache curl

# Create non-root system user and group for security
RUN addgroup -S gatekeeper && adduser -S gatekeeper -G gatekeeper

WORKDIR /app

# Copy extracted layers in order of least to most frequently changed
COPY --from=builder --chown=gatekeeper:gatekeeper /build/extracted/dependencies/ ./
COPY --from=builder --chown=gatekeeper:gatekeeper /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=gatekeeper:gatekeeper /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=gatekeeper:gatekeeper /build/extracted/application/ ./

USER gatekeeper

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
