package domain.replication

import domain.cache.Cache
import domain.cache.CacheKey
import domain.cache.CacheResult
import domain.cache.CacheValue
import domain.cache.WriteResult
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Simple in-process implementation of ReplicationCoordinator.
 *
 * This implementation coordinates replication across multiple cache instances
 * representing different nodes. In a production system, this would communicate
 * with remote nodes over the network.
 *
 * This implementation is primarily for testing and demonstration purposes.
 * A full production implementation would use the Netty network layer for
 * inter-node communication.
 *
 * @property localCache The local cache instance for this node
 * @property hashRing The consistent hash ring for replica selection
 * @property config The replication configuration
 * @property conflictResolver Strategy for resolving conflicting versions
 * @property nodeStores Map of node IDs to their cache instances (simulates remote nodes)
 */
class SimpleReplicationCoordinator<T>(
    private val localCache: Cache<T>,
    private val hashRing: ConsistentHashRing,
    private val config: ReplicationConfig = ReplicationConfig(),
    private val conflictResolver: ConflictResolver = LastWriteWinsResolver(),
    private val nodeStores: Map<String, Cache<T>> = emptyMap(),
) : ReplicationCoordinator<T> {
    override fun replicatedPut(
        key: CacheKey,
        value: CacheValue<T>,
        consistencyLevel: ConsistencyLevel,
        timeout: Duration,
    ): WriteResult {
        // Get replica nodes from the hash ring
        val replicaNodes = hashRing.getReplicaNodes(key, config.replicationFactor)

        if (replicaNodes.isEmpty()) {
            throw ReplicationException("No nodes available in the cluster")
        }

        val requiredResponses = consistencyLevel.requiredResponses(config.replicationFactor)

        // Execute writes to all replicas in parallel
        val writeFutures =
            replicaNodes.map { node ->
                CompletableFuture.supplyAsync {
                    try {
                        val nodeCache = nodeStores[node.id] ?: localCache
                        nodeCache.put(key, value)
                    } catch (e: Exception) {
                        WriteResult.Failure(e.message ?: "Write failed")
                    }
                }
            }

        // Wait for required number of successful responses
        val results =
            try {
                writeFutures
                    .take(requiredResponses)
                    .map { it.get(timeout.toMillis(), TimeUnit.MILLISECONDS) }
            } catch (e: Exception) {
                throw ReplicationException("Write timeout or failure", e)
            }

        // Count successful writes
        val successCount = results.count { it is WriteResult.Success }

        if (successCount < requiredResponses) {
            throw ReplicationException(
                "Could not satisfy consistency level $consistencyLevel: " +
                    "required $requiredResponses responses, got $successCount",
            )
        }

        // TODO: Implement hinted handoff for failed replicas if enabled
        // if (config.hintedHandoffEnabled) {
        //     storeHintsForFailedReplicas(...)
        // }

        return WriteResult.Success
    }

    override fun replicatedGet(
        key: CacheKey,
        consistencyLevel: ConsistencyLevel,
        timeout: Duration,
    ): CacheResult<T> {
        // Get replica nodes from the hash ring
        val replicaNodes = hashRing.getReplicaNodes(key, config.replicationFactor)

        if (replicaNodes.isEmpty()) {
            throw ReplicationException("No nodes available in the cluster")
        }

        val requiredResponses = consistencyLevel.requiredResponses(config.replicationFactor)

        // Execute reads from replicas in parallel
        val readFutures =
            replicaNodes.map { node ->
                CompletableFuture.supplyAsync {
                    try {
                        val nodeCache = nodeStores[node.id] ?: localCache
                        nodeCache.get(key)
                    } catch (e: Exception) {
                        CacheResult.Miss<T>()
                    }
                }
            }

        // Wait for required number of responses
        val results =
            try {
                readFutures
                    .take(requiredResponses)
                    .map { it.get(timeout.toMillis(), TimeUnit.MILLISECONDS) }
            } catch (e: Exception) {
                throw ReplicationException("Read timeout or failure", e)
            }

        // Extract successful reads (hits)
        val hits = results.filterIsInstance<CacheResult.Hit<T>>()

        if (hits.isEmpty()) {
            return CacheResult.Miss()
        }

        // If we got multiple hits, use conflict resolution to determine the winner
        val winningResult =
            if (hits.size == 1) {
                hits.first()
            } else {
                resolveConflicts(hits)
            }

        // TODO: Implement read repair if enabled and versions differ
        // if (config.readRepairEnabled && hits.size > 1) {
        //     performReadRepair(key, winningResult.value, replicaNodes)
        // }

        return winningResult
    }

    override fun replicatedDelete(
        key: CacheKey,
        consistencyLevel: ConsistencyLevel,
        timeout: Duration,
    ): WriteResult {
        // Get replica nodes from the hash ring
        val replicaNodes = hashRing.getReplicaNodes(key, config.replicationFactor)

        if (replicaNodes.isEmpty()) {
            throw ReplicationException("No nodes available in the cluster")
        }

        val requiredResponses = consistencyLevel.requiredResponses(config.replicationFactor)

        // Execute deletes on all replicas in parallel
        val deleteFutures =
            replicaNodes.map { node ->
                CompletableFuture.supplyAsync {
                    try {
                        val nodeCache = nodeStores[node.id] ?: localCache
                        nodeCache.delete(key)
                    } catch (e: Exception) {
                        WriteResult.Failure(e.message ?: "Delete failed")
                    }
                }
            }

        // Wait for required number of successful responses
        val results =
            try {
                deleteFutures
                    .take(requiredResponses)
                    .map { it.get(timeout.toMillis(), TimeUnit.MILLISECONDS) }
            } catch (e: Exception) {
                throw ReplicationException("Delete timeout or failure", e)
            }

        // Count successful deletes
        val successCount = results.count { it is WriteResult.Success }

        if (successCount < requiredResponses) {
            throw ReplicationException(
                "Could not satisfy consistency level $consistencyLevel: " +
                    "required $requiredResponses responses, got $successCount",
            )
        }

        return WriteResult.Success
    }

    override fun getConfig(): ReplicationConfig = config

    /**
     * Resolves conflicts between multiple versions of the same key.
     *
     * Uses the configured ConflictResolver to determine the winning version.
     *
     * @param hits List of cache hits with potentially different versions
     * @return The winning cache result
     */
    private fun resolveConflicts(hits: List<CacheResult.Hit<T>>): CacheResult.Hit<T> {
        // Use conflict resolver to determine the winning version
        val values = hits.map { it.value }
        val winningValue = conflictResolver.resolve(values)

        return hits.first { it.value == winningValue }
    }
}
