# Cache Statistics Guide

This guide covers the comprehensive statistics system available in jwdb's cache implementation.

## Overview

The cache provides detailed statistics to help you monitor performance, debug issues, and make informed decisions about cache configuration. All statistics are thread-safe and can be accessed concurrently without performance degradation.

## Available Statistics Fields

### CacheStats Data Class

The `CacheStats` data class provides the following fields:

#### Core Metrics

**`hits: Long`**
- Number of successful cache retrievals (key found and not expired)
- Incremented on each `get()` operation that returns a valid value
- Used to calculate hit rate

**`misses: Long`**
- Number of failed cache retrievals (key not found or expired)
- Incremented on each `get()` operation that doesn't find a valid value
- Includes both non-existent keys and expired entries

**`size: Long`**
- Current number of entries stored in the cache
- Snapshot value at the time `getStats()` is called
- Does not include expired entries that haven't been removed yet

#### Operation Counters

**`putCount: Long`**
- Total number of `put()` operations performed
- Incremented for both new entries and updates to existing keys
- Tracked since cache creation or last stats reset

**`deleteCount: Long`**
- Total number of `delete()` operations performed
- Incremented regardless of whether the key existed
- Useful for tracking cache mutation patterns

**`clearCount: Long`**
- Total number of `clear()` operations performed
- Each call to `clear()` increments this counter by 1
- Useful for monitoring full cache invalidations

#### Eviction Metrics

**`evictions: Long`**
- Total number of entries evicted due to size constraints
- Only incremented when maxSize is set and reached
- Sum of all policy-specific eviction counts

**`evictionsByPolicy: Map<EvictionPolicy, Long>`**
- Breakdown of evictions by policy type (LRU, FIFO, RANDOM)
- Each policy tracks its eviction count independently
- Empty map if no evictions have occurred

#### Timing Information

**`createdAt: Long`**
- Unix timestamp (milliseconds) when the cache was created
- Can be used to calculate cache uptime
- Reset when `resetStats()` is called (updates to reset time)

#### Computed Properties

**`hitRate: Double`**
- Percentage of successful cache lookups (0.0 to 1.0)
- Calculated as: `hits / (hits + misses)`
- Returns 0.0 if no get operations have been performed
- Example: 0.85 means 85% hit rate

**`totalOperations: Long`**
- Sum of all cache operations
- Calculated as: `hits + misses + putCount + deleteCount + clearCount`
- Useful for understanding overall cache activity

## Examples of Monitoring Cache Stats

### Basic Statistics Retrieval

```kotlin
val cache = InMemoryCache<String>()

// Perform some operations
cache.put(CacheKey("user:123"), CacheValue("John"))
cache.put(CacheKey("user:456"), CacheValue("Jane"))
cache.get(CacheKey("user:123")) // hit
cache.get(CacheKey("user:999")) // miss

// Get statistics
val stats = cache.getStats()
println("Hits: ${stats.hits}")           // 1
println("Misses: ${stats.misses}")       // 1
println("Hit Rate: ${stats.hitRate}")    // 0.5 (50%)
println("Total Ops: ${stats.totalOperations}") // 4 (2 puts + 1 hit + 1 miss)
```

### Formatted Statistics Output

For logging and monitoring, use the formatted output:

```kotlin
val cache = InMemoryCache<String>()

// Perform operations...
cache.put(CacheKey("key1"), CacheValue("value1"))
cache.get(CacheKey("key1"))

// Get human-readable output
println(cache.getStatsFormatted())
```

Output:
```
Cache Statistics:
  Size: 1 entries
  Hits: 1
  Misses: 0
  Hit Rate: 100.00%
  Operations:
    Puts: 1
    Deletes: 0
    Clears: 0
    Total: 2
  Evictions: 0
  Uptime: 42s
```

### Monitoring Eviction Policies

Track which eviction policy is being used most:

```kotlin
val cache = InMemoryCache<String>(
    maxSize = 3,
    evictionPolicy = EvictionPolicy.LRU
)

// Fill cache beyond maxSize
cache.put(CacheKey("key1"), CacheValue("value1"))
cache.put(CacheKey("key2"), CacheValue("value2"))
cache.put(CacheKey("key3"), CacheValue("value3"))
cache.put(CacheKey("key4"), CacheValue("value4")) // triggers eviction

val stats = cache.getStats()
println("Total evictions: ${stats.evictions}") // 1
println("LRU evictions: ${stats.evictionsByPolicy[EvictionPolicy.LRU]}") // 1
```

### Monitoring Cache Performance Over Time

```kotlin
class CacheMonitor(private val cache: InMemoryCache<String>) {
    private var lastStats = cache.getStats()

    fun reportDelta() {
        val currentStats = cache.getStats()

        val hitsDelta = currentStats.hits - lastStats.hits
        val missesDelta = currentStats.misses - lastStats.misses
        val putsDelta = currentStats.putCount - lastStats.putCount

        println("Operations since last check:")
        println("  New hits: $hitsDelta")
        println("  New misses: $missesDelta")
        println("  New puts: $putsDelta")
        println("  Current hit rate: ${String.format("%.2f%%", currentStats.hitRate * 100)}")

        lastStats = currentStats
    }
}

// Usage
val cache = InMemoryCache<String>()
val monitor = CacheMonitor(cache)

// ... perform operations ...
monitor.reportDelta() // Reports delta since last check
```

### Production Monitoring Integration

Example integration with a metrics system:

```kotlin
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CacheMetricsReporter(
    private val cache: InMemoryCache<String>,
    private val reportIntervalSeconds: Long = 60
) {
    private val executor = Executors.newSingleThreadScheduledExecutor()

    fun start() {
        executor.scheduleAtFixedRate(
            { reportMetrics() },
            0,
            reportIntervalSeconds,
            TimeUnit.SECONDS
        )
    }

    fun stop() {
        executor.shutdown()
    }

    private fun reportMetrics() {
        val stats = cache.getStats()

        // Report to your metrics system (Prometheus, Datadog, etc.)
        recordGauge("cache.size", stats.size)
        recordGauge("cache.hit_rate", stats.hitRate)
        recordCounter("cache.hits", stats.hits)
        recordCounter("cache.misses", stats.misses)
        recordCounter("cache.evictions", stats.evictions)
        recordCounter("cache.puts", stats.putCount)
        recordCounter("cache.deletes", stats.deleteCount)

        // Log formatted output
        println(cache.getStatsFormatted())
    }

    private fun recordGauge(name: String, value: Long) {
        // Integration with your metrics system
    }

    private fun recordGauge(name: String, value: Double) {
        // Integration with your metrics system
    }

    private fun recordCounter(name: String, value: Long) {
        // Integration with your metrics system
    }
}

// Usage
val cache = InMemoryCache<String>()
val reporter = CacheMetricsReporter(cache, reportIntervalSeconds = 30)
reporter.start()
```

### Detecting Cache Issues

Use statistics to identify common cache problems:

```kotlin
fun analyzeCacheHealth(cache: InMemoryCache<String>) {
    val stats = cache.getStats()

    // Low hit rate warning
    if (stats.hitRate < 0.5 && stats.totalOperations > 1000) {
        println("WARNING: Hit rate is ${stats.hitRate * 100}% - consider increasing cache size")
    }

    // High eviction rate
    val evictionRate = stats.evictions.toDouble() / stats.putCount
    if (evictionRate > 0.3) {
        println("WARNING: ${evictionRate * 100}% of puts resulted in evictions")
        println("Consider increasing maxSize from current capacity")
    }

    // Frequent clears
    if (stats.clearCount > 10) {
        println("INFO: Cache has been cleared ${stats.clearCount} times")
        println("Consider if full invalidation is really necessary")
    }

    // Size vs capacity
    if (stats.size == 0L && stats.putCount > 0) {
        println("WARNING: Cache has entries but size is 0 - possible clear or expiration")
    }
}
```

## When to Use resetStats()

The `resetStats()` method clears all statistics counters and resets the `createdAt` timestamp. Use it in these scenarios:

### 1. After Warmup Periods

Reset statistics after cache warmup to get accurate production metrics:

```kotlin
val cache = InMemoryCache<String>()

// Warmup phase - preload frequently accessed data
preloadCache(cache)

// Reset stats to ignore warmup operations
cache.resetStats()

// Now monitor actual production traffic
startMonitoring(cache)
```

### 2. Between Test Phases

Reset statistics when running sequential test scenarios:

```kotlin
@Test
fun `test cache under different load patterns`() {
    val cache = InMemoryCache<String>()

    // Phase 1: Test read-heavy workload
    simulateReadHeavyLoad(cache)
    val readStats = cache.getStats()
    assertEquals(0.9, readStats.hitRate, 0.1)

    // Reset for next phase
    cache.resetStats()

    // Phase 2: Test write-heavy workload
    simulateWriteHeavyLoad(cache)
    val writeStats = cache.getStats()
    assertTrue(writeStats.putCount > writeStats.hits)
}
```

### 3. After Configuration Changes

Reset statistics when changing cache parameters:

```kotlin
val cache = InMemoryCache<String>(maxSize = 100)

// Run with initial config
runWorkload(cache)

// Change eviction policy or size (requires creating new cache)
val newCache = InMemoryCache<String>(
    maxSize = 200,
    evictionPolicy = EvictionPolicy.LRU
)

// Migrate data if needed, then reset stats
migrateCache(cache, newCache)
newCache.resetStats() // Start fresh metrics with new config
```

### 4. Periodic Statistics Reporting

Reset after collecting metrics to track incremental changes:

```kotlin
class PeriodicStatsReporter(private val cache: InMemoryCache<String>) {
    fun reportAndReset() {
        val stats = cache.getStats()

        // Send stats to monitoring system
        reportToMonitoring(stats)

        // Log the report
        println("Hourly report: ${cache.getStatsFormatted()}")

        // Reset for next period
        cache.resetStats()
    }
}

// Run every hour
val reporter = PeriodicStatsReporter(cache)
scheduler.scheduleAtFixedRate(
    { reporter.reportAndReset() },
    1, 1, TimeUnit.HOURS
)
```

### 5. Debugging and Profiling

Reset before specific operations to measure their impact:

```kotlin
// Measure impact of a specific operation
cache.resetStats()

performComplexOperation(cache)

val stats = cache.getStats()
println("Operation used ${stats.totalOperations} cache operations")
println("Hit rate during operation: ${stats.hitRate}")
```

### Important Notes About resetStats()

- **Cache contents are NOT affected** - Only counters are reset, stored entries remain
- **createdAt is updated** - Timestamp reflects when stats were reset, not original cache creation
- **Thread-safe** - Safe to call concurrently with other cache operations
- **Atomic operation** - All counters reset together, no partial states
- **Does NOT reset size** - Size reflects actual entries, not a counter
- **Evictions map cleared** - `evictionsByPolicy` becomes empty after reset

### When NOT to Use resetStats()

**Don't reset if you need:**
- Lifetime statistics for the cache
- Cumulative metrics for monitoring trends
- Historical data for capacity planning
- Debugging information from earlier operations

**Alternatives to resetStats():**
- Take snapshots of stats at intervals for delta calculations
- Use multiple cache instances for different workloads
- Implement your own metrics tracking that doesn't interfere with built-in stats
