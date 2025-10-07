#!/bin/bash

# Check GitHub Actions deployment requirements
echo "Checking GitHub Actions Deployment Requirements"
echo "=============================================="

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "Repository root: $REPO_ROOT"
echo ""

# Check required files
echo "Checking required files..."
MISSING_FILES=false

files_to_check=("dev.sh" "build.xml" "docs-template/build.sh" "docs-template/copy-circuit-files.sh")
for file in "${files_to_check[@]}"; do
    if [ -f "$file" ]; then
        echo "✅ Found: $file"
    else
        echo "❌ Missing: $file"
        MISSING_FILES=true
    fi
done

# Check if files are executable
echo ""
echo "Checking executable permissions..."
exec_files=("dev.sh" "docs-template/build.sh" "docs-template/build-docs-only.sh" "docs-template/copy-circuit-files.sh")
for file in "${exec_files[@]}"; do
    if [ -f "$file" ] && [ -x "$file" ]; then
        echo "✅ Executable: $file"
    elif [ -f "$file" ]; then
        echo "⚠️  Not executable: $file (will be fixed by GitHub Actions)"
    else
        echo "❌ Missing: $file"
        MISSING_FILES=true
    fi
done

# Check workflow file
echo ""
echo "Checking GitHub Actions workflow..."
if [ -f ".github/workflows/deploy-docs.yml" ]; then
    echo "✅ Found: .github/workflows/deploy-docs.yml"
    
    # Check for required components in workflow
    if grep -q "ant" ".github/workflows/deploy-docs.yml"; then
        echo "✅ Ant installation included in workflow"
    else
        echo "❌ Ant installation missing from workflow"
        MISSING_FILES=true
    fi
    
    if grep -q "actions/upload-pages-artifact@v4" ".github/workflows/deploy-docs.yml"; then
        echo "✅ Using current upload-pages-artifact version"
    else
        echo "⚠️  May be using deprecated upload-pages-artifact version"
    fi
else
    echo "❌ Missing: .github/workflows/deploy-docs.yml"
    MISSING_FILES=true
fi

# Check Java and Ant availability (local)
echo ""
echo "Checking local build environment..."
if command -v java &> /dev/null; then
    java_version=$(java -version 2>&1 | head -n 1)
    echo "✅ Java found: $java_version"
else
    echo "⚠️  Java not found locally (GitHub Actions will install)"
fi

if command -v ant &> /dev/null; then
    ant_version=$(ant -version 2>&1 | head -n 1)
    echo "✅ Ant found: $ant_version"
else
    echo "⚠️  Ant not found locally (GitHub Actions will install)"
fi

# Test compilation
echo ""
echo "Testing local compilation..."
if [ -x "dev.sh" ]; then
    echo "Testing ./dev.sh compile..."
    if ./dev.sh compile > /tmp/compile-test.log 2>&1; then
        echo "✅ Local compilation successful"
    else
        echo "❌ Local compilation failed. Check log:"
        tail -10 /tmp/compile-test.log
        MISSING_FILES=true
    fi
else
    echo "⚠️  Cannot test compilation (dev.sh not executable or missing)"
fi

echo ""
if [ "$MISSING_FILES" = true ]; then
    echo "❌ Issues found. Please fix the above problems before deploying."
    exit 1
else
    echo "✅ All requirements met for GitHub Actions deployment!"
fi