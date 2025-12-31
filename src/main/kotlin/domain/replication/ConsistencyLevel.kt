package domain.replication

/**
 * Consistency levels for read and write operations in a replicated cache.
 *
 * Consistency levels allow tuning the trade-off between consistency, availability,
 * and latency on a per-operation basis.
 */
enum class ConsistencyLevel {
    /**
     * Operation succeeds after the first replica responds.
     *
     * - **Latency**: Lowest
     * - **Availability**: Highest
     * - **Consistency**: Weakest
     *
     * Suitable for use cases where speed is critical and eventual consistency is acceptable.
     */
    ONE,

    /**
     * Operation succeeds after a quorum (majority) of replicas respond.
     *
     * - **Latency**: Medium
     * - **Availability**: Medium
     * - **Consistency**: Strong (for RF >= 3)
     *
     * Quorum is calculated as: `floor(replicationFactor / 2) + 1`
     *
     * This is the recommended default for most use cases, providing a good balance
     * between consistency and availability.
     */
    QUORUM,

    /**
     * Operation succeeds only after all replicas respond.
     *
     * - **Latency**: Highest
     * - **Availability**: Lowest
     * - **Consistency**: Strongest
     *
     * The operation fails if any replica is unavailable. Suitable for critical data
     * where strongest consistency guarantees are required.
     */
    ALL,

    ;

    /**
     * Calculate the required number of replica responses for this consistency level.
     *
     * @param replicationFactor The total number of replicas for the data
     * @return The number of replicas that must respond for the operation to succeed
     * @throws IllegalArgumentException if replicationFactor is less than 1
     */
    fun requiredResponses(replicationFactor: Int): Int {
        require(replicationFactor >= 1) { "Replication factor must be at least 1" }

        return when (this) {
            ONE -> 1
            QUORUM -> (replicationFactor / 2) + 1
            ALL -> replicationFactor
        }
    }
}
