#!/bin/bash

# Complete build script for CircuitJS1 documentation
# This script compiles CircuitJS, renders the Quarto site, and copies files

echo "Building CircuitJS1 Documentation Site..."
echo "======================================="

# Step 1: Compile CircuitJS for production
echo "Step 1: Compiling CircuitJS application..."
cd .. && ./dev.sh compile
if [ $? -ne 0 ]; then
    echo "Error: CircuitJS compilation failed"
    exit 1
fi

# Return to docs directory
cd docs-template

# Step 2: Render the Quarto site
echo "Step 2: Rendering Quarto site..."
quarto render

if [ $? -ne 0 ]; then
    echo "Error: Quarto render failed"
    exit 1
fi

# Step 3: Copy CircuitJS files
echo "Step 3: Copying CircuitJS files..."
./copy-circuit-files.sh

if [ $? -ne 0 ]; then
    echo "Error: Failed to copy CircuitJS files"
    exit 1
fi

echo ""
echo "Build completed successfully!"
echo "Site is ready in _site/ directory"
echo ""
echo "To preview locally, run:"
echo "  cd _site && python3 -m http.server 8080"
echo "  Then open http://localhost:8080"