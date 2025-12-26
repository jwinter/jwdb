## Context
The project already specifies Google-style test classification in `openspec/project.md`:
- Small: Fast, isolated unit tests
- Medium: Integration tests with some dependencies
- Large: End-to-end tests with full system dependencies

However, this classification is not yet implemented. We need to establish the structure, conventions, and tooling to make this practical.

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
  - Rationale: JUnit 5 tags are standard, well-supported by Gradle, and allow flexible test filtering. They're also discoverable and explicit.

- Decision: Create custom tag annotations (`@SmallTest`, `@MediumTest`, `@LargeTest`)
  - Alternatives considered: Using string tags directly, using JUnit 5 built-in tags
  - Rationale: Custom annotations provide type safety, better IDE support, and make the classification explicit and discoverable.

- Decision: Configure Gradle with separate test tasks for each classification
  - Alternatives considered: Using Gradle test filtering flags, using test suites
  - Rationale: Separate tasks (`testSmall`, `testMedium`, `testLarge`) are more discoverable and provide better integration with IDEs and CI/CD.

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

