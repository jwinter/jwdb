#!/bin/bash
# Test script to verify DevContainer setup
# This can be run inside the DevContainer to verify everything works

set -e

echo "=== Testing DevContainer Setup ==="
echo ""

# Test 1: Java version
echo "1. Checking Java version..."
java -version
JAVA_VERSION=$(java -version 2>&1 | head -1 | awk -F'"' '{print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" != "25" ]; then
    echo "❌ ERROR: Expected Java 25, got Java $JAVA_VERSION"
    exit 1
fi
echo "✅ Java 25 detected"
echo ""

# Test 2: Java vendor (Temurin)
echo "2. Checking Java vendor..."
JAVA_VENDOR=$(java -version 2>&1 | grep -i "temurin" || echo "")
if [ -z "$JAVA_VENDOR" ]; then
    echo "❌ ERROR: Java vendor should be Temurin"
    exit 1
fi
echo "✅ Temurin detected"
echo ""

# Test 3: Gradle wrapper exists
echo "3. Checking Gradle wrapper..."
if [ ! -f "./gradlew" ]; then
    echo "❌ ERROR: gradlew not found"
    exit 1
fi
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "❌ ERROR: gradle-wrapper.jar not found"
    exit 1
fi
echo "✅ Gradle wrapper files present"
echo ""

# Test 4: Gradle version
echo "4. Checking Gradle version..."
./gradlew --version
GRADLE_VERSION=$(./gradlew --version | grep "Gradle" | awk '{print $2}')
echo "✅ Gradle $GRADLE_VERSION detected"
echo ""

# Test 5: Build project
echo "5. Testing project build..."
./gradlew build --no-daemon
echo "✅ Project builds successfully"
echo ""

# Test 6: Run tests
echo "6. Testing test execution..."
./gradlew test --no-daemon
echo "✅ Tests run successfully"
echo ""

# Test 7: Working directory
echo "7. Checking working directory..."
if [ "$PWD" != "/workspace" ]; then
    echo "⚠️  WARNING: Working directory is $PWD, expected /workspace"
else
    echo "✅ Working directory is /workspace"
fi
echo ""

echo "=== All tests passed! ==="

