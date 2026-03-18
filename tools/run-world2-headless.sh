#!/usr/bin/env bash
set -euo pipefail

SCENARIO="1"
STEPS="1000"
DT="0.2"
SERVER_PORT="18082"
UI_BASE="http://127.0.0.1:8000/headless.html"
OPEN_BROWSER="0"
START_SERVER="1"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Starts the World2 JVM server (optional) and prints/opens a headless UI URL.

Options:
  -s, --scenario ID       Scenario id (default: 1)
  -n, --steps N           Number of steps (default: 1000)
  -d, --dt VALUE          Timestep dt (default: 0.2)
  -p, --port PORT         World2 server port (default: 18082)
  -u, --ui-base URL       Base UI URL (default: http://127.0.0.1:8000/headless.html)
      --open              Open URL in browser (uses xdg-open)
      --no-start-server   Do not start server; assume it is already running
  -h, --help              Show this help

Examples:
  $(basename "$0") --scenario 6.2 --open
  $(basename "$0") -s 10 -n 1500 -d 0.1 --no-start-server
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -s|--scenario)
      SCENARIO="$2"
      shift 2
      ;;
    -n|--steps)
      STEPS="$2"
      shift 2
      ;;
    -d|--dt)
      DT="$2"
      shift 2
      ;;
    -p|--port)
      SERVER_PORT="$2"
      shift 2
      ;;
    -u|--ui-base)
      UI_BASE="$2"
      shift 2
      ;;
    --open)
      OPEN_BROWSER="1"
      shift
      ;;
    --no-start-server)
      START_SERVER="0"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_URL="http://127.0.0.1:${SERVER_PORT}"
PID_FILE="${REPO_ROOT}/build/world2-server-${SERVER_PORT}.pid"

server_is_ready() {
  curl -fsS --max-time 2 "${SERVER_URL}/health" >/dev/null 2>&1
}

if [[ "$START_SERVER" == "1" ]]; then
  if server_is_ready; then
    echo "World2 server already running on port ${SERVER_PORT}; reusing existing process."
    if [[ -f "${PID_FILE}" ]]; then
      echo "PID file: ${PID_FILE} ($(cat "${PID_FILE}"))"
    else
      echo "PID file: ${PID_FILE} (not set; reused external/already-running server)"
    fi
  else
    LOG_FILE="${REPO_ROOT}/build/world2-server-${SERVER_PORT}.log"
    mkdir -p "${REPO_ROOT}/build"

    echo "Starting World2 server on port ${SERVER_PORT} ..."
    (
      cd "${REPO_ROOT}"
      ./gradlew :world2-server:run --args="${SERVER_PORT}" >"${LOG_FILE}" 2>&1
    ) &
    SERVER_PID=$!
    echo "${SERVER_PID}" >"${PID_FILE}"

    echo "Server PID: ${SERVER_PID}"
    echo "PID file: ${PID_FILE}"
    echo "Server log: ${LOG_FILE}"

    for _ in $(seq 1 60); do
      if server_is_ready; then
        break
      fi
      sleep 0.5
    done

    if ! server_is_ready; then
      echo "World2 server did not become ready in time. Check: ${LOG_FILE}" >&2
      exit 1
    fi
  fi
fi

HEADLESS_URL="${UI_BASE}?world2Scenario=${SCENARIO}&steps=${STEPS}&dt=${DT}&world2Api=${SERVER_URL}&v=$(date +%s)"

echo
printf 'Headless URL:\n%s\n' "$HEADLESS_URL"

echo
echo "Quick API check:"
curl -fsS "${SERVER_URL}/health" || true
echo

if [[ "$OPEN_BROWSER" == "1" ]]; then
  if command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$HEADLESS_URL" >/dev/null 2>&1 || true
    echo "Opened in browser."
  else
    echo "xdg-open not found; copy URL manually."
  fi
fi
