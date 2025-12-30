package domain.cache

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe in-memory cache implementation using ConcurrentHashMap.
 *
 * This implementation provides:
 * - Thread-safe concurrent access
 * - Automatic expiration checking
 * - Basic statistics tracking
 * - Optional maximum size limit
 * - Automatic background cleanup of expired entries
 *
 * @property maxSize Maximum number of entries (null for unlimited)
 * @property evictionPolicy Strategy for evicting entries when maxSize is reached
 * @property enableAutoCleanup Whether to enable automatic background cleanup of expired entries
 * @property cleanupIntervalSeconds Interval in seconds between cleanup runs (default: 60)
 */
class InMemoryCache<T>(
    private val maxSize: Long? = null,
    private val evictionPolicy: EvictionPolicy = EvictionPolicy.LRU,
    private val enableAutoCleanup: Boolean = true,
    private val cleanupIntervalSeconds: Long = 60,
) : Cache<T> {
    private val storage = ConcurrentHashMap<CacheKey, CacheValue<T>>()
    private val accessOrder = ConcurrentHashMap<CacheKey, AtomicLong>()
    private val accessCounter = AtomicLong(0)

    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val evictions = AtomicLong(0)
    private val putCount = AtomicLong(0)
    private val deleteCount = AtomicLong(0)
    private val clearCount = AtomicLong(0)
    private val evictionsByPolicy = ConcurrentHashMap<EvictionPolicy, AtomicLong>()
    private val createdAt = System.currentTimeMillis()
    private val expiredEntriesRemoved = AtomicLong(0)
    private val cleanupCount = AtomicLong(0)

    @Volatile
    private var lastCleanupTime: Long? = null

    private var cleanupExecutor: ScheduledExecutorService? = null

    init {
        if (enableAutoCleanup) {
            startBackgroundCleanup()
        }
    }

    override fun get(key: CacheKey): CacheResult<T> {
        val value = storage[key]

        return when {
            value == null -> {
                misses.incrementAndGet()
                CacheResult.Miss()
            }
            value.isExpired() -> {
                storage.remove(key)
                accessOrder.remove(key)
                misses.incrementAndGet()
                CacheResult.Miss()
            }
            else -> {
                hits.incrementAndGet()
                updateAccessOrder(key)
                CacheResult.Hit(value)
            }
        }
    }

    override fun put(
        key: CacheKey,
        value: CacheValue<T>,
    ): WriteResult {
        return try {
            // Check if we need to evict before adding
            if (maxSize != null && !storage.containsKey(key) && storage.size >= maxSize) {
                evictOne()
            }

            storage[key] = value
            updateAccessOrder(key)
            putCount.incrementAndGet()
            WriteResult.Success
        } catch (e: Exception) {
            WriteResult.Failure("Failed to put value in cache", e)
        }
    }

    override fun delete(key: CacheKey): WriteResult {
        return try {
            storage.remove(key)
            accessOrder.remove(key)
            deleteCount.incrementAndGet()
            WriteResult.Success
        } catch (e: Exception) {
            WriteResult.Failure("Failed to delete value from cache", e)
        }
    }

    override fun contains(key: CacheKey): Boolean {
        val value = storage[key]
        return value != null && !value.isExpired()
    }

    override fun clear(): WriteResult {
        return try {
            storage.clear()
            accessOrder.clear()
            clearCount.incrementAndGet()
            WriteResult.Success
        } catch (e: Exception) {
            WriteResult.Failure("Failed to clear cache", e)
        }
    }

    override fun size(): Long = storage.size.toLong()

    override fun keys(): Set<CacheKey> = storage.keys.toSet()

    /**
     * Gets current cache statistics.
     */
    fun getStats(): CacheStats =
        CacheStats(
            hits = hits.get(),
            misses = misses.get(),
            evictions = evictions.get(),
            size = storage.size.toLong(),
            putCount = putCount.get(),
            deleteCount = deleteCount.get(),
            clearCount = clearCount.get(),
            evictionsByPolicy = evictionsByPolicy.mapValues { it.value.get() },
            createdAt = createdAt,
            expiredEntriesRemoved = expiredEntriesRemoved.get(),
            lastCleanupTime = lastCleanupTime,
            cleanupCount = cleanupCount.get(),
        )

    /**
     * Resets all statistics counters to zero.
     * Cache contents are not affected.
     */
    fun resetStats() {
        hits.set(0)
        misses.set(0)
        evictions.set(0)
        putCount.set(0)
        deleteCount.set(0)
        clearCount.set(0)
        evictionsByPolicy.clear()
        expiredEntriesRemoved.set(0)
        cleanupCount.set(0)
        lastCleanupTime = null
    }

    /**
     * Gets formatted statistics output for logging/monitoring.
     */
    fun getStatsFormatted(): String {
        val stats = getStats()
        val uptime = System.currentTimeMillis() - stats.createdAt
        val uptimeSeconds = uptime / 1000

        return buildString {
            appendLine("Cache Statistics:")
            appendLine("  Size: ${String.format("%,d", stats.size)} entries")
            appendLine("  Hits: ${String.format("%,d", stats.hits)}")
            appendLine("  Misses: ${String.format("%,d", stats.misses)}")
            appendLine("  Hit Rate: ${String.format("%.2f%%", stats.hitRate * 100)}")
            appendLine("  Operations:")
            appendLine("    Puts: ${String.format("%,d", stats.putCount)}")
            appendLine("    Deletes: ${String.format("%,d", stats.deleteCount)}")
            appendLine("    Clears: ${String.format("%,d", stats.clearCount)}")
            appendLine("    Total: ${String.format("%,d", stats.totalOperations)}")
            appendLine("  Evictions: ${String.format("%,d", stats.evictions)}")
            if (stats.evictionsByPolicy.isNotEmpty()) {
                appendLine("    By Policy:")
                stats.evictionsByPolicy.forEach { (policy, count) ->
                    appendLine("      $policy: ${String.format("%,d", count)}")
                }
            }
            if (stats.cleanupCount > 0 || stats.expiredEntriesRemoved > 0) {
                appendLine("  TTL Cleanup:")
                appendLine("    Expired Entries Removed: ${String.format("%,d", stats.expiredEntriesRemoved)}")
                appendLine("    Cleanup Cycles: ${String.format("%,d", stats.cleanupCount)}")
                stats.lastCleanupTime?.let {
                    val timeSinceCleanup = (System.currentTimeMillis() - it) / 1000
                    appendLine("    Last Cleanup: ${timeSinceCleanup}s ago")
                }
            }
            append("  Uptime: ${uptimeSeconds}s")
        }
    }

    /**
     * Removes expired entries from the cache.
     * Returns the number of entries removed.
     */
    fun removeExpired(): Int {
        val now = Instant.now()
        var removed = 0

        storage.entries.removeIf { (key, value) ->
            if (value.isExpired(now)) {
                accessOrder.remove(key)
                removed++
                true
            } else {
                false
            }
        }

        return removed
    }

    /**
     * Starts the background cleanup task.
     * Called automatically if enableAutoCleanup is true.
     */
    private fun startBackgroundCleanup() {
        cleanupExecutor =
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "cache-cleanup-thread").apply {
                    isDaemon = true
                }
            }

        cleanupExecutor?.scheduleAtFixedRate(
            {
                try {
                    runCleanup()
                } catch (e: Exception) {
                    // Log error but don't let it stop the scheduled task
                    System.err.println("Error during cache cleanup: ${e.message}")
                }
            },
            cleanupIntervalSeconds,
            cleanupIntervalSeconds,
            TimeUnit.SECONDS,
        )
    }

    /**
     * Runs a single cleanup cycle.
     */
    private fun runCleanup() {
        val removed = removeExpired()
        expiredEntriesRemoved.addAndGet(removed.toLong())
        cleanupCount.incrementAndGet()
        lastCleanupTime = System.currentTimeMillis()
    }

    /**
     * Stops the background cleanup task and releases resources.
     * Should be called when the cache is no longer needed.
     */
    fun shutdown() {
        cleanupExecutor?.shutdown()
        try {
            if (cleanupExecutor?.awaitTermination(5, TimeUnit.SECONDS) == false) {
                cleanupExecutor?.shutdownNow()
            }
        } catch (e: InterruptedException) {
            cleanupExecutor?.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    private fun updateAccessOrder(key: CacheKey) {
        if (evictionPolicy == EvictionPolicy.LRU) {
            accessOrder.computeIfAbsent(key) { AtomicLong(0) }
                .set(accessCounter.incrementAndGet())
        }
    }

    private fun evictOne() {
        when (evictionPolicy) {
            EvictionPolicy.LRU -> evictLRU()
            EvictionPolicy.FIFO -> evictFIFO()
            EvictionPolicy.RANDOM -> evictRandom()
        }
        evictions.incrementAndGet()
        evictionsByPolicy.computeIfAbsent(evictionPolicy) { AtomicLong(0) }.incrementAndGet()
    }

    private fun evictLRU() {
        val lruKey = accessOrder.entries.minByOrNull { it.value.get() }?.key
        lruKey?.let {
            storage.remove(it)
            accessOrder.remove(it)
        }
    }

    private fun evictFIFO() {
        // For FIFO, we evict the first entry (oldest)
        storage.keys.firstOrNull()?.let { key ->
            storage.remove(key)
            accessOrder.remove(key)
        }
    }

    private fun evictRandom() {
        storage.keys.randomOrNull()?.let { key ->
            storage.remove(key)
            accessOrder.remove(key)
        }
    }
}

/**
 * Eviction policies for cache entries when maxSize is reached.
 */
enum class EvictionPolicy {
    /** Least Recently Used - evicts the entry that was accessed least recently */
    LRU,

    /** First In First Out - evicts the oldest entry */
    FIFO,

    /** Random eviction */
    RANDOM,
}
