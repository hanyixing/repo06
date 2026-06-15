# ==============================================================================
# Stage 1: Build stage - compile and package the application
# ==============================================================================
FROM maven:3.6.3-jdk-8-slim AS builder

WORKDIR /build

# Copy pom.xml first for better layer caching
COPY pom.xml .

# Download dependencies (this layer is cached unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application, skip tests (tests should run in CI)
RUN mvn clean package -DskipTests -B && \
    mv target/pybbs.jar app.jar

# ==============================================================================
# Stage 2: Runtime stage - minimal image for running the application
# ==============================================================================
FROM openjdk:8-jre-slim

LABEL maintainer="pybbs"
LABEL description="pybbs forum application"

# Install essential tools and set timezone
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl tzdata && \
    ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser -d /app appuser

WORKDIR /app

# Copy jar from build stage
COPY --from=builder /build/app.jar pybbs.jar

# Create directories for external resources
RUN mkdir -p logs static/upload templates && \
    chown -R appuser:appuser /app

USER appuser

# Expose application port
EXPOSE 8080

# JVM options with sensible defaults
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
    CMD curl -f http://localhost:8080/actuator/health || curl -f http://localhost:8080/ || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar pybbs.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"]
