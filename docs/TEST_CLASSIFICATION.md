# Test Classification Guide

This project uses a three-tier test classification system to organize tests by scope and dependencies.

## Test Types

### Unit Tests (`@Tag("unit")`)

**Purpose**: Test individual functions or classes in isolation.

**Characteristics**:
- Fast execution (typically < 100ms)
- No external dependencies (databases, networks, file systems)
- No test doubles or mocks needed for external services
- Tests pure business logic

**When to use**:
- Testing calculation logic
- Testing data transformations
- Testing domain models
- Testing utility functions

**Example**:
```kotlin
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

@Tag("unit")
class CacheKeyTest {
    @Test
    fun `should create cache key from string`() {
        val key = "user:123"
        val result = key.hashCode()
        assertEquals(key.hashCode(), result)
    }
}
```

**Run unit tests only**:
```bash
./gradlew testUnit
make test-unit
```

---

### Integration Tests (`@Tag("integration")`)

**Purpose**: Test interactions between components or modules.

**Characteristics**:
- Moderate execution time (100ms - 1s)
- May use in-memory databases or test doubles
- Tests component interactions
- Limited external dependencies

**When to use**:
- Testing cache operations with in-memory storage
- Testing service layer interactions
- Testing data access with in-memory databases
- Testing multiple components working together

**Example**:
```kotlin
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

@Tag("integration")
class CacheIntegrationTest {
    @Test
    fun `should store and retrieve value from in-memory cache`() {
        val cache = mutableMapOf<String, String>()
        cache["user:123"] = "John"

        val result = cache["user:123"]

        assertEquals("John", result)
    }
}
```

**Run integration tests only**:
```bash
./gradlew testIntegration
make test-integration
```

---

### End-to-End Tests (`@Tag("e2e")`)

**Purpose**: Test complete workflows through the entire system.

**Characteristics**:
- Longer execution time (> 1s)
- Uses real external dependencies (or realistic test environments)
- Tests full user workflows
- Validates system behavior from end to end

**When to use**:
- Testing complete cache replication workflows
- Testing cross-datacenter operations
- Testing full API request/response cycles
- Testing deployment scenarios

**Example**:
```kotlin
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

@Tag("e2e")
class EndToEndTest {
    @Test
    fun `should perform full cache workflow`() {
        val cache = mutableMapOf<String, String>()

        // Store phase
        cache["key1"] = "value1"
        cache["key2"] = "value2"

        // Retrieve phase
        val result1 = cache["key1"]
        val result2 = cache["key2"]

        // Verify phase
        assertTrue(result1 == "value1" && result2 == "value2")
    }
}
```

**Run e2e tests only**:
```bash
./gradlew testE2e
make test-e2e
```

---

## Running Tests

### Run all tests
```bash
./gradlew test
make test
```

### Run by classification
```bash
# Unit tests only (fastest)
./gradlew testUnit
make test-unit

# Integration tests only
./gradlew testIntegration
make test-integration

# End-to-end tests only (slowest)
./gradlew testE2e
make test-e2e
```

## Best Practices

1. **Always tag your tests** - Every test should have exactly one classification tag
2. **Start with unit tests** - Write unit tests first, then integration, then e2e
3. **Prefer unit tests** - They're fastest and easiest to maintain
4. **Use the right level** - Don't write an e2e test when a unit test would suffice
5. **Keep tests isolated** - Each test should be independent and not rely on other tests

## Decision Tree

```
Does the test use external dependencies (DB, network, files)?
├─ No → Unit test (@Tag("unit"))
└─ Yes
   ├─ Uses test doubles or in-memory versions? → Integration test (@Tag("integration"))
   └─ Uses real external systems? → E2E test (@Tag("e2e"))
```

## Examples in This Project

- **Unit**: `src/test/kotlin/domain/cache/CacheKeyTest.kt`
- **Integration**: `src/test/kotlin/domain/cache/CacheIntegrationTest.kt`
- **E2E**: `src/test/kotlin/infrastructure/EndToEndTest.kt`
