# Cache TTL and Automatic Cleanup Guide

This guide covers the automatic Time-To-Live (TTL) cleanup feature in jwdb's cache implementation.

## Overview

The cache provides automatic background cleanup of expired entries to prevent memory exhaustion and maintain optimal performance. Without automatic cleanup, expired entries would remain in memory until they are accessed (lazy expiration), which could lead to unbounded memory growth.

## Automatic Cleanup Behavior

### How It Works

When automatic cleanup is enabled (the default):

1. **Background Task**: A daemon thread runs periodically to scan for and remove expired entries
2. **Configurable Interval**: You can control how often cleanup runs (default: 60 seconds)
3. **Metrics Tracking**: All cleanup operations are tracked in cache statistics
4. **Error Handling**: Cleanup errors are logged but don't crash the background task
5. **Graceful Shutdown**: The cleanup task can be stopped cleanly when the cache is no longer needed

### Default Configuration

```kotlin
val cache = InMemoryCache<String>()

// This is equivalent to:
val cache = InMemoryCache<String>(
    enableAutoCleanup = true,           // Automatic cleanup enabled
    cleanupIntervalSeconds = 60,        // Cleanup runs every 60 seconds
)
```

## Configuration Options

### Enable/Disable Automatic Cleanup

**Enabled (Default)**
```kotlin
val cache = InMemoryCache<String>(
    enableAutoCleanup = true
)
```

Use automatic cleanup when:
- Your cache handles entries with TTL/expiration
- You want to prevent memory growth from expired entries
- You're running a long-lived cache in production

**Disabled**
```kotlin
val cache = InMemoryCache<String>(
    enableAutoCleanup = false
)
```

Disable automatic cleanup when:
- You don't use TTL/expiration features
- You want complete control over when cleanup happens
- You're running short-lived caches (tests, temporary operations)
- You want to minimize background thread usage

### Cleanup Interval Configuration

```kotlin
// Aggressive cleanup - every 10 seconds
val cache = InMemoryCache<String>(
    enableAutoCleanup = true,
    cleanupIntervalSeconds = 10
)

// Conservative cleanup - every 5 minutes
val cache = InMemoryCache<String>(
    enableAutoCleanup = true,
    cleanupIntervalSeconds = 300
)

// Default cleanup - every 60 seconds
val cache = InMemoryCache<String>(
    enableAutoCleanup = true,
    cleanupIntervalSeconds = 60
)
```

**Choosing the Right Interval:**

| Interval | Use Case | Trade-offs |
|----------|----------|------------|
| 10-30s | High TTL churn, memory-sensitive applications | More CPU usage, fresher memory footprint |
| 60s (default) | General purpose, balanced | Good balance of CPU vs memory |
| 120-300s | Low TTL usage, CPU-sensitive | Less CPU overhead, higher temporary memory usage |
| 600s+ | Rare expiration, batch processing | Minimal CPU impact, potential memory buildup |

## Examples

### Basic Usage with TTL

```kotlin
import java.time.Duration
import java.time.Instant

val cache = InMemoryCache<String>()

// Add entry that expires in 5 minutes
cache.put(
    CacheKey("session:123"),
    CacheValue(
        "user_data",
        expiresAt = Instant.now().plus(Duration.ofMinutes(5))
    )
)

// Entry will be automatically removed ~1 minute after expiration
// (cleanup runs every 60 seconds by default)
```

### Custom Cleanup Interval

```kotlin
// For a session cache with 1-minute TTLs, use faster cleanup
val sessionCache = InMemoryCache<String>(
    enableAutoCleanup = true,
    cleanupIntervalSeconds = 15  // Check every 15 seconds
)

// Add short-lived session
sessionCache.put(
    CacheKey("temp:session"),
    CacheValue(
        "session_data",
        expiresAt = Instant.now().plus(Duration.ofMinutes(1))
    )
)

// Session will be cleaned up within 15 seconds of expiration
```

### Manual Cleanup with Automatic Cleanup Disabled

```kotlin
val cache = InMemoryCache<String>(
    enableAutoCleanup = false  // No background cleanup
)

// Add expired entry
cache.put(
    CacheKey("key1"),
    CacheValue(
        "value1",
        expiresAt = Instant.now().minus(Duration.ofHours(1))
    )
)

// Manually trigger cleanup when needed
val removedCount = cache.removeExpired()
println("Manually removed $removedCount expired entries")
```

### Hybrid Approach: Automatic + Manual Cleanup

```kotlin
// Enable automatic cleanup with long interval
val cache = InMemoryCache<String>(
    enableAutoCleanup = true,
    cleanupIntervalSeconds = 300  // Every 5 minutes
)

// Perform operations...
cache.put(CacheKey("key1"), CacheValue("value1", expiresAt = ...))

// Manually trigger cleanup before a memory-intensive operation
val removed = cache.removeExpired()
println("Freed up $removed entries before batch job")

// Automatic cleanup continues in the background
```

## Lifecycle Management

### Shutdown

Always call `shutdown()` when you're done with a cache that has automatic cleanup enabled:

```kotlin
val cache = InMemoryCache<String>(
    enableAutoCleanup = true
)

try {
    // Use the cache...
    cache.put(CacheKey("key"), CacheValue("value"))

    // ... your application logic ...

} finally {
    // Stop the background cleanup task
    cache.shutdown()
}
```

**What shutdown() does:**
1. Stops the background cleanup scheduler
2. Waits up to 5 seconds for current cleanup to finish
3. Force-stops if it doesn't finish in time
4. Releases thread resources

**Important Notes:**
- `shutdown()` is safe to call multiple times
- `shutdown()` doesn't clear cache contents, only stops the cleanup task
- Not calling `shutdown()` can leak threads (daemon threads, but still not ideal)
- For caches with `enableAutoCleanup = false`, `shutdown()` is a no-op but safe to call

### Application-Wide Cache Management

```kotlin
class CacheManager(private val cleanupInterval: Long = 60) {
    private val caches = mutableListOf<InMemoryCache<*>>()

    fun <T> createCache(): InMemoryCache<T> {
        val cache = InMemoryCache<T>(
            enableAutoCleanup = true,
            cleanupIntervalSeconds = cleanupInterval
        )
        caches.add(cache)
        return cache
    }

    fun shutdownAll() {
        caches.forEach { it.shutdown() }
        caches.clear()
    }
}

// Usage
val manager = CacheManager(cleanupInterval = 30)

val sessionCache = manager.createCache<String>()
val dataCache = manager.createCache<ByteArray>()

// ... use caches ...

// Shutdown all caches at once (e.g., on application shutdown)
Runtime.getRuntime().addShutdownHook(Thread {
    manager.shutdownAll()
})
```

## Monitoring Cleanup Operations

### Cleanup Metrics

The cache tracks three cleanup-related metrics:

```kotlin
val cache = InMemoryCache<String>(
    enableAutoCleanup = true,
    cleanupIntervalSeconds = 60
)

// Add some entries with TTL...

// Check cleanup statistics
val stats = cache.getStats()

println("Expired entries removed: ${stats.expiredEntriesRemoved}")  // Total lifetime count
println("Cleanup cycles run: ${stats.cleanupCount}")                // Number of times cleanup ran
println("Last cleanup: ${stats.lastCleanupTime}")                   // Timestamp of last cleanup
```

### Formatted Output

```kotlin
val cache = InMemoryCache<String>(enableAutoCleanup = true)

// ... use cache ...

println(cache.getStatsFormatted())
```

Example output:
```
Cache Statistics:
  Size: 1,234 entries
  Hits: 5,678
  Misses: 123
  Hit Rate: 97.88%
  Operations:
    Puts: 2,345
    Deletes: 456
    Clears: 0
    Total: 8,602
  Evictions: 789
  TTL Cleanup:
    Expired Entries Removed: 1,011
    Cleanup Cycles: 42
    Last Cleanup: 2025-12-29T10:15:30Z
  Uptime: 3600s
```

### Monitoring in Production

```kotlin
class CacheHealthMonitor(
    private val cache: InMemoryCache<String>,
    private val alertThreshold: Long = 1000
) {
    private var lastExpiredCount = 0L

    fun checkHealth() {
        val stats = cache.getStats()

        // Alert if too many expired entries in one interval
        val newExpired = stats.expiredEntriesRemoved - lastExpiredCount
        if (newExpired > alertThreshold) {
            println("WARNING: ${newExpired} entries expired since last check")
            println("Consider shorter TTLs or larger cache size")
        }

        // Alert if cleanup hasn't run recently
        stats.lastCleanupTime?.let { lastCleanup ->
            val timeSinceCleanup = System.currentTimeMillis() - lastCleanup
            val expectedInterval = 60 * 1000 * 2  // 2x the cleanup interval

            if (timeSinceCleanup > expectedInterval) {
                println("WARNING: Cleanup hasn't run in ${timeSinceCleanup / 1000}s")
                println("Background task may have stalled")
            }
        }

        lastExpiredCount = stats.expiredEntriesRemoved
    }
}

// Usage
val cache = InMemoryCache<String>(enableAutoCleanup = true)
val monitor = CacheHealthMonitor(cache)

// Check health every minute
scheduler.scheduleAtFixedRate({ monitor.checkHealth() }, 1, 1, TimeUnit.MINUTES)
```

## Performance Considerations

### CPU Impact

**Cleanup Cost:**
- Each cleanup cycle scans all entries in the cache
- Time complexity: O(n) where n is the number of entries
- CPU impact depends on cache size and cleanup frequency

**Optimization Tips:**
1. **Tune cleanup interval**: Balance between memory freshness and CPU usage
2. **Monitor cleanup duration**: Track how long each cleanup takes
3. **Off-peak cleanup**: For batch systems, run cleanup during low-traffic periods

```kotlin
// For very large caches, use longer intervals
val largeCache = InMemoryCache<String>(
    maxSize = 1_000_000,
    enableAutoCleanup = true,
    cleanupIntervalSeconds = 300  // 5 minutes to reduce scan frequency
)
```

### Memory Impact

**Without Automatic Cleanup:**
- Expired entries remain in memory until accessed
- Memory grows unbounded with expiring entries
- Eventually leads to OutOfMemoryError

**With Automatic Cleanup:**
- Memory freed regularly based on cleanup interval
- Worst-case memory: size of cache + entries expired in one interval
- Predictable memory footprint

**Memory Calculation:**
```
Max Memory = (Active Entries) + (Entries Expired in Cleanup Interval)

Example with 60s cleanup interval:
- 10,000 active entries
- 100 entries/sec with 30s TTL
- Max memory = 10,000 + (100 * 60) = 16,000 entries
```

### Thread Usage

**Background Cleanup Thread:**
- One daemon thread per cache instance
- Thread sleeps between cleanup cycles (minimal CPU when idle)
- Thread is named "cache-cleanup-thread" for easy identification

**Thread Pool Sizing:**
```kotlin
// If you have many cache instances, consider disabling auto-cleanup
// and using a shared cleanup scheduler

class SharedCleanupManager {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val caches = mutableListOf<InMemoryCache<*>>()

    fun <T> registerCache(cache: InMemoryCache<T>) {
        caches.add(cache)
    }

    fun startCleanup(intervalSeconds: Long) {
        executor.scheduleAtFixedRate({
            caches.forEach { it.removeExpired() }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS)
    }

    fun shutdown() {
        executor.shutdown()
    }
}

// Usage: One thread for all caches
val cleanupManager = SharedCleanupManager()

val cache1 = InMemoryCache<String>(enableAutoCleanup = false)
val cache2 = InMemoryCache<ByteArray>(enableAutoCleanup = false)

cleanupManager.registerCache(cache1)
cleanupManager.registerCache(cache2)
cleanupManager.startCleanup(60)
```

## Common Patterns

### Pattern 1: Session Cache

```kotlin
// Short TTLs, frequent cleanup
val sessionCache = InMemoryCache<String>(
    maxSize = 10_000,
    enableAutoCleanup = true,
    cleanupIntervalSeconds = 30  // Clean up every 30 seconds
)

// Add session with 30-minute TTL
fun createSession(sessionId: String, userData: String) {
    sessionCache.put(
        CacheKey("session:$sessionId"),
        CacheValue(
            userData,
            expiresAt = Instant.now().plus(Duration.ofMinutes(30))
        )
    )
}
```

### Pattern 2: API Response Cache

```kotlin
// Medium TTLs, standard cleanup
val apiCache = InMemoryCache<String>(
    maxSize = 50_000,
    enableAutoCleanup = true,
    cleanupIntervalSeconds = 60  // Standard interval
)

// Cache API responses with 5-minute TTL
fun cacheApiResponse(endpoint: String, response: String) {
    apiCache.put(
        CacheKey("api:$endpoint"),
        CacheValue(
            response,
            expiresAt = Instant.now().plus(Duration.ofMinutes(5))
        )
    )
}
```

### Pattern 3: Long-Lived Cache

```kotlin
// Long TTLs, infrequent cleanup
val referenceDataCache = InMemoryCache<String>(
    maxSize = 100_000,
    enableAutoCleanup = true,
    cleanupIntervalSeconds = 600  // Clean up every 10 minutes
)

// Cache reference data with 24-hour TTL
fun cacheReferenceData(key: String, data: String) {
    referenceDataCache.put(
        CacheKey("ref:$key"),
        CacheValue(
            data,
            expiresAt = Instant.now().plus(Duration.ofHours(24))
        )
    )
}
```

### Pattern 4: Test Cache (No Cleanup)

```kotlin
@Test
fun `test cache behavior`() {
    val cache = InMemoryCache<String>(
        enableAutoCleanup = false  // No background threads in tests
    )

    try {
        // Add entry
        cache.put(
            CacheKey("key"),
            CacheValue(
                "value",
                expiresAt = Instant.now().plus(Duration.ofSeconds(1))
            )
        )

        // Wait for expiration
        Thread.sleep(1500)

        // Manually trigger cleanup for deterministic testing
        val removed = cache.removeExpired()
        assertEquals(1, removed)
    } finally {
        cache.shutdown()  // No-op for disabled cleanup, but good practice
    }
}
```

## Troubleshooting

### Issue: Memory Still Growing

**Symptoms:** Memory usage increases despite automatic cleanup

**Possible Causes:**
1. Cleanup interval too long for your TTL pattern
2. More entries added than expire in each interval
3. No TTL set on entries (they never expire)

**Solutions:**
```kotlin
// Reduce cleanup interval
val cache = InMemoryCache<String>(
    enableAutoCleanup = true,
    cleanupIntervalSeconds = 30  // Shorter interval
)

// Or set maxSize to enforce bounds
val cache = InMemoryCache<String>(
    maxSize = 10_000,  // Hard limit
    enableAutoCleanup = true
)

// Verify TTL is being set
cache.put(
    key,
    CacheValue(data, expiresAt = Instant.now().plus(Duration.ofMinutes(5)))  // Don't forget expiration!
)
```

### Issue: High CPU Usage

**Symptoms:** CPU constantly high even when cache is idle

**Possible Causes:**
1. Cleanup interval too short
2. Cache is very large (cleanup scans all entries)

**Solutions:**
```kotlin
// Increase cleanup interval
val cache = InMemoryCache<String>(
    enableAutoCleanup = true,
    cleanupIntervalSeconds = 300  // Less frequent cleanup
)

// Or disable auto-cleanup and use manual cleanup
val cache = InMemoryCache<String>(
    enableAutoCleanup = false
)

// Trigger cleanup only when needed
scheduler.scheduleAtFixedRate({
    cache.removeExpired()
}, 5, 5, TimeUnit.MINUTES)
```

### Issue: Threads Not Cleaned Up

**Symptoms:** Thread count grows, "cache-cleanup-thread" threads accumulate

**Cause:** Not calling `shutdown()` on caches

**Solution:**
```kotlin
// Always use try-finally or use-pattern
val cache = InMemoryCache<String>(enableAutoCleanup = true)
try {
    // Use cache
} finally {
    cache.shutdown()  // Critical!
}

// Or add shutdown hook for long-lived caches
Runtime.getRuntime().addShutdownHook(Thread {
    cache.shutdown()
})
```

## FAQ

**Q: What happens if I don't call shutdown()?**
A: The cleanup thread will continue running as a daemon thread. It won't prevent JVM shutdown, but it's a resource leak and not recommended.

**Q: Can I change cleanup settings after creating a cache?**
A: No, cleanup settings are immutable. Create a new cache instance if you need different settings.

**Q: Does cleanup affect cache performance?**
A: Minimal impact. Cleanup runs in a background thread and uses `removeIf()` which is efficient. Main operations (get/put) are not blocked.

**Q: What's the difference between automatic cleanup and manual removeExpired()?**
A: Automatic cleanup runs `removeExpired()` periodically in the background. Manual cleanup gives you full control over when it runs. They track different metrics.

**Q: Should I use automatic cleanup in tests?**
A: Generally no. Tests should be deterministic. Use `enableAutoCleanup = false` and call `removeExpired()` manually when needed.

**Q: Can cleanup remove entries while they're being accessed?**
A: No. ConcurrentHashMap ensures thread-safe removal. Active reads/writes won't be interrupted.

**Q: How do I monitor if cleanup is keeping up with expiration?**
A: Compare `expiredEntriesRemoved` growth rate to your put rate. If they're similar and memory is stable, cleanup is keeping up.
