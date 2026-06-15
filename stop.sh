#!/bin/bash
set -euo pipefail

# pybbs 停止脚本（jar 方式）：优先按 PID 文件优雅停止，回退按 jar 名查找
# 可用环境变量覆盖：JAR / PID_FILE / STOP_TIMEOUT
APP_NAME="pybbs"
JAR="${JAR:-pybbs.jar}"
PID_FILE="${PID_FILE:-./pybbs.pid}"
STOP_TIMEOUT="${STOP_TIMEOUT:-30}"

stop_pid() {
  local pid="$1"
  if ! kill -0 "$pid" 2>/dev/null; then
    return 1
  fi
  echo "[stop] 发送 SIGTERM 给 PID $pid，最多等待 ${STOP_TIMEOUT}s ..."
  kill "$pid" 2>/dev/null || true
  for _ in $(seq 1 "$STOP_TIMEOUT"); do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "[stop] 已优雅停止 (PID $pid)"
      return 0
    fi
    sleep 1
  done
  echo "[stop] 超时未退出，强制 kill -9 PID $pid"
  kill -9 "$pid" 2>/dev/null || true
  return 0
}

# 优先使用 PID 文件
if [ -f "$PID_FILE" ]; then
  PID="$(cat "$PID_FILE")"
  if stop_pid "$PID"; then
    rm -f "$PID_FILE"
    exit 0
  fi
  echo "[stop] PID 文件中的进程不存在，改为按进程名查找"
  rm -f "$PID_FILE"
fi

# 回退：按 jar 名匹配（排除自身）
PIDS="$(pgrep -f "$JAR" || true)"
if [ -z "$PIDS" ]; then
  echo "[stop] 未发现运行中的 $APP_NAME"
  exit 0
fi
for pid in $PIDS; do
  stop_pid "$pid" || true
done
echo "[stop] 完成"
