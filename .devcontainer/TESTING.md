# Testing the DevContainer

This guide explains how to test that the DevContainer is set up correctly.

## Prerequisites

- Docker (or Docker alternative like Colima) installed and running
- VS Code or Cursor with the Dev Containers extension installed

## Method 1: Using VS Code/Cursor (Recommended)

1. **Open the project** in VS Code or Cursor
2. **Rebuild the container:**
   - Command Palette (`Cmd+Shift+P` / `Ctrl+Shift+P`)
   - Select "Dev Containers: Rebuild Container"
   - Wait for the container to build and start

3. **Verify the postCreateCommand ran:**
   - Check the terminal output when the container starts
   - You should see Java version and Gradle version output

4. **Run the test script:**
   ```bash
   ./.devcontainer/test-devcontainer.sh
   ```

5. **Manual verification:**
   ```bash
   # Check Java version
   java -version
   
   # Check Gradle wrapper
   ./gradlew --version
   
   # Build the project
   ./gradlew build
   
   # Run tests
   ./gradlew test
   ```

## Method 2: Using Docker CLI

If you want to test the container build without VS Code/Cursor:

1. **Build the container:**
   ```bash
   cd /path/to/project
   docker build -f .devcontainer/Dockerfile -t kotlin-devcontainer .
   ```

2. **Run the container interactively:**
   ```bash
   docker run -it --rm \
     -v "$(pwd):/workspace" \
     -w /workspace \
     kotlin-devcontainer \
     bash
   ```

3. **Inside the container, run the test script:**
   ```bash
   ./.devcontainer/test-devcontainer.sh
   ```

## What Gets Tested

The test script verifies:

- ✅ Java 25 (Temurin) is installed
- ✅ Gradle wrapper files are present
- ✅ Gradle version matches project requirements
- ✅ Project builds successfully
- ✅ Tests run successfully
- ✅ Working directory is correct

## Expected Results

When everything works correctly, you should see:

- Java version output showing `openjdk version "25"` with `Temurin` vendor
- Gradle wrapper version output showing `Gradle 9.2.1`
- Successful build output
- Successful test execution
- All test script checks passing

## Troubleshooting

### Container won't build
- Check Docker is running: `docker ps`
- Check Docker has enough resources (see COLIMA_SETUP.md)
- Review Docker build logs for errors

### Gradle wrapper not found
- Ensure `gradlew` and `gradle/wrapper/` files are committed to the repository
- Check that files weren't excluded by `.gitignore`

### Java version incorrect
- Verify the Dockerfile uses `eclipse-temurin:25-jdk` base image
- Rebuild the container to pick up changes

### Build or tests fail
- Check that all source files are present
- Verify project structure matches expected layout
- Review build error messages for specific issues

