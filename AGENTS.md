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

# Current Focus

Before adding more distributed-systems features (long form in [docs/IDEAS.md](docs/IDEAS.md)):
1. Exercise the db through an external client / CLI — commit to a real protocol boundary first
2. `make test` that stands up docker-compose with all test dependencies
3. Add detekt alongside ktlint now, while the findings list is still small

If asked "what's next," prefer these over Phase 2A replication work, and say so if the
roadmap in `README.md` or `openspec/` disagrees.

**Remind me of this Current Focus at the start of each session**, and again whenever a
task would start new work outside these three. One or two lines is enough — don't recite
the whole list every message.

# Project Overview

This is a Kotlin project for learning AI-assisted programming, with the goal of building a distributed in-memory cache with cross-datacenter replication (combining concepts from Apache Cassandra and Couchbase).

Tech stack, architecture, and conventions live in `openspec/project.md` — read it rather
than relying on a copy here.

# The Basics

Each rule is stated once, here. Nowhere else in this file restates them.

1. Safety is the highest priority. Take no action without a human's approval.
2. Explain your plan before executing, then stop and wait for approval.
3. Write one small failing test first, then implement.
4. Work incrementally: max 20 lines of code or 3 files per step. For docs, max 100 lines
   or 2 files. Larger than that, stop and ask.
5. After each step, explain what was done, suggest the next small step, and wait.
6. Favor clarity in code and succinctness in docs.

# Development Workflow

## Before Starting Any Task
1. Read `README.md` for current state and roadmap
2. Check `openspec list` for active changes, `openspec list --specs` for capabilities

Then The Basics apply: plan, approval, one failing test.

## Development Commands

Run `make help` for the full target list — it is generated from the Makefile, so it cannot
go stale. Prefer `make` over raw `./gradlew` so local runs match CI.

Beyond the obvious `build` / `test` / `format` / `check`: `test-unit`, `test-integration`,
and `test-e2e` run one test size at a time; `dev-up` / `dev-down` manage a long-lived JDK
container, and `build-docker` / `test-docker` / `check-docker` run Gradle inside it for
hosts without a local JDK.

A DevContainer is also configured, on Temurin JDK 21.

# Architecture and Testing

Package structure (`domain/` and `infrastructure/`), the functional-core / imperative-shell
design philosophy, code style, and the standing constraints are all specified in
`openspec/project.md`.

Tests are classified with JUnit 5 tags, currently `@Tag("unit")`, `@Tag("integration")`,
`@Tag("e2e")` — wired to the `test-unit` / `test-integration` / `test-e2e` targets via
`includeTags` in `build.gradle.kts`. See `docs/TEST_CLASSIFICATION.md` for which tag a new
test belongs in. Tests mirror the source structure.

**The project is moving to Google-style small / medium / large** (classifying by
dependencies and speed, not test shape) — see `docs/IDEAS.md` item 4. Until that rename
lands, keep using the existing tags; don't half-migrate a single file.

The OpenSpec workflow (when to write a proposal, scaffolding, validation, archiving) is
documented in `openspec/AGENTS.md`, which the block at the top of this file already tells
you to open. Do not duplicate it here.

# Development Guidelines

## Judgment
- Avoid over-engineering — default to <100 lines of new code
- Single-file implementations until proven insufficient
- Choose boring, proven patterns

## Security
Low-latency, high-traffic data store design. Watch for:
- Command injection
- Input validation at system boundaries only
- Trust internal code and framework guarantees

# Key Files

- `Makefile` - Every common task; `make help` lists them
- `build.gradle.kts` - Build configuration with Kotlin, JUnit 5, ktlint, and test tags
- `openspec/project.md` - Tech stack, architecture, code style, constraints
- `openspec/AGENTS.md` - Complete OpenSpec workflow documentation
- `README.md` - Current project state and roadmap
- `docs/IDEAS.md` - Directions not yet turned into proposals (see Current Focus above)
- `docs/TEST_CLASSIFICATION.md` - Which tag a new test belongs in
- `PHASE_2A_REVIEW.md` - Replication work: what's done, what's blocking
