package domain.cache

/**
 * Immutable value class representing a cache key.
 * Ensures type safety and provides consistent hashing.
 *
 * Keys are string-based to support:
 * - Easy serialization for network transmission (UTF-8 encoding)
 * - Multi-language client compatibility
 * - Flexible key patterns (e.g., "user:123", "session:abc-def", "product:SKU-456")
 * - Human-readable debugging
 */
@JvmInline
value class CacheKey(val value: String) {
    init {
        require(value.isNotEmpty()) { "Cache key cannot be empty" }
    }

    override fun toString(): String = value
}
