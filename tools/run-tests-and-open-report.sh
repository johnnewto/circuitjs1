#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPORT_PATH="$ROOT_DIR/build/reports/tests/test/index.html"

cd "$ROOT_DIR"
set +e
./gradlew test
TEST_EXIT_CODE=$?
set -e

if [[ ! -f "$REPORT_PATH" ]]; then
  echo "Test report not found: $REPORT_PATH"
  exit "$TEST_EXIT_CODE"
fi

# Convert local path to file:// URL
REPORT_URL="file://$REPORT_PATH"

# Try common Linux openers, then macOS fallback
if command -v xdg-open >/dev/null 2>&1; then
  nohup xdg-open "$REPORT_URL" >/dev/null 2>&1 &
elif command -v gio >/dev/null 2>&1; then
  nohup gio open "$REPORT_URL" >/dev/null 2>&1 &
elif command -v sensible-browser >/dev/null 2>&1; then
  nohup sensible-browser "$REPORT_URL" >/dev/null 2>&1 &
elif command -v open >/dev/null 2>&1; then
  nohup open "$REPORT_URL" >/dev/null 2>&1 &
else
  echo "No browser opener found. Open this file manually: $REPORT_PATH"
  exit "$TEST_EXIT_CODE"
fi

echo "Opened test report: $REPORT_PATH"
exit "$TEST_EXIT_CODE"
