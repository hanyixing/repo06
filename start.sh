#!/bin/bash
set -euo pipefail

# pybbs 启动脚本（jar 方式）
# 可用环境变量覆盖：JAR / SPRING_PROFILES_ACTIVE / JAVA_OPTS / LOG_DIR / PID_FILE
APP_NAME="pybbs"
JAR="${JAR:-pybbs.jar}"
PROFILE="${SPRING_PROFILES_ACTIVE:-prod}"
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx512m}"
LOG_DIR="${LOG_DIR:-./logs}"
PID_FILE="${PID_FILE:-./pybbs.pid}"

if [ ! -f "$JAR" ]; then
  echo "[start] 找不到 $JAR，请先执行 mvn clean package 或设置 JAR 环境变量" >&2
  exit 1
fi

# 已在运行则不重复启动
if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "[start] $APP_NAME 已在运行 (PID $(cat "$PID_FILE"))"
  exit 0
fi

mkdir -p "$LOG_DIR"
echo "[start] 启动 $APP_NAME，profile=$PROFILE"
# shellcheck disable=SC2086  # JAVA_OPTS 需要按空格拆分为多个参数
nohup java $JAVA_OPTS -jar "$JAR" --spring.profiles.active="$PROFILE" > "$LOG_DIR/app.out" 2>&1 &
echo $! > "$PID_FILE"
echo "[start] 已启动，PID $(cat "$PID_FILE")，日志：$LOG_DIR/app.out"
