#!/bin/bash

# Quick documentation-only build
# Use this when you've only changed documentation files, not CircuitJS code
# For CircuitJS code changes, use ./build.sh instead

echo "Quick Documentation Build..."
echo "=========================="

# Step 0: Sync published reference docs from dev_docs whitelist
echo "Step 0: Syncing public reference docs..."
if [ -x "../dev_docs/sync_reference_docs.sh" ]; then
    ../dev_docs/sync_reference_docs.sh
else
    bash ../dev_docs/sync_reference_docs.sh
fi

if [ $? -ne 0 ]; then
    echo "Error: Reference docs sync failed"
    exit 1
fi

# Step 1: Render the Quarto site
echo "Step 1: Rendering Quarto site..."
quarto render

if [ $? -ne 0 ]; then
    echo "Error: Quarto render failed"
    exit 1
fi

# Step 2: Copy existing CircuitJS files
echo "Step 2: Copying existing CircuitJS files..."
./copy-circuit-files.sh

if [ $? -ne 0 ]; then
    echo "Error: Failed to copy CircuitJS files"
    exit 1
fi

echo ""
echo "Quick build completed!"
echo "Site is ready in _site/ directory"
echo ""
echo "Note: This uses existing CircuitJS build. If you changed"
echo "CircuitJS code, run ./build.sh for a complete rebuild."