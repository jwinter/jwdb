# Change: Add DevContainer Support

## Why
We need a consistent, reproducible development environment that allows all developers to run tests and build the project in an isolated container. This ensures that everyone works with the same dependencies, Java version, and system configuration, reducing "works on my machine" issues. Additionally, we need guidance for setting up local Docker clients (like Colima) with appropriate resource allocation for this Kotlin project.

## What Changes
- Create `.devcontainer/devcontainer.json` configuration for VS Code/Cursor
- Create `Dockerfile` for the development container with Java 25 and Gradle
- Add documentation for Colima setup with recommended CPU and memory allocation
- Add documentation for alternative Docker client setup options
- Configure container to support running Gradle tests and builds
- Ensure container environment matches project requirements (Java 25, Temurin)

## Impact
- Affected specs: New capability `development-environment` will be created
- Affected code: New configuration files (no existing code to modify)
- Developer workflow: Developers can now use DevContainers for consistent development experience

