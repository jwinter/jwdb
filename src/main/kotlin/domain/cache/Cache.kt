package domain.cache

/**
 * Core cache interface defining fundamental operations.
 * This is the functional core - all methods are pure transformations
 * when combined with the current cache state.
 */
interface Cache<T> {
    /**
     * Retrieves a value from the cache.
     */
    fun get(key: CacheKey): CacheResult<T>

    /**
     * Stores a value in the cache.
     */
    fun put(
        key: CacheKey,
        value: CacheValue<T>,
    ): WriteResult

    /**
     * Removes a value from the cache.
     */
    fun delete(key: CacheKey): WriteResult

    /**
     * Checks if a key exists in the cache.
     */
    fun contains(key: CacheKey): Boolean

    /**
     * Clears all entries from the cache.
     */
    fun clear(): WriteResult

    /**
     * Returns the current number of entries in the cache.
     */
    fun size(): Long

    /**
     * Returns all keys currently in the cache.
     */
    fun keys(): Set<CacheKey>
}

/**
 * Statistics about cache performance.
 */
data class CacheStats(
    val hits: Long = 0L,
    val misses: Long = 0L,
    val evictions: Long = 0L,
    val size: Long = 0L,
    val putCount: Long = 0L,
    val deleteCount: Long = 0L,
    val clearCount: Long = 0L,
    val evictionsByPolicy: Map<EvictionPolicy, Long> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val hitRate: Double
        get() {
            val total = hits + misses
            return if (total > 0) hits.toDouble() / total else 0.0
        }

    val totalOperations: Long
        get() = hits + misses + putCount + deleteCount + clearCount
}
