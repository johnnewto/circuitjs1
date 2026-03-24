#!/usr/bin/env bash
# migrate-to-elements.sh
# Moves element domain directories under client/elements/
# and updates all package declarations and imports.
#
# Domains (use --domain flag to move one at a time):
#   1. electronics   (electronic circuit elements)
#   2. economics     (stock-flow economic elements)
#   3. math          (mathematical operation elements)
#   4. annotation    (visual-only schematic elements)
#   5. miscElm       (miscellaneous elements, renamed to misc/)
#
# Usage:
#   ./tools/migrate-to-elements.sh --domain electronics
#   ./tools/migrate-to-elements.sh --domain economics
#   ./tools/migrate-to-elements.sh --domain math
#   ./tools/migrate-to-elements.sh --domain annotation
#   ./tools/migrate-to-elements.sh --domain miscElm
#   ./tools/migrate-to-elements.sh --all   # move all at once (not recommended)
#
# Run from repository root. Requires git and perl.
# Run gate tests after each step:
#   ./gradlew compileJava && ./gradlew test && ./gradlew compileGwt

set -euo pipefail

BASE="src/com/lushprojects/circuitjs1/client"

usage() {
    echo "Usage: $0 --domain <name> | --all"
    echo ""
    echo "Domains: electronics, economics, math, annotation, miscElm"
    echo ""
    echo "Example workflow:"
    echo "  $0 --domain electronics && ./gradlew compileJava test compileGwt"
    echo "  $0 --domain economics && ./gradlew compileJava test compileGwt"
    echo "  # ... etc"
    exit 1
}

move_domain() {
    local domain="$1"
    local target_name="$2"  # allows renaming (e.g., miscElm -> misc)
    local old_pkg="com.lushprojects.circuitjs1.client.${domain}"
    local new_pkg="com.lushprojects.circuitjs1.client.elements.${target_name}"

    echo "=== Moving $domain -> elements/$target_name ==="

    # Create elements dir if needed
    mkdir -p "$BASE/elements"

    # Move directory
    if [[ -d "$BASE/$domain" ]]; then
        echo "Moving $BASE/$domain -> $BASE/elements/$target_name..."
        git mv "$BASE/$domain" "$BASE/elements/$target_name"
    else
        echo "ERROR: $BASE/$domain not found (already moved?)"
        exit 1
    fi

    # Update package declarations in moved files
    echo "Updating package declarations..."
    find "$BASE/elements/$target_name" -name "*.java" -exec perl -pi -e "
        s/package ${old_pkg//./\\.}/package ${new_pkg//./\\.}/g;
    " {} +

    # Update imports throughout src
    echo "Updating imports in src/..."
    find src -name "*.java" -exec perl -pi -e "
        s/import ${old_pkg//./\\.}/import ${new_pkg//./\\.}/g;
    " {} +

    # Update test files
    echo "Updating imports in test/..."
    find test -name "*.java" -exec perl -pi -e "
        s/${old_pkg//./\\.}/${new_pkg//./\\.}/g;
    " {} + 2>/dev/null || true

    echo ""
    echo "=== $domain migration complete ==="
    echo ""
    echo "Run gate tests now:"
    echo "  ./gradlew compileJava && ./gradlew test && ./gradlew compileGwt"
    echo ""
    echo "If gates pass, commit:"
    echo "  git add -A && git commit -m 'refactor: move $domain to client.elements.$target_name'"
}

# Parse arguments
if [[ $# -eq 0 ]]; then
    usage
fi

case "$1" in
    --domain)
        if [[ $# -lt 2 ]]; then
            usage
        fi
        case "$2" in
            electronics) move_domain "electronics" "electronics" ;;
            economics)   move_domain "economics" "economics" ;;
            math)        move_domain "math" "math" ;;
            annotation)  move_domain "annotation" "annotation" ;;
            miscElm)     move_domain "miscElm" "misc" ;;  # rename to misc
            *)
                echo "Unknown domain: $2"
                usage
                ;;
        esac
        ;;
    --all)
        echo "Moving all domains (not recommended - prefer one at a time)"
        move_domain "electronics" "electronics"
        move_domain "economics" "economics"
        move_domain "math" "math"
        move_domain "annotation" "annotation"
        move_domain "miscElm" "misc"
        ;;
    *)
        usage
        ;;
esac
