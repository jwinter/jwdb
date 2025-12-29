<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

# Project Overview

This is a Kotlin project for learning AI-assisted programming, with the goal of building a distributed in-memory cache with cross-datacenter replication (combining concepts from Apache Cassandra and Couchbase).

**Tech Stack:**
- Kotlin 2.3.0 with Kotlin DSL
- JVM: Temurin Java 21
- Testing: JUnit 5
- Code quality: ktlint
- Build system: Gradle

# The Basics
1. Safety is the highest priority. Do not take any action without a human user's approval.
2. Explain your plan first before executing
3. Work incrementally in very small steps, DO NOT make changes larger than 20 lines of code or 3 files without approval. Then stop and wait for approval from a human.
4. Work incrementally in small steps on docs, DO NOT make changes larger than 100 lines or 2 files without approval
5. Write one small failing test first, then implement
6. Strive for succinctness and accuracy in documentation
7. Focus on clarity of code

# Development Workflow

## Before Starting Any Task:
1. Read `AGENTS.md` for workflow guidelines
2. Read `PROJECT_STATUS.md` for current state
3. Check `openspec list` for active changes
4. Check `openspec list --specs` for existing capabilities
5. Explain your plan and get approval

## During Development:
- Write one failing test then wait for human approval
- Max 20 lines of code changes
- Max 3 files modified
- Write tests first
- Focus on clarity

## After Each Step:
- Explain what was done
- Ask for approval to continue
- Suggest next small step

## Development Commands

### Building and Running
```bash
./gradlew build          # Build the project
./gradlew run            # Run the application
./gradlew clean          # Clean build artifacts
```

### Testing
```bash
./gradlew test           # Run all tests
./gradlew test --tests ClassName.testName  # Run a single test
```

### Code Quality
```bash
./gradlew ktlintCheck    # Check code style
./gradlew ktlintFormat   # Auto-format code
```

### DevContainer
This project uses a DevContainer for consistent development environments. The container is configured to use Temurin JDK 21.

# Architecture

## Package Structure (Hybrid Approach)
- `domain/` - Feature-based organization for pure business logic (cache, replication, consistency)
- `infrastructure/` - Layer-based organization for I/O boundaries (network, persistence, monitoring)

## Design Philosophy
**Functional core, imperative shell**: Pure functions at the core with imperative I/O at the boundaries (Gary Bernhardt's approach). Keep business logic pure and side effects at the boundaries.

## Testing Strategy
Follow **Google-style test classification**:
- **Small tests**: Fast, isolated unit tests (< 1 second)
- **Medium tests**: Integration tests with some dependencies (< 1 minute)
- **Large tests**: End-to-end tests with full system dependencies

Tests should mirror the source structure and use JUnit 5.

# OpenSpec Workflow

This project uses OpenSpec for spec-driven development. Key points:

## When to Create a Proposal
Create a proposal (`openspec/changes/[change-id]/`) for:
- New features or capabilities
- Breaking changes (API, schema, architecture)
- Performance optimizations that change behavior
- Security pattern updates

**Skip proposals for**: Bug fixes, typos, formatting, dependency updates, configuration changes.

## Implementation Workflow
1. Review existing specs in `openspec/specs/`
2. For new changes, scaffold proposal in `openspec/changes/[change-id]/`
3. Create `proposal.md`, `tasks.md`, and spec deltas
4. Run `openspec validate [change-id] --strict`
5. Get approval before implementation
6. Implement tasks sequentially, updating checklist
7. After deployment, archive with `openspec archive <change-id>`

## Important OpenSpec Commands
```bash
openspec list                  # List active changes
openspec list --specs          # List specifications
openspec show [item]           # Display change or spec
openspec validate [item] --strict  # Validate changes
openspec archive <change-id>   # Archive after deployment
```

# Development Guidelines

## Code Changes
- Work incrementally: max 20 lines of code or 3 files per step
- Write one small failing test first, then implement
- Explain your plan before executing
- Wait for human approval between steps

## Code Style
- Follow official Kotlin style guide
- Use ktlint for formatting (enforced via Gradle)
- Standard Kotlin naming conventions

## Important Constraints
- Stability is more important than performance
- Must compile on Temurin JVM Java 21
- Avoid over-engineering - default to <100 lines of new code
- Single-file implementations until proven insufficient
- Choose boring, proven patterns

## Security
Low-latency, high-traffic data store design. Watch for:
- Command injection
- Input validation at system boundaries only
- Trust internal code and framework guarantees

# Key Files

- `build.gradle.kts` - Build configuration with Kotlin, JUnit 5, and ktlint
- `settings.gradle.kts` - Project settings
- `openspec/project.md` - Project conventions and tech stack details
- `openspec/AGENTS.md` - Complete OpenSpec workflow documentation
- `AGENTS.md` - This file - Development workflow and safety guidelines
- `PROJECT_STATUS.md` - Current project state
