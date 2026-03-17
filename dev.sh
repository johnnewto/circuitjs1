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
    "$GWT_DIR/webAppCreator" -out ../tempProject com.lushprojects.circuitjs1.circuitjs1
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
        com.lushprojects.circuitjs1.circuitjs1
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
    pkill -f "com.google.gwt.dev.codeserver.CodeServer.*com.lushprojects.circuitjs1.circuitjs1" 2>/dev/null || true

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
