#!/bin/bash
# ==============================================================================
# stop.sh - Stop pybbs application
# ==============================================================================
# Usage:
#   ./stop.sh            Stop the application gracefully
#   ./stop.sh --force    Force kill the application
#   ./stop.sh --docker   Stop Docker containers
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_NAME="pybbs"
JAR_NAME="pybbs.jar"
PID_FILE="${SCRIPT_DIR}/${APP_NAME}.pid"
STOP_MODE="${1:-graceful}"

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
# Stop Docker containers
# ------------------------------------------------------------------
stop_docker() {
    log_info "Stopping Docker containers..."
    if command -v docker-compose &> /dev/null; then
        docker-compose down
        log_ok "Docker containers stopped"
    else
        log_error "docker-compose is not installed"
        exit 1
    fi
}

# ------------------------------------------------------------------
# Stop JVM process
# ------------------------------------------------------------------
stop_jvm() {
    local pid=""

    # Try PID file first
    if [ -f "$PID_FILE" ]; then
        pid=$(cat "$PID_FILE")
    fi

    # Fallback: find by process name
    if [ -z "$pid" ] || ! kill -0 "$pid" 2>/dev/null; then
        pid=$(pgrep -f "${JAR_NAME}" 2>/dev/null || true)
    fi

    if [ -z "$pid" ]; then
        log_info "${APP_NAME} is not running"
        rm -f "$PID_FILE"
        return 0
    fi

    if [ "$STOP_MODE" = "--force" ]; then
        log_info "Force killing ${APP_NAME} (PID: $pid)..."
        kill -9 "$pid" 2>/dev/null || true
        rm -f "$PID_FILE"
        log_ok "${APP_NAME} force killed"
        return 0
    fi

    # Graceful shutdown
    log_info "Stopping ${APP_NAME} (PID: $pid)..."
    kill "$pid"

    # Wait for graceful shutdown (max 30 seconds)
    local count=0
    while kill -0 "$pid" 2>/dev/null && [ $count -lt 30 ]; do
        sleep 1
        count=$((count + 1))
        if [ $((count % 5)) -eq 0 ]; then
            log_info "Waiting for shutdown... ($count/30s)"
        fi
    done

    if kill -0 "$pid" 2>/dev/null; then
        log_warn "Graceful shutdown timed out, force killing..."
        kill -9 "$pid" 2>/dev/null || true
    fi

    rm -f "$PID_FILE"
    log_ok "${APP_NAME} stopped"
}

# ==============================================================================
# Main
# ==============================================================================
case "$STOP_MODE" in
    --docker)
        stop_docker
        ;;
    --force|--graceful)
        stop_jvm
        ;;
    *)
        log_error "Unknown option: $STOP_MODE"
        echo "Usage: $0 [--force|--docker]"
        exit 1
        ;;
esac
