package domain.cache

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe in-memory cache implementation using ConcurrentHashMap.
 *
 * This implementation provides:
 * - Thread-safe concurrent access
 * - Automatic expiration checking
 * - Basic statistics tracking
 * - Optional maximum size limit
 *
 * @property maxSize Maximum number of entries (null for unlimited)
 * @property evictionPolicy Strategy for evicting entries when maxSize is reached
 */
class InMemoryCache<T>(
    private val maxSize: Long? = null,
    private val evictionPolicy: EvictionPolicy = EvictionPolicy.LRU,
) : Cache<T> {
    private val storage = ConcurrentHashMap<CacheKey, CacheValue<T>>()
    private val accessOrder = ConcurrentHashMap<CacheKey, AtomicLong>()
    private val accessCounter = AtomicLong(0)

    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val evictions = AtomicLong(0)

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
            WriteResult.Success
        } catch (e: Exception) {
            WriteResult.Failure("Failed to put value in cache", e)
        }
    }

    override fun delete(key: CacheKey): WriteResult {
        return try {
            storage.remove(key)
            accessOrder.remove(key)
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
        )

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
