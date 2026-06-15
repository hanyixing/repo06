#!/bin/bash
set -euo pipefail

# pybbs 部署脚本（jar 方式）：构建 -> 备份 -> 停止 -> 替换 -> 启动 -> 健康检查(失败自动回滚)
# 环境变量：SPRING_PROFILES_ACTIVE / SKIP_BUILD / HEALTH_URL / HEALTH_TIMEOUT / JAR / BACKUP_DIR / LOG_DIR
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

PROFILE="${SPRING_PROFILES_ACTIVE:-prod}"
JAR="${JAR:-pybbs.jar}"
BUILT_JAR="target/pybbs.jar"
BACKUP_DIR="${BACKUP_DIR:-./backup}"
LOG_DIR="${LOG_DIR:-./logs}"
HEALTH_URL="${HEALTH_URL:-http://localhost:8080/actuator/health}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-90}"
SKIP_BUILD="${SKIP_BUILD:-false}"

log() { echo "[deploy] $*"; }

# 1. 构建
if [ "$SKIP_BUILD" != "true" ]; then
  log "执行 Maven 打包 (profile=prod, 跳过测试) ..."
  mvn -B clean package -Pprod -DskipTests
fi
if [ ! -f "$BUILT_JAR" ]; then
  log "构建产物不存在：$BUILT_JAR" >&2
  exit 1
fi

# 2. 备份当前 jar
BACKUP_JAR=""
if [ -f "$JAR" ]; then
  mkdir -p "$BACKUP_DIR"
  BACKUP_JAR="$BACKUP_DIR/pybbs-$(date +%Y%m%d%H%M%S).jar"
  cp -f "$JAR" "$BACKUP_JAR"
  log "已备份当前版本到 $BACKUP_JAR"
fi

# 3. 停止旧进程
SPRING_PROFILES_ACTIVE="$PROFILE" "$SCRIPT_DIR/stop.sh" || true

# 4. 替换 jar
cp -f "$BUILT_JAR" "$JAR"
log "已部署新版本 -> $JAR"

# 5. 启动
SPRING_PROFILES_ACTIVE="$PROFILE" "$SCRIPT_DIR/start.sh"

# 6. 健康检查
log "等待健康检查通过：$HEALTH_URL (最多 ${HEALTH_TIMEOUT}s)"
ok="false"
for _ in $(seq 1 "$HEALTH_TIMEOUT"); do
  if curl -fsS "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
    ok="true"
    break
  fi
  sleep 1
done

if [ "$ok" = "true" ]; then
  log "部署成功，应用健康。"
  exit 0
fi

# 7. 健康检查失败 -> 回滚
log "健康检查失败！打印最近日志并尝试回滚。" >&2
tail -n 50 "$LOG_DIR/app.out" 2>/dev/null || true
"$SCRIPT_DIR/stop.sh" || true
if [ -n "$BACKUP_JAR" ] && [ -f "$BACKUP_JAR" ]; then
  log "回滚到上一个版本：$BACKUP_JAR" >&2
  cp -f "$BACKUP_JAR" "$JAR"
  SPRING_PROFILES_ACTIVE="$PROFILE" "$SCRIPT_DIR/start.sh" || true
fi
exit 1
