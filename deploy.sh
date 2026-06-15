#!/bin/bash
# ==============================================================================
# deploy.sh - Automated deployment script for pybbs
# ==============================================================================
# Usage:
#   ./deploy.sh [dev|test|prod]     Deploy to specified environment
#   ./deploy.sh prod --build        Rebuild Docker image before deploying
#   ./deploy.sh prod --rollback     Rollback to previous version
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_NAME="pybbs"
JAR_NAME="pybbs.jar"
LOG_DIR="${SCRIPT_DIR}/logs"
PID_FILE="${SCRIPT_DIR}/${APP_NAME}.pid"
BACKUP_DIR="${SCRIPT_DIR}/backups"
DEPLOY_ENV="${1:-prod}"
DEPLOY_ACTION="${2:-deploy}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info()  { echo -e "${BLUE}[INFO]${NC}  $(date '+%Y-%m-%d %H:%M:%S') $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $(date '+%Y-%m-%d %H:%M:%S') $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $(date '+%Y-%m-%d %H:%M:%S') $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') $*"; }

# ------------------------------------------------------------------
# Validate environment
# ------------------------------------------------------------------
validate_env() {
    if [[ ! "$DEPLOY_ENV" =~ ^(dev|test|prod)$ ]]; then
        log_error "Invalid environment: $DEPLOY_ENV"
        echo "Usage: $0 [dev|test|prod] [--build|--rollback]"
        exit 1
    fi
    log_info "Target environment: ${DEPLOY_ENV}"
}

# ------------------------------------------------------------------
# Pre-flight checks
# ------------------------------------------------------------------
preflight_checks() {
    log_info "Running pre-flight checks..."

    # Check Java
    if ! command -v java &> /dev/null; then
        log_error "Java is not installed or not in PATH"
        exit 1
    fi
    log_ok "Java found: $(java -version 2>&1 | head -1)"

    # Check if port is available (for non-Docker deploy)
    local port
    case "$DEPLOY_ENV" in
        dev)  port=8080 ;;
        test) port=8081 ;;
        prod) port=8080 ;;
    esac

    if lsof -i :"$port" &> /dev/null; then
        log_warn "Port $port is already in use"
    fi

    # Ensure directories exist
    mkdir -p "$LOG_DIR" "$BACKUP_DIR"
    log_ok "Pre-flight checks passed"
}

# ------------------------------------------------------------------
# Build the application with Maven
# ------------------------------------------------------------------
build_app() {
    log_info "Building application with Maven profile: ${DEPLOY_ENV}..."

    if ! command -v mvn &> /dev/null; then
        log_error "Maven is not installed or not in PATH"
        exit 1
    fi

    mvn clean package -P"${DEPLOY_ENV}" -DskipTests -B
    if [ $? -eq 0 ]; then
        log_ok "Build completed successfully"
    else
        log_error "Build failed"
        exit 1
    fi

    # Verify jar exists
    if [ ! -f "target/${JAR_NAME}" ]; then
        log_error "Jar file not found: target/${JAR_NAME}"
        exit 1
    fi

    cp "target/${JAR_NAME}" "${SCRIPT_DIR}/${JAR_NAME}"
    log_ok "Jar copied to ${SCRIPT_DIR}/${JAR_NAME}"
}

# ------------------------------------------------------------------
# Backup current version
# ------------------------------------------------------------------
backup_current() {
    if [ -f "${SCRIPT_DIR}/${JAR_NAME}" ]; then
        local backup_file="${BACKUP_DIR}/${JAR_NAME}.${TIMESTAMP}"
        cp "${SCRIPT_DIR}/${JAR_NAME}" "$backup_file"
        log_ok "Backup created: $backup_file"

        # Keep only last 5 backups
        ls -t "${BACKUP_DIR}"/${JAR_NAME}.* 2>/dev/null | tail -n +6 | xargs -r rm --
        log_info "Old backups cleaned (keeping last 5)"
    fi
}

# ------------------------------------------------------------------
# Stop running application
# ------------------------------------------------------------------
stop_app() {
    log_info "Stopping ${APP_NAME}..."

    if [ -f "$PID_FILE" ]; then
        local pid
        pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid"
            # Wait for graceful shutdown (max 30 seconds)
            local count=0
            while kill -0 "$pid" 2>/dev/null && [ $count -lt 30 ]; do
                sleep 1
                count=$((count + 1))
            done

            if kill -0 "$pid" 2>/dev/null; then
                log_warn "Graceful shutdown timed out, force killing..."
                kill -9 "$pid"
            fi
            log_ok "Application stopped (PID: $pid)"
        else
            log_warn "Process $pid is not running"
        fi
        rm -f "$PID_FILE"
    else
        # Try to find by process name
        local pid
        pid=$(pgrep -f "${JAR_NAME}" 2>/dev/null || true)
        if [ -n "$pid" ]; then
            kill "$pid"
            sleep 3
            log_ok "Application stopped (PID: $pid)"
        else
            log_info "No running instance found"
        fi
    fi
}

# ------------------------------------------------------------------
# Start application
# ------------------------------------------------------------------
start_app() {
    log_info "Starting ${APP_NAME} with profile: ${DEPLOY_ENV}..."

    local java_opts="-Xms256m -Xmx512m -XX:+UseG1GC"
    local spring_opts="--spring.profiles.active=${DEPLOY_ENV}"

    # Environment-specific JVM options
    if [ "$DEPLOY_ENV" = "prod" ]; then
        java_opts="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
    fi

    nohup java ${java_opts} -jar "${SCRIPT_DIR}/${JAR_NAME}" ${spring_opts} \
        > "${LOG_DIR}/${APP_NAME}.log" 2>&1 &

    local pid=$!
    echo "$pid" > "$PID_FILE"
    log_ok "Application started (PID: $pid)"
    log_info "Log file: ${LOG_DIR}/${APP_NAME}.log"
}

# ------------------------------------------------------------------
# Health check
# ------------------------------------------------------------------
health_check() {
    log_info "Running health check..."

    local port
    case "$DEPLOY_ENV" in
        dev)  port=8080 ;;
        test) port=8081 ;;
        prod) port=8080 ;;
    esac

    local max_retries=30
    local count=0

    while [ $count -lt $max_retries ]; do
        if curl -sf "http://localhost:${port}/" > /dev/null 2>&1; then
            log_ok "Health check passed - application is running on port $port"
            return 0
        fi
        sleep 2
        count=$((count + 1))
    done

    log_error "Health check failed after $((max_retries * 2)) seconds"
    return 1
}

# ------------------------------------------------------------------
# Docker-based deployment
# ------------------------------------------------------------------
deploy_docker() {
    log_info "Deploying with Docker Compose (profile: ${DEPLOY_ENV})..."

    if ! command -v docker-compose &> /dev/null && ! command -v docker &> /dev/null; then
        log_error "Docker is not installed"
        exit 1
    fi

    export SPRING_PROFILES_ACTIVE="${DEPLOY_ENV}"

    if [ "$DEPLOY_ACTION" = "--build" ]; then
        log_info "Rebuilding Docker images..."
        docker-compose build --no-cache
    fi

    docker-compose up -d
    log_ok "Docker services started"

    # Wait for health check
    sleep 10
    if docker-compose ps | grep -q "Up"; then
        log_ok "Docker deployment successful"
    else
        log_error "Docker deployment may have failed. Check: docker-compose logs"
    fi
}

# ------------------------------------------------------------------
# Rollback
# ------------------------------------------------------------------
rollback() {
    log_info "Rolling back to previous version..."

    local latest_backup
    latest_backup=$(ls -t "${BACKUP_DIR}"/${JAR_NAME}.* 2>/dev/null | head -1)

    if [ -z "$latest_backup" ]; then
        log_error "No backup found for rollback"
        exit 1
    fi

    stop_app
    cp "$latest_backup" "${SCRIPT_DIR}/${JAR_NAME}"
    log_ok "Restored from backup: $latest_backup"
    start_app
    health_check
}

# ==============================================================================
# Main
# ==============================================================================
main() {
    echo "============================================"
    echo "  pybbs Deployment - ${DEPLOY_ENV}"
    echo "  $(date '+%Y-%m-%d %H:%M:%S')"
    echo "============================================"

    validate_env

    case "$DEPLOY_ACTION" in
        --rollback)
            rollback
            ;;
        --docker)
            deploy_docker
            ;;
        *)
            preflight_checks
            backup_current
            build_app
            stop_app
            start_app
            health_check
            ;;
    esac

    echo ""
    log_ok "Deployment completed!"
}

main "$@"
