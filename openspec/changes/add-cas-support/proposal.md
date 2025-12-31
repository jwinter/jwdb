# Change: Add CAS (Check-And-Set) Support

**Status: Proposed**

## Why
Check-And-Set (CAS) is a critical primitive for optimistic concurrency control in distributed caching systems. It enables clients to safely update cached values only when they haven't been modified by another client, preventing lost updates and race conditions without requiring pessimistic locks.

CAS is essential for:
- **Race-free counters**: Increment counters without lost updates
- **Session management**: Update session data only if unchanged
- **Optimistic locking**: Implement application-level transactions
- **Distributed coordination**: Build higher-level synchronization primitives

This feature naturally builds on the versioning infrastructure we established in Phase 2A (single-DC replication), where every cache value already has a distributed `Version` (timestamp + nodeId). CAS operations can leverage this existing version for conflict detection.

## What Changes
- Add `compareAndSet()` operation to Cache interface
- Implement CAS protocol in network layer (protobuf messages)
- Add CAS-specific consistency level handling
- Extend statistics to track CAS success/failure rates
- Add comprehensive tests for CAS edge cases and race conditions

## Impact
- Affected specs: `cache-operations` (new CAS operation), `serialization` (new CAS messages)
- Affected code: Cache interface, InMemoryCache, CacheProtocolHandler, protobuf schemas
- Dependencies: Requires Phase 2A versioning system (completed)
- Architecture: Enables optimistic concurrency without distributed locks
- Consistency: CAS operations require version-based conflict detection
- Performance: CAS is lightweight (single roundtrip), more efficient than locks
- Testing: Requires concurrent write tests and race condition scenarios

## Design Decisions

### CAS Operation Semantics
- **Compare**: Match expected version against current version
- **Set**: Update value only if versions match
- **Atomic**: Compare and set happen atomically
- **Return**: Success/failure + current version (for retry)

### Version Matching Strategy
- **Exact match**: Expected version must match current version exactly
- **Null handling**: First write (version=null) always succeeds
- **Deleted keys**: CAS fails if key doesn't exist (unless expected version is null)
- **Expired entries**: CAS fails on expired entries

### Consistency Levels
- **CAS + QUORUM**: Safest - ensures majority sees the update
- **CAS + ONE**: Fastest - but risk of conflicts in distributed scenario
- **CAS + ALL**: Strongest - all replicas must agree
- **Recommendation**: Default to QUORUM for balance of safety and performance

### Client Retry Pattern
```kotlin
// Typical CAS retry loop
do {
    val current = cache.get(key)
    val expectedVersion = current.version
    val newValue = computeNewValue(current.data)
    val result = cache.compareAndSet(
        key = key,
        expectedVersion = expectedVersion,
        newValue = newValue,
        newVersion = Version.now(nodeId)
    )
} while (!result.success)
```

### Distributed CAS Coordination
1. **Coordinator** receives CAS request with expectedVersion
2. **Query replicas** based on consistency level
3. **Compare versions** from all responding replicas
4. **If all match** expectedVersion: Send write to replicas
5. **If mismatch**: Return failure with current version
6. **Return** success/failure + current version to client

## API Design

### Cache Interface Extension
```kotlin
interface Cache<T> {
    // Existing operations...

    /**
     * Atomically updates a value only if current version matches expected version.
     * Returns success/failure and the current version.
     */
    fun compareAndSet(
        key: CacheKey,
        expectedVersion: Version?,
        newValue: CacheValue<T>,
    ): CasResult<T>
}

sealed class CasResult<T> {
    data class Success<T>(val newVersion: Version) : CasResult<T>()
    data class Failure<T>(val currentValue: CacheValue<T>?) : CasResult<T>()
}
```

### Protocol Buffers Schema
```protobuf
// CAS request
message CasRequest {
  string key = 1;
  Version expected_version = 2;  // Null means "key must not exist"
  CacheEntry new_entry = 3;
}

// CAS response
message CasResponse {
  enum Status {
    SUCCESS = 0;          // CAS succeeded, value updated
    VERSION_MISMATCH = 1; // Expected version didn't match
    KEY_NOT_FOUND = 2;    // Key doesn't exist (and expected_version != null)
    ERROR = 3;            // Other error
  }

  Status status = 1;
  Version current_version = 2;  // Version after CAS (success) or current version (failure)
  CacheEntry current_entry = 3; // Only present on failure, for client retry
  string error_message = 4;     // Only present on ERROR
}
```

## Use Cases

### Use Case 1: Distributed Counter
```kotlin
fun incrementCounter(cache: Cache<Long>, key: CacheKey) {
    var success = false
    while (!success) {
        val result = cache.get(key)
        val current = when (result) {
            is CacheResult.Hit -> result.value
            is CacheResult.Miss -> CacheValue(0L, version = null)
        }

        val newValue = CacheValue(
            data = current.data + 1,
            version = Version.now(nodeId)
        )

        val casResult = cache.compareAndSet(key, current.version, newValue)
        success = casResult is CasResult.Success
    }
}
```

### Use Case 2: Session Update
```kotlin
fun updateSession(
    cache: Cache<Session>,
    sessionId: String,
    update: (Session) -> Session
): Boolean {
    val result = cache.get(CacheKey(sessionId))
    val current = when (result) {
        is CacheResult.Hit -> result.value
        else -> return false // Session doesn't exist
    }

    val updated = CacheValue(
        data = update(current.data),
        version = Version.now(nodeId)
    )

    val casResult = cache.compareAndSet(
        CacheKey(sessionId),
        current.version,
        updated
    )

    return casResult is CasResult.Success
}
```

### Use Case 3: Rate Limiting
```kotlin
data class RateLimit(val count: Int, val windowStart: Instant)

fun checkRateLimit(
    cache: Cache<RateLimit>,
    userId: String,
    maxRequests: Int,
    windowSeconds: Long
): Boolean {
    val key = CacheKey("ratelimit:$userId")
    val now = Instant.now()

    var allowed = false
    var attempts = 0

    while (!allowed && attempts < 3) {
        val result = cache.get(key)
        val current = when (result) {
            is CacheResult.Hit -> {
                val limit = result.value.data
                if (now.epochSecond - limit.windowStart.epochSecond > windowSeconds) {
                    // Window expired, reset
                    RateLimit(0, now)
                } else {
                    limit
                }
            }
            is CacheResult.Miss -> RateLimit(0, now)
        }

        if (current.count < maxRequests) {
            val updated = CacheValue(
                data = current.copy(count = current.count + 1),
                version = Version.now(nodeId)
            )

            val casResult = cache.compareAndSet(
                key,
                result.value?.version,
                updated
            )

            allowed = casResult is CasResult.Success
        } else {
            return false // Rate limit exceeded
        }

        attempts++
    }

    return allowed
}
```

## Out of Scope
- **Compare-And-Delete (CAD)**: Future enhancement
- **Multi-key CAS**: Transactions across multiple keys (Phase 3+ with 2PC/Paxos)
- **CAS with predicates**: Beyond version comparison (e.g., "if value > 10")
- **Conditional updates**: Complex conditions beyond version matching

## Dependencies
- ✅ Version data class (timestamp + nodeId) - completed in Phase 2A
- ✅ CacheValue with Version field - completed in Phase 2A
- ✅ Conflict resolution infrastructure - completed in Phase 2A
- ⏳ Replication coordinator - required for distributed CAS (Phase 2A in progress)

## Testing Strategy
- **Unit tests**: CAS on single node, version matching, null handling
- **Integration tests**: Concurrent CAS from multiple threads
- **E2E tests**: Distributed CAS across cluster with consistency levels
- **Race condition tests**: Intentional concurrent writes to same key
- **Performance tests**: CAS throughput and latency under contention
- **Chaos tests**: CAS behavior during node failures

## Implementation Phases

### Phase 1: Single-Node CAS (Foundation)
- Implement CAS in InMemoryCache
- Add version comparison logic
- Unit tests for CAS semantics

### Phase 2: Network Protocol
- Add CAS protobuf messages
- Implement CAS in CacheProtocolHandler
- Integration tests for client-server CAS

### Phase 3: Distributed CAS
- Implement CAS in ReplicationCoordinator
- Handle consistency levels for CAS
- E2E tests with multi-node cluster

### Phase 4: Optimization
- Add CAS statistics and monitoring
- Performance tuning
- Client retry strategies and helpers

## Success Metrics
- CAS operations complete in < 5ms (p99, single-node)
- CAS operations complete in < 20ms (p99, distributed with QUORUM)
- Zero lost updates under concurrent load
- CAS success rate > 90% under normal contention
- Comprehensive test coverage (>95%) for all CAS scenarios
