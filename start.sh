#!/bin/bash
# ==============================================================================
# start.sh - Start pybbs application
# ==============================================================================
# Usage:
#   ./start.sh [dev|test|prod|docker]   Start with specified profile (default: prod)
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_NAME="pybbs"
JAR_NAME="pybbs.jar"
LOG_DIR="${SCRIPT_DIR}/logs"
PID_FILE="${SCRIPT_DIR}/${APP_NAME}.pid"
PROFILE="${1:-prod}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${BLUE}[INFO]${NC}  $(date '+%Y-%m-%d %H:%M:%S') $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $(date '+%Y-%m-%d %H:%M:%S') $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $(date '+%Y-%m-%d %H:%M:%S') $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') $*"; }

# ------------------------------------------------------------------
# Check if already running
# ------------------------------------------------------------------
check_running() {
    if [ -f "$PID_FILE" ]; then
        local pid
        pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            log_warn "${APP_NAME} is already running (PID: $pid)"
            exit 1
        else
            log_warn "Stale PID file found, cleaning up..."
            rm -f "$PID_FILE"
        fi
    fi

    # Also check by process name
    local existing_pid
    existing_pid=$(pgrep -f "${JAR_NAME}" 2>/dev/null || true)
    if [ -n "$existing_pid" ]; then
        log_warn "${APP_NAME} is already running (PID: $existing_pid)"
        exit 1
    fi
}

# ------------------------------------------------------------------
# Docker mode
# ------------------------------------------------------------------
start_docker() {
    log_info "Starting ${APP_NAME} with Docker Compose..."
    if ! command -v docker-compose &> /dev/null; then
        log_error "docker-compose is not installed"
        exit 1
    fi
    docker-compose up -d
    log_ok "Docker services started"
    docker-compose ps
}

# ------------------------------------------------------------------
# JVM mode
# ------------------------------------------------------------------
start_jvm() {
    # Verify jar exists
    if [ ! -f "${SCRIPT_DIR}/${JAR_NAME}" ]; then
        # Try target directory
        if [ -f "${SCRIPT_DIR}/target/${JAR_NAME}" ]; then
            cp "${SCRIPT_DIR}/target/${JAR_NAME}" "${SCRIPT_DIR}/${JAR_NAME}"
            log_info "Copied jar from target/"
        else
            log_error "${JAR_NAME} not found. Run 'mvn package' or './deploy.sh ${PROFILE}' first."
            exit 1
        fi
    fi

    # Verify Java
    if ! command -v java &> /dev/null; then
        log_error "Java is not installed or not in PATH"
        exit 1
    fi

    mkdir -p "$LOG_DIR"

    # Set JVM options based on environment
    local java_opts
    case "$PROFILE" in
        dev)
            java_opts="-Xms128m -Xmx256m -XX:+UseG1GC -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
            ;;
        test)
            java_opts="-Xms256m -Xmx512m -XX:+UseG1GC"
            ;;
        prod)
            java_opts="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${LOG_DIR}"
            ;;
    esac

    log_info "Starting ${APP_NAME} with profile: ${PROFILE}"
    log_info "JVM options: ${java_opts}"

    nohup java ${java_opts} \
        -jar "${SCRIPT_DIR}/${JAR_NAME}" \
        --spring.profiles.active="${PROFILE}" \
        > "${LOG_DIR}/${APP_NAME}.log" 2>&1 &

    local pid=$!
    echo "$pid" > "$PID_FILE"
    log_ok "${APP_NAME} started (PID: $pid)"
    log_info "Log file: ${LOG_DIR}/${APP_NAME}.log"
    log_info "Profile:  ${PROFILE}"

    # Quick health check
    local port=8080
    [ "$PROFILE" = "test" ] && port=8081

    log_info "Waiting for application to start..."
    local count=0
    while [ $count -lt 30 ]; do
        if curl -sf "http://localhost:${port}/" > /dev/null 2>&1; then
            log_ok "Application is ready on port $port"
            return 0
        fi
        sleep 2
        count=$((count + 1))
    done

    log_warn "Application may not be fully started yet. Check logs: tail -f ${LOG_DIR}/${APP_NAME}.log"
}

# ==============================================================================
# Main
# ==============================================================================
if [ "$PROFILE" = "docker" ]; then
    start_docker
else
    check_running
    start_jvm
fi
