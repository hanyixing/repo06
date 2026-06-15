# ---------- 构建阶段 ----------
FROM maven:3.6.3-jdk-8 AS builder
WORKDIR /build

# 先只拷贝 pom.xml 预热依赖，利用 Docker 层缓存，源码改动时不必重新下载依赖
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 再拷贝源码并打包（生产 profile，跳过测试，加快镜像构建）
COPY src ./src
RUN mvn -B clean package -Pprod -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:8-jre-alpine
LABEL maintainer="pybbs"

ENV LANG=C.UTF-8 \
    TZ=Asia/Shanghai \
    # JVM 自动读取 JAVA_TOOL_OPTIONS，运行时可在 compose 中覆盖
    JAVA_TOOL_OPTIONS="-Xms256m -Xmx512m"

# curl 用于容器健康检查；tzdata 保证时间正确
RUN apk add --no-cache curl tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app
# 仅拷贝构建产物，运行镜像不含 Maven / 源码 / JDK，大幅减小体积
COPY --from=builder /build/target/pybbs.jar /app/app.jar

EXPOSE 8080

# 探活：访问 actuator 健康端点（DataSourceHealthIndicator 会反映 MySQL 连接状态）
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# exec 形式启动，保证收到 SIGTERM 时 Spring 能优雅停机；CMD 提供默认 profile，可被覆盖
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
CMD ["--spring.profiles.active=docker"]
