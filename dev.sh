#!/bin/bash
set -o errexit -o nounset # bash script safety

# For GWT download URLs see https://www.gwtproject.org/versions.html
# Note: Google Storage URLs for older GWT versions (2.8.x, 2.9.x) return 403 Forbidden
# Use GitHub releases for newer versions which are more reliably available
GWT_VERSION="2.13.0"
GWT_URL="https://github.com/gwtproject/gwt/releases/download/2.13.0/gwt-2.13.0.zip"
#GWT_URL="https://storage.googleapis.com/gwt-releases/gwt-2.9.0.zip"  # 403 Forbidden
#GWT_URL="https://goo.gl/pZZPXS" # 2.8.2 - 403 Forbidden
#GWT_URL="https://goo.gl/TysXZl" # 2.8.1 (does not run)

SCRIPT_DIR="$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")"
SDK_DIR="$SCRIPT_DIR/.."
GWT_DIR="$SDK_DIR/gwt-$GWT_VERSION"
GWT_PROD_MODULE="com.lushprojects.circuitjs1.circuitjs1"
GWT_DEV_MODULE="com.lushprojects.circuitjs1.circuitjs1dev"

WEB_PORT=${WEB_PORT:-8000}
WEB_BINDADDRESS=${WEB_BINDADDRESS:-127.0.0.1}
CODESERVER_BINDADDRESS=${CODESERVER_BINDADDRESS:-127.0.0.1}
CODESERVER_PORT=${CODESERVER_PORT:-9876}

ensure_gwt_sdk() {
    if [[ -d "$GWT_DIR" ]]; then
        return
    fi

    echo "GWT SDK not found at $GWT_DIR"
    echo "Downloading GWT $GWT_VERSION from $GWT_URL"

    mkdir -p "$SDK_DIR"
    (
        cd "$SDK_DIR"
        wget "$GWT_URL" -O "gwt-$GWT_VERSION.zip"
        unzip "gwt-$GWT_VERSION.zip"
        rm "gwt-$GWT_VERSION.zip"
    )
}

compile() {
    ant build
}

package() {
    compile
    (
        cd "$SCRIPT_DIR/war"
        tar czf "$SCRIPT_DIR/circuitjs1.tar.gz" .
    )
}

setup() {
    # Install Java if no java compiler is present
    if ! which javac > /dev/null 2>&1 ||  ! which ant > /dev/null 2>&1; then
        echo "Installing packages may need your sudo password."
        set -x
        sudo apt-get update
        sudo apt-get install -y openjdk-11-jdk-headless ant
        set +x
    fi

    ensure_gwt_sdk

    if [[ -e build.xml ]]; then
        mv build.xml build.xml.backup
    fi
    chmod +x "$GWT_DIR/webAppCreator"
    "$GWT_DIR/webAppCreator" -out ../tempProject "$GWT_PROD_MODULE"
    cp ../tempProject/build.xml ./
    sed -i 's/source="1.7"/source="1.8"/g' build.xml
    sed -i 's/target="1.7"/target="1.8"/g' build.xml
    rm -rf ../tempProject
}

codeserver() {
    ensure_gwt_sdk
    if ! [[ -f "$GWT_DIR/gwt-codeserver.jar" && -f "$GWT_DIR/gwt-dev.jar" && -f "$GWT_DIR/gwt-user.jar" ]]; then
        echo "Missing required GWT jars in $GWT_DIR"
        echo "Expected: gwt-codeserver.jar, gwt-dev.jar, gwt-user.jar"
        return 1
    fi

    mkdir -p war
    java -classpath "src:$GWT_DIR/gwt-codeserver.jar:$GWT_DIR/gwt-dev.jar:$GWT_DIR/gwt-user.jar" \
        com.google.gwt.dev.codeserver.CodeServer \
        -launcherDir war \
	-bindAddress ${CODESERVER_BINDADDRESS} \
	-port ${CODESERVER_PORT} \
        "$GWT_DEV_MODULE"
}

webserver() {
    webroot="$SCRIPT_DIR/war"

    (
        cd $webroot
        # Use PHP server if available (supports shortrelay.php), otherwise fall back to Python
        if command -v php > /dev/null 2>&1; then
            php -S ${WEB_BINDADDRESS}:${WEB_PORT}
        else
            echo "Warning: PHP not installed. Short URL feature will not work."
            python3 -m http.server --bind ${WEB_BINDADDRESS} ${WEB_PORT}
        fi
    )
}

stop() {
    echo "Stopping code server and web server (if running)..."

    # Stop GWT CodeServer instances for this module
    pkill -f "com.google.gwt.dev.codeserver.CodeServer.*$GWT_DEV_MODULE" 2>/dev/null || true

    # Stop web servers started by this script (matching configured bind/port)
    pkill -f "php -S ${WEB_BINDADDRESS}:${WEB_PORT}" 2>/dev/null || true
    pkill -f "python3 -m http.server --bind ${WEB_BINDADDRESS} ${WEB_PORT}" 2>/dev/null || true

    echo "Stop signal sent."
}

restart() {
    stop
    start
}

start() {
    echo "Starting web server http://${WEB_BINDADDRESS}:${WEB_PORT}"
    echo "Starting code server http://${CODESERVER_BINDADDRESS}:${CODESERVER_PORT}"
    trap "stop" EXIT
    webserver >"webserver.log" 2>&1 &
    sleep 0.5
    codeserver | tee "codeserver.log"
}

startprod() {
    echo "Compiling with full optimization (production mode)..."
    compile
    echo ""
    echo "Starting web server http://${WEB_BINDADDRESS}:${WEB_PORT}"
    echo "Press Ctrl+C to stop"
    webserver
}

test() {
    "$SCRIPT_DIR/tools/run-tests-and-open-report.sh"
}

world2() {
    local scenario="${1:-1}"
    local steps="${2:-1000}"
    local dt="${3:-0.2}"
    local world2_port="${WORLD2_PORT:-18082}"

    if ! curl -fsS --max-time 2 "http://${WEB_BINDADDRESS}:${WEB_PORT}/world2.html" >/dev/null 2>&1; then
        echo "Starting web server http://${WEB_BINDADDRESS}:${WEB_PORT}"
        webserver >"webserver.log" 2>&1 &
        sleep 0.5
    fi

    echo "Starting World2 UI flow (scenario=${scenario}, steps=${steps}, dt=${dt}, port=${world2_port})"
    "$SCRIPT_DIR/tools/run-world2-ui.sh" \
        --scenario "${scenario}" \
        --steps "${steps}" \
        --dt "${dt}" \
        --port "${world2_port}" \
        --open
}

stopworld2() {
    local world2_port="${1:-${WORLD2_PORT:-18082}}"
    local pid_file="$SCRIPT_DIR/build/world2-server-${world2_port}.pid"
    local stopped="0"
    local was_running="0"

    if curl -fsS --max-time 2 "http://127.0.0.1:${world2_port}/health" >/dev/null 2>&1; then
        was_running="1"
    fi

    if [[ -f "$pid_file" ]]; then
        local pid
        pid="$(cat "$pid_file" 2>/dev/null || true)"
        if [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1; then
            echo "Stopping World2 server PID ${pid} (port ${world2_port})"
            kill "$pid" >/dev/null 2>&1 || true
            sleep 0.5
            stopped="1"
        fi
        rm -f "$pid_file"
    fi

    pkill -f ":world2-server:run --args=${world2_port}" 2>/dev/null || true
    pkill -f "johnnewto.world2.server.World2Server ${world2_port}" 2>/dev/null || true

    if curl -fsS --max-time 2 "http://127.0.0.1:${world2_port}/health" >/dev/null 2>&1; then
        echo "World2 server still appears to be running on port ${world2_port}."
        return 1
    fi

    if [[ "$stopped" == "1" || "$was_running" == "1" ]]; then
        echo "World2 server stopped."
    else
        echo "No running World2 server found on port ${world2_port}."
    fi
}

restartworld2() {
    local scenario="${1:-1}"
    local steps="${2:-1000}"
    local dt="${3:-0.2}"

    stopworld2 >/dev/null 2>&1 || true
    world2 "$scenario" "$steps" "$dt"
}


for func in $(compgen -A function); do
    if [[ $func == "$1" ]]; then
        shift
        $func "$@"
        exit $?
    fi
done

echo "Unknown command '$1'. Try one of the following:"
compgen -A function
exit 1
