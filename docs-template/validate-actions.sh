#!/bin/bash

# Validate GitHub Actions workflow for deprecated versions
echo "Validating GitHub Actions workflow for deprecated versions..."
echo "==========================================================="

WORKFLOW_FILE=".github/workflows/deploy-docs.yml"

if [ ! -f "$WORKFLOW_FILE" ]; then
    echo "❌ Workflow file not found: $WORKFLOW_FILE"
    exit 1
fi

echo "Checking for deprecated action versions..."

# Check for common deprecated versions
DEPRECATED_FOUND=false

# Check upload-pages-artifact
if grep -q "upload-pages-artifact@v[123]" "$WORKFLOW_FILE"; then
    echo "❌ Found deprecated upload-pages-artifact version (should be v4+)"
    DEPRECATED_FOUND=true
fi

# Check deploy-pages
if grep -q "deploy-pages@v[123]" "$WORKFLOW_FILE"; then
    echo "❌ Found deprecated deploy-pages version (should be v4+)"
    DEPRECATED_FOUND=true
fi

# Check configure-pages
if grep -q "configure-pages@v[1234]" "$WORKFLOW_FILE"; then
    echo "❌ Found deprecated configure-pages version (should be v5+)"
    DEPRECATED_FOUND=true
fi

# Check setup-java
if grep -q "setup-java@v[123]" "$WORKFLOW_FILE"; then
    echo "❌ Found deprecated setup-java version (should be v4+)"
    DEPRECATED_FOUND=true
fi

if [ "$DEPRECATED_FOUND" = false ]; then
    echo "✅ No deprecated action versions found"
    echo ""
    echo "Current versions in use:"
    grep -E "uses: actions/(upload-pages-artifact|deploy-pages|configure-pages|setup-java)" "$WORKFLOW_FILE" | sort -u
else
    echo ""
    echo "Please update the deprecated versions in $WORKFLOW_FILE"
fi

echo ""
echo "For latest versions, check:"
echo "- https://github.com/actions/upload-pages-artifact/releases"
echo "- https://github.com/actions/deploy-pages/releases" 
echo "- https://github.com/actions/configure-pages/releases"
echo "- https://github.com/actions/setup-java/releases"