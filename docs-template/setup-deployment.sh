#!/bin/bash

# Setup script for GitHub Pages deployment
echo "Setting up GitHub Pages deployment for CircuitJS1 documentation..."
echo "=================================================================="

# Check if we're in the right directory
if [ ! -f "dev.sh" ] || [ ! -d "docs-template" ]; then
    echo "Error: Please run this script from the main CircuitJS1 repository root"
    echo "Expected structure:"
    echo "  - dev.sh (CircuitJS build script)"
    echo "  - docs-template/ (documentation source)"
    exit 1
fi

echo "✓ Repository structure looks correct"

# Check if GitHub Actions workflow exists
if [ ! -f ".github/workflows/deploy-docs.yml" ]; then
    echo "Creating GitHub Actions workflow..."
    mkdir -p .github/workflows
    cp docs-template/.github/workflows/deploy.yml .github/workflows/deploy-docs.yml
    echo "✓ Created .github/workflows/deploy-docs.yml"
else
    echo "✓ GitHub Actions workflow already exists"
fi

# Verify build scripts are executable
chmod +x docs-template/build.sh
chmod +x docs-template/build-docs-only.sh
chmod +x docs-template/copy-circuit-files.sh
echo "✓ Made build scripts executable"

echo ""
echo "Setup complete! Next steps:"
echo ""
echo "1. Push your changes to GitHub:"
echo "   git add ."
echo "   git commit -m 'Add documentation site with GitHub Pages deployment'"
echo "   git push origin main"
echo ""
echo "2. Go to your GitHub repository Settings → Pages"
echo "3. Under 'Source', select 'GitHub Actions'"
echo "4. The workflow will automatically run on the next push"
echo ""
echo "5. Your site will be available at:"
echo "   https://$(git config --get remote.origin.url | sed 's/.*github.com[:/]//' | sed 's/.git$//' | tr '[:upper:]' '[:lower:]')/docs-template"
echo ""
echo "Note: It may take a few minutes for the first deployment to complete."