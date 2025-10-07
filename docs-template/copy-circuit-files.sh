#!/bin/bash

# Script to copy CircuitJS files to the Quarto _site directory
# Run this after 'quarto render'

SITE_DIR="_site"
WAR_DIR="../war"

echo "Copying CircuitJS files to $SITE_DIR..."

# Create _site directory if it doesn't exist
mkdir -p "$SITE_DIR"

# Copy essential CircuitJS files
echo "Copying circuitjs.html..."
cp "$WAR_DIR/circuitjs.html" "$SITE_DIR/"

echo "Copying circuitjs1 directory..."
cp -r "$WAR_DIR/circuitjs1" "$SITE_DIR/"

echo "Copying font directory..."
cp -r "$WAR_DIR/font" "$SITE_DIR/"

echo "Copying lz-string.min.js..."
cp "$WAR_DIR/lz-string.min.js" "$SITE_DIR/"

echo "Copying manifest.json..."
cp "$WAR_DIR/manifest.json" "$SITE_DIR/"

echo "CircuitJS files copied successfully!"