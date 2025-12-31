package domain.replication

import domain.cache.CacheKey
import java.security.MessageDigest
import java.util.TreeMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A virtual node in the consistent hash ring.
 * Each physical node is assigned multiple virtual nodes for better load distribution.
 *
 * @property physicalNode The actual node this virtual node represents
 * @property vNodeIndex The index of this virtual node (0 to vNodesPerNode-1)
 */
data class VirtualNode(
    val physicalNode: Node,
    val vNodeIndex: Int,
) {
    /**
     * Unique identifier for this virtual node
     */
    val id: String = "${physicalNode.id}#$vNodeIndex"
}

/**
 * Consistent hash ring implementation for distributing data across cluster nodes.
 * Uses virtual nodes (vnodes) to improve load balancing and minimize data movement
 * when nodes join or leave the cluster.
 *
 * This implementation is thread-safe and supports concurrent reads with exclusive writes.
 *
 * @property vNodesPerNode Number of virtual nodes to create per physical node (default: 256)
 */
class ConsistentHashRing(
    private val vNodesPerNode: Int = 256,
) {
    // Ring is stored as a sorted map of hash values to virtual nodes
    // TreeMap ensures O(log n) lookups and maintains sorted order
    private val ring = TreeMap<Long, VirtualNode>()

    // Maps physical node IDs to their set of virtual nodes for easy removal
    private val nodeToVirtualNodes = mutableMapOf<String, MutableSet<VirtualNode>>()

    // Thread-safe access using read-write lock
    // Multiple readers can access concurrently, but writes are exclusive
    private val lock = ReentrantReadWriteLock()

    // MD5 hasher for consistent hashing (thread-local to avoid synchronization)
    private val md5ThreadLocal =
        ThreadLocal.withInitial {
            MessageDigest.getInstance("MD5")
        }

    /**
     * Adds a node to the ring.
     * Creates vNodesPerNode virtual nodes for the physical node.
     *
     * @param node The physical node to add
     */
    fun addNode(node: Node) {
        lock.write {
            // Skip if node is already in the ring
            if (nodeToVirtualNodes.containsKey(node.id)) {
                return@write
            }

            val virtualNodes = mutableSetOf<VirtualNode>()

            // Create virtual nodes for this physical node
            repeat(vNodesPerNode) { vNodeIndex ->
                val vNode = VirtualNode(node, vNodeIndex)
                val hash = hash(vNode.id)
                ring[hash] = vNode
                virtualNodes.add(vNode)
            }

            nodeToVirtualNodes[node.id] = virtualNodes
        }
    }

    /**
     * Removes a node from the ring.
     * Removes all virtual nodes associated with the physical node.
     *
     * @param nodeId The ID of the node to remove
     */
    fun removeNode(nodeId: String) {
        lock.write {
            val virtualNodes = nodeToVirtualNodes.remove(nodeId) ?: return@write

            // Remove all virtual nodes from the ring
            virtualNodes.forEach { vNode ->
                val hash = hash(vNode.id)
                ring.remove(hash)
            }
        }
    }

    /**
     * Gets the primary node responsible for a given key.
     *
     * @param key The cache key to locate
     * @return The node responsible for the key, or null if ring is empty
     */
    fun getNode(key: CacheKey): Node? =
        lock.read {
            if (ring.isEmpty()) return null

            val hash = hash(key.value)

            // Find the first virtual node with hash >= key hash (clockwise on ring)
            val entry = ring.ceilingEntry(hash) ?: ring.firstEntry()
            return entry.value.physicalNode
        }

    /**
     * Gets N replica nodes for a given key.
     * Returns the primary node plus N-1 successor nodes on the ring.
     * Ensures distinct physical nodes (skips additional vnodes of same physical node).
     *
     * @param key The cache key to locate
     * @param replicationFactor Number of replicas to return
     * @return List of replica nodes, or empty list if ring is empty
     */
    fun getReplicaNodes(
        key: CacheKey,
        replicationFactor: Int,
    ): List<Node> =
        lock.read {
            if (ring.isEmpty() || replicationFactor <= 0) return emptyList()

            val hash = hash(key.value)
            val replicas = mutableListOf<Node>()
            val seenNodeIds = mutableSetOf<String>()

            // Start from the ceiling entry (first node clockwise from hash)
            var currentEntry = ring.ceilingEntry(hash) ?: ring.firstEntry()

            // Collect distinct physical nodes until we have enough replicas
            while (replicas.size < replicationFactor && seenNodeIds.size < nodeToVirtualNodes.size) {
                val physicalNode = currentEntry.value.physicalNode

                // Only add if we haven't seen this physical node yet
                // and if the node is alive (skip dead/suspected nodes)
                if (physicalNode.id !in seenNodeIds &&
                    physicalNode.status == NodeStatus.ALIVE
                ) {
                    replicas.add(physicalNode)
                    seenNodeIds.add(physicalNode.id)
                } else if (physicalNode.id !in seenNodeIds) {
                    // Mark as seen even if not alive, to avoid infinite loops
                    seenNodeIds.add(physicalNode.id)
                }

                // Move to next entry (wrap around if needed)
                currentEntry = ring.higherEntry(currentEntry.key) ?: ring.firstEntry()
            }

            return replicas
        }

    /**
     * Gets all nodes currently in the ring.
     *
     * @return Set of all physical nodes
     */
    fun getAllNodes(): Set<Node> =
        lock.read {
            nodeToVirtualNodes.values
                .flatMap { virtualNodes -> virtualNodes.map { it.physicalNode } }
                .toSet()
        }

    /**
     * Gets the number of physical nodes in the ring.
     */
    fun size(): Int =
        lock.read {
            nodeToVirtualNodes.size
        }

    /**
     * Checks if the ring is empty.
     */
    fun isEmpty(): Boolean =
        lock.read {
            ring.isEmpty()
        }

    /**
     * Clears all nodes from the ring.
     */
    fun clear() =
        lock.write {
            ring.clear()
            nodeToVirtualNodes.clear()
        }

    /**
     * Hashes a string to a long value for ring placement.
     * Uses MD5 hash and takes first 8 bytes as long.
     *
     * @param value The string to hash
     * @return Hash value as long
     */
    private fun hash(value: String): Long {
        val md5 = md5ThreadLocal.get()
        md5.reset()
        val digest = md5.digest(value.toByteArray())

        // Take first 8 bytes and convert to long
        var hash = 0L
        for (i in 0 until 8) {
            hash = (hash shl 8) or (digest[i].toLong() and 0xFF)
        }

        return hash
    }

    /**
     * Gets statistics about the ring for monitoring.
     */
    fun getStats(): RingStats =
        lock.read {
            RingStats(
                physicalNodes = nodeToVirtualNodes.size,
                virtualNodes = ring.size,
                vNodesPerNode = vNodesPerNode,
            )
        }
}

/**
 * Statistics about the consistent hash ring.
 */
data class RingStats(
    val physicalNodes: Int,
    val virtualNodes: Int,
    val vNodesPerNode: Int,
)
