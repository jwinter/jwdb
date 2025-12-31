package domain.replication

/**
 * Represents a version for conflict detection and resolution in a distributed system.
 * Uses a combination of timestamp and node ID for last-write-wins semantics.
 *
 * @property timestamp Logical timestamp (milliseconds since epoch)
 * @property nodeId Unique identifier of the node that performed the write
 */
data class Version(
    val timestamp: Long,
    val nodeId: String,
) : Comparable<Version> {
    /**
     * Compares this version to another for conflict resolution.
     * Uses last-write-wins (LWW) semantics:
     * 1. Later timestamp wins
     * 2. If timestamps equal, lexicographically greater nodeId wins (for determinism)
     *
     * @param other The version to compare against
     * @return Negative if this < other, positive if this > other, zero if equal
     */
    override fun compareTo(other: Version): Int {
        // First compare timestamps
        val timestampComparison = timestamp.compareTo(other.timestamp)
        if (timestampComparison != 0) {
            return timestampComparison
        }

        // If timestamps are equal, compare node IDs for deterministic ordering
        return nodeId.compareTo(other.nodeId)
    }

    /**
     * Returns true if this version is newer than the other version.
     */
    fun isNewerThan(other: Version): Boolean = this > other

    /**
     * Returns true if this version is older than the other version.
     */
    fun isOlderThan(other: Version): Boolean = this < other

    override fun toString(): String = "$timestamp@$nodeId"

    companion object {
        /**
         * Creates a new version with the current system timestamp.
         *
         * @param nodeId The ID of the node creating this version
         * @return New version with current timestamp
         */
        fun now(nodeId: String): Version =
            Version(
                timestamp = System.currentTimeMillis(),
                nodeId = nodeId,
            )

        /**
         * Returns the newer of two versions.
         */
        fun max(
            v1: Version,
            v2: Version,
        ): Version = if (v1 >= v2) v1 else v2

        /**
         * Returns the older of two versions.
         */
        fun min(
            v1: Version,
            v2: Version,
        ): Version = if (v1 <= v2) v1 else v2
    }
}
