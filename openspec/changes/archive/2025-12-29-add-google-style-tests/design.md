## Context
The project needs a clear test classification system. We're adopting industry-standard terminology:
- Unit: Fast, isolated tests with no external dependencies
- Integration: Tests with some dependencies or component interactions
- E2E (End-to-End): Full system tests with all dependencies

This is clearer and more semantically meaningful than Google's small/medium/large nomenclature.

## Goals / Non-Goals
- Goals:
  - Implement test classification using JUnit 5 tags
  - Enable selective test execution by classification
  - Provide clear guidelines for when to use each test type
  - Support the existing testing strategy defined in project.md
  - Make it easy for developers to classify their tests

- Non-Goals:
  - Enforcing classification at build time (guidelines, not strict rules)
  - Migrating existing tests (if any) - focus on establishing the pattern
  - Creating complex test infrastructure (keep it simple)

## Decisions
- Decision: Use JUnit 5 `@Tag` annotations for classification
  - Alternatives considered: Custom annotations, naming conventions, separate test directories
  - Rationale: JUnit 5 tags are standard, well-supported by Gradle, and allow flexible test filtering.

- Decision: Use string tags directly (`@Tag("unit")`, `@Tag("integration")`, `@Tag("e2e")`)
  - Alternatives considered: Custom wrapper annotations
  - Rationale: Simpler, more direct, follows Google's approach. Custom annotations would be over-engineering for this use case.

- Decision: Use unit/integration/e2e terminology instead of small/medium/large
  - Alternatives considered: Google's small/medium/large nomenclature
  - Rationale: Industry-standard terms that clearly communicate the test's purpose rather than just its size.

- Decision: Configure Gradle with separate test tasks for each classification
  - Alternatives considered: Using Gradle test filtering flags, using test suites
  - Rationale: Separate tasks (`testUnit`, `testIntegration`, `testE2e`) are more discoverable and provide better integration with IDEs and CI/CD.

- Decision: Default `test` task runs all tests
  - Alternatives considered: Default to small tests only
  - Rationale: Running all tests by default ensures nothing is missed. Developers can opt into running subsets when needed for speed.

## Risks / Trade-offs
- Developer confusion about classification → Mitigation: Clear documentation and examples
- Inconsistent classification → Mitigation: Provide guidelines and review process
- Maintenance overhead → Mitigation: Keep it simple, use standard JUnit 5 features

## Migration Plan
- No migration needed - this establishes the pattern for new tests
- Existing tests (if any) can be gradually migrated
- New tests should follow the classification from the start

## Open Questions
- Should we enforce that every test must have a classification tag?
- Do we need separate source sets for different test types, or keep them together with tags?

