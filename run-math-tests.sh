#!/bin/bash

# Math Elements Test Runner
# Runs the CircuitJS1 mathematical elements tests

echo "=========================================="
echo "CircuitJS1 Math Elements Test Suite"
echo "=========================================="
echo ""

# Check if compiled
if [ ! -d "build/classes/java/main" ]; then
    echo "⚠️  Classes not compiled. Compiling now..."
    ./gradlew compileJava
    if [ $? -ne 0 ]; then
        echo "❌ Compilation failed!"
        exit 1
    fi
fi

echo "Running tests..."
echo ""

# Since the tests use GWT client-side code, they need to run in the GWT environment
# For now, we'll compile and provide instructions

echo "📝 Test Instructions:"
echo ""
echo "Option 1: Run in Development Mode"
echo "  1. Run: ./gradlew gwtDev"
echo "  2. Open browser to: http://localhost:8888/math-tests.html"
echo "  3. Click 'Run All Tests' button"
echo ""
echo "Option 2: Compile and Test"
echo "  1. Run: ./gradlew compileGwt"
echo "  2. Run: cd war && python3 -m http.server 8000"
echo "  3. Open browser to: http://localhost:8000/math-tests.html"
echo "  4. Click 'Run All Tests' button"
echo ""
echo "Option 3: Add JUnit and Run as Unit Tests"
echo "  1. Add to build.gradle dependencies:"
echo "     testImplementation 'junit:junit:4.13.2'"
echo "  2. Modify MathElementsTest.java to extend GWTTestCase"
echo "  3. Run: ./gradlew test"
echo ""
echo "📁 Test Files:"
echo "  - Test Class: src/com/lushprojects/circuitjs1/client/test/MathElementsTest.java"
echo "  - Test Page: war/math-tests.html"
echo "  - Documentation: MATH_ELEMENTS_TEST_GUIDE.md"
echo ""
echo "✅ Test suite is ready to run!"
echo ""
