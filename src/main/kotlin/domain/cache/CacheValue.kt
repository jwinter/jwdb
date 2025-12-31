package domain.cache

import domain.replication.Version
import java.time.Instant

/**
 * Immutable cache value with metadata.
 *
 * This is the domain model for cached data. For network transmission and multi-language
 * client support, values will be serialized using Protocol Buffers in the infrastructure layer.
 *
 * @property data The actual data stored in the cache (will be serialized to protobuf for network transmission)
 * @property createdAt Timestamp when the entry was created
 * @property expiresAt Optional expiration timestamp (null means no expiration)
 * @property version Distributed version for conflict resolution (timestamp + nodeId)
 */
data class CacheValue<T>(
    val data: T,
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant? = null,
    val version: Version? = null,
) {
    /**
     * Checks if this cache value has expired based on the current time.
     */
    fun isExpired(now: Instant = Instant.now()): Boolean = expiresAt?.let { now.isAfter(it) } ?: false

    /**
     * Creates a new version of this cache value with updated data and new version.
     *
     * @param newData The new data to store
     * @param newVersion The version to assign (typically created with Version.now(nodeId))
     */
    fun withData(
        newData: T,
        newVersion: Version? = null,
    ): CacheValue<T> = copy(data = newData, version = newVersion, createdAt = Instant.now())

    /**
     * Creates a new version with a specific TTL (time-to-live) in seconds.
     */
    fun withTtl(ttlSeconds: Long): CacheValue<T> = copy(expiresAt = Instant.now().plusSeconds(ttlSeconds))
}
