## Context
This project requires Java 25 (Temurin) and uses Gradle for builds. Developers may be on macOS, Linux, or Windows, and need a consistent development environment. Docker Desktop is not always available or preferred, so we need to support alternative Docker clients like Colima.

## Goals / Non-Goals
- Goals:
  - Provide a DevContainer that matches production-like environment
  - Support running all tests and builds inside the container
  - Document setup for Colima and other Docker clients
  - Ensure Java 25 (Temurin) is correctly installed
  - Make it easy for new developers to get started

- Non-Goals:
  - Supporting all possible Docker client configurations (focus on common ones)
  - Optimizing container size for production use (this is for development)
  - Supporting older Java versions

## Decisions
- Decision: Use official Eclipse Temurin Docker image as base
  - Alternatives considered: OpenJDK official, Amazon Corretto, Adoptium
  - Rationale: Project specifies Temurin, and Eclipse Temurin images are well-maintained and match the project requirements

- Decision: Include Gradle wrapper in container (use project's gradlew)
  - Alternatives considered: Install Gradle globally in container
  - Rationale: Using gradlew ensures version consistency and matches local development workflow

- Decision: Document Colima as primary alternative Docker client
  - Alternatives considered: Docker Desktop, Podman, Lima
  - Rationale: Colima is popular on macOS, lightweight, and well-documented. We'll also mention other options.

- Decision: Recommend minimum 4GB RAM and 2 CPUs for Colima
  - Alternatives considered: Lower resources (2GB, 1 CPU)
  - Rationale: Kotlin compilation and Gradle builds can be resource-intensive, especially with tests. Better to have headroom.

## Risks / Trade-offs
- Container startup time → Mitigation: Use multi-stage builds and cache layers appropriately
- Resource usage on developer machines → Mitigation: Provide clear documentation on resource requirements
- Docker client compatibility → Mitigation: Test with common clients and document alternatives

## Migration Plan
- No migration needed - this is a new capability
- Developers can opt-in to using DevContainers
- Existing local development workflows remain unchanged

## Open Questions
- Should we include any additional development tools in the container (e.g., ktlint, git)?
- Do we need to configure any specific network settings for the container?

