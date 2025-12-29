# Project Context

## Purpose
This project is designed to learn AI LLM-assisted programming with a focus on Kotlin backend services. The goal is to build a distributed in-memory cache with cross datacenter replication, combining concepts from Apache Cassandra and Couchbase.

## Tech Stack
- Kotlin (latest LTS version)
- Gradle (Kotlin DSL)
- Netty (for networking)
- JVM: Temurin Java 21
- Testing framework: JUnit 5

## Project Conventions

### Code Style
- Follow official Kotlin style guide
- Use ktlint for code formatting
- Follow standard Kotlin naming conventions

### Architecture Patterns
- **Functional core, imperative shell**: Pure functions at the core with imperative I/O at the boundaries (Gary Bernhardt's approach)
- Package structure: Hybrid approach
  - `domain/` - Feature-based organization for pure business logic (cache, replication, consistency)
  - `infrastructure/` - Layer-based organization for I/O boundaries (network, persistence, monitoring)

### Testing Strategy
- **Google-style test classification**: Small, medium, and large tests based on execution time and dependency requirements
  - Small: Fast, isolated unit tests
  - Medium: Integration tests with some dependencies
  - Large: End-to-end tests with full system dependencies
- Test types: Both unit and integration tests required
- Test organization: Mirror source structure
- Testing framework: JUnit 5

### Git Workflow
- Branching: Feature branches off `main`
- Commit message format: [To be decided - conventional commits vs simple messages]

## Domain Context
This is a low-latency, high-traffic data store designed to handle many concurrent connections. The system must support distributed in-memory caching with cross-datacenter replication capabilities.

## Important Constraints
- High performance is important
- Stability is more important than performance
- Must compile and run on Temurin JVM Java 21
- Kubernetes-native design preferred

## External Dependencies
- Kubernetes (for orchestration and deployment)
- [Additional external services/APIs to be documented as they are added]
