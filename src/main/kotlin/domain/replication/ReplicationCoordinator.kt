package domain.replication

import domain.cache.CacheKey
import domain.cache.CacheResult
import domain.cache.CacheValue
import domain.cache.WriteResult
import java.time.Duration

/**
 * Coordinates replication of cache operations across multiple nodes.
 *
 * The coordinator is responsible for:
 * - Determining replica nodes using consistent hashing
 * - Coordinating reads and writes with configurable consistency levels
 * - Handling replica failures and timeouts
 * - Triggering read repair when versions differ
 * - Managing hinted handoff for unavailable replicas
 *
 * Any node in the cluster can act as a coordinator for any request.
 */
interface ReplicationCoordinator<T> {
    /**
     * Executes a replicated write operation.
     *
     * The coordinator:
     * 1. Determines replica nodes using the consistent hash ring
     * 2. Sends the write to all replica nodes
     * 3. Waits for the required number of acknowledgments based on consistency level
     * 4. Returns success if consistency level is satisfied
     * 5. Stores hints for any unavailable replicas (hinted handoff)
     *
     * @param key The cache key to write
     * @param value The value to write (including version information)
     * @param consistencyLevel The required consistency level (ONE, QUORUM, or ALL)
     * @param timeout Maximum time to wait for replica responses
     * @return WriteResult indicating success or failure
     * @throws ReplicationException if the required consistency level cannot be satisfied
     */
    fun replicatedPut(
        key: CacheKey,
        value: CacheValue<T>,
        consistencyLevel: ConsistencyLevel = ConsistencyLevel.QUORUM,
        timeout: Duration = Duration.ofSeconds(1),
    ): WriteResult

    /**
     * Executes a replicated read operation.
     *
     * The coordinator:
     * 1. Determines replica nodes using the consistent hash ring
     * 2. Queries the required number of replicas based on consistency level
     * 3. Compares versions if multiple responses are received
     * 4. Returns the value with the latest version
     * 5. Triggers asynchronous read repair if versions differ
     *
     * @param key The cache key to read
     * @param consistencyLevel The required consistency level (ONE, QUORUM, or ALL)
     * @param timeout Maximum time to wait for replica responses
     * @return CacheResult containing the value with the latest version, or a miss
     * @throws ReplicationException if the required consistency level cannot be satisfied
     */
    fun replicatedGet(
        key: CacheKey,
        consistencyLevel: ConsistencyLevel = ConsistencyLevel.QUORUM,
        timeout: Duration = Duration.ofSeconds(1),
    ): CacheResult<T>

    /**
     * Executes a replicated delete operation.
     *
     * Deletes are handled similarly to writes, with tombstone markers used to
     * ensure eventual consistency across replicas.
     *
     * @param key The cache key to delete
     * @param consistencyLevel The required consistency level (ONE, QUORUM, or ALL)
     * @param timeout Maximum time to wait for replica responses
     * @return WriteResult indicating success or failure
     * @throws ReplicationException if the required consistency level cannot be satisfied
     */
    fun replicatedDelete(
        key: CacheKey,
        consistencyLevel: ConsistencyLevel = ConsistencyLevel.QUORUM,
        timeout: Duration = Duration.ofSeconds(1),
    ): WriteResult

    /**
     * Retrieves the current replication configuration.
     *
     * @return The replication configuration including replication factor and default consistency
     */
    fun getConfig(): ReplicationConfig
}

/**
 * Configuration for replication behavior.
 *
 * @property replicationFactor Number of replicas to maintain for each key (default: 3)
 * @property defaultReadConsistency Default consistency level for reads (default: QUORUM)
 * @property defaultWriteConsistency Default consistency level for writes (default: QUORUM)
 * @property hintedHandoffEnabled Whether to use hinted handoff for unavailable replicas (default: true)
 * @property readRepairEnabled Whether to perform read repair when versions differ (default: true)
 */
data class ReplicationConfig(
    val replicationFactor: Int = 3,
    val defaultReadConsistency: ConsistencyLevel = ConsistencyLevel.QUORUM,
    val defaultWriteConsistency: ConsistencyLevel = ConsistencyLevel.QUORUM,
    val hintedHandoffEnabled: Boolean = true,
    val readRepairEnabled: Boolean = true,
) {
    init {
        require(replicationFactor >= 1) { "Replication factor must be at least 1" }
    }

    /**
     * Calculates the quorum size for this replication factor.
     * Quorum = floor(RF / 2) + 1
     */
    val quorumSize: Int
        get() = (replicationFactor / 2) + 1
}

/**
 * Exception thrown when replication operations fail.
 */
class ReplicationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
