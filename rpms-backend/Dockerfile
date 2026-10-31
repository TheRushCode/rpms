# ==============================================================================
# Stage 1: Build stage using official Eclipse Temurin JDK 21
# ==============================================================================
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Copy Maven wrapper and POM first to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Ensure maven wrapper has executable permissions
RUN chmod +x ./mvnw

# Pre-fetch project dependencies offline
RUN ./mvnw dependency:go-offline -B

# Copy application source code
COPY src ./src

# Build production JAR (tests skipped during image build, verified in CI/local)
RUN ./mvnw clean package -DskipTests

# ==============================================================================
# Stage 2: Minimal, secure runtime stage using Eclipse Temurin JRE 21
# ==============================================================================
FROM eclipse-temurin:21-jre-jammy

# Create non-root system group and user for secure container operation
RUN addgroup --system --gid 1001 rpmsgroup && \
    adduser --system --uid 1001 --ingroup rpmsgroup rpmsuser

WORKDIR /app

# Copy compiled JAR from builder stage and assign ownership to non-root user
COPY --from=builder --chown=rpmsuser:rpmsgroup /app/target/*.jar app.jar

# Switch to non-root user
USER rpmsuser:rpmsgroup

# Document standard port (Render will dynamically supply $PORT at runtime)
EXPOSE 8080

# Configure JVM flags optimized for containerized environments
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Launch Spring Boot application; sh -c ensures environment variables expand
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
