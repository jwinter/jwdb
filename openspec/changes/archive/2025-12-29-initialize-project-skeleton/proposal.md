# Change: Initialize Project Skeleton

## Why
We need a foundational Kotlin project structure that compiles on Temurin JVM Java 25 to begin development. This establishes the build system, directory structure, and basic configuration according to the project conventions defined in `openspec/project.md`.

## What Changes
- Create Gradle build configuration (Kotlin DSL) with Java 25 compatibility
- Set up project directory structure following domain/infrastructure pattern
- Configure Kotlin LTS version
- Add ktlint for code formatting
- Configure JUnit 5 for testing
- Create basic source and test directory structure
- Add a minimal Kotlin source file that compiles successfully

## Impact
- Affected specs: New capability `project-foundation` will be created
- Affected code: New project structure (no existing code to modify)

