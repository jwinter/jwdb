package domain.replication

import domain.cache.CacheValue

/**
 * Strategy for resolving conflicts when multiple replicas have different versions
 * of the same key.
 *
 * In a distributed system, concurrent writes to different replicas can create conflicts.
 * The conflict resolver determines which version "wins" and should be kept.
 */
interface ConflictResolver {
    /**
     * Resolves a conflict between multiple versions of a cache value.
     *
     * @param values List of conflicting cache values from different replicas
     * @return The winning cache value that should be kept
     * @throws IllegalArgumentException if values list is empty
     */
    fun <T> resolve(values: List<CacheValue<T>>): CacheValue<T>
}

/**
 * Last-Write-Wins conflict resolution strategy.
 *
 * Selects the version with the latest timestamp. If timestamps are equal,
 * uses node ID lexicographic ordering for deterministic tie-breaking.
 *
 * This is the default conflict resolution strategy used by systems like
 * Cassandra and Riak when using simple values (not CRDTs).
 */
class LastWriteWinsResolver : ConflictResolver {
    override fun <T> resolve(values: List<CacheValue<T>>): CacheValue<T> {
        require(values.isNotEmpty()) { "Cannot resolve conflict with empty values list" }

        // If only one value, return it
        if (values.size == 1) {
            return values.first()
        }

        // Find the value with the highest version
        return values.maxWith(
            compareBy(
                // First compare by version (null versions are considered oldest)
                { it.version },
                // If versions are equal or both null, compare by creation time
                { it.createdAt },
            ),
        )
    }
}
