package domain.replication

import domain.cache.CacheKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

@Tag("unit")
class ConsistentHashRingTest {
    private lateinit var ring: ConsistentHashRing
    private lateinit var node1: Node
    private lateinit var node2: Node
    private lateinit var node3: Node

    @BeforeEach
    fun setup() {
        ring = ConsistentHashRing(vNodesPerNode = 256)
        node1 = Node("node1", InetSocketAddress("127.0.0.1", 8081))
        node2 = Node("node2", InetSocketAddress("127.0.0.1", 8082))
        node3 = Node("node3", InetSocketAddress("127.0.0.1", 8083))
    }

    @Test
    fun `empty ring returns null for getNode`() {
        val key = CacheKey("test-key")
        assertNull(ring.getNode(key))
    }

    @Test
    fun `empty ring returns empty list for getReplicaNodes`() {
        val key = CacheKey("test-key")
        val replicas = ring.getReplicaNodes(key, replicationFactor = 3)
        assertTrue(replicas.isEmpty())
    }

    @Test
    fun `single node ring returns that node for any key`() {
        ring.addNode(node1)

        val key1 = CacheKey("key1")
        val key2 = CacheKey("key2")

        assertEquals(node1, ring.getNode(key1))
        assertEquals(node1, ring.getNode(key2))
    }

    @Test
    fun `adding same node twice is idempotent`() {
        ring.addNode(node1)
        ring.addNode(node1) // Add again

        assertEquals(1, ring.size())
        assertEquals(256, ring.getStats().virtualNodes)
    }

    @Test
    fun `three nodes are distributed across the ring`() {
        ring.addNode(node1)
        ring.addNode(node2)
        ring.addNode(node3)

        assertEquals(3, ring.size())
        assertEquals(768, ring.getStats().virtualNodes) // 3 nodes * 256 vnodes
    }

    @Test
    fun `same key always maps to same node`() {
        ring.addNode(node1)
        ring.addNode(node2)
        ring.addNode(node3)

        val key = CacheKey("consistent-key")
        val firstResult = ring.getNode(key)

        // Query 100 times - should always return same node
        repeat(100) {
            assertEquals(firstResult, ring.getNode(key))
        }
    }

    @Test
    fun `getReplicaNodes returns correct number of replicas`() {
        ring.addNode(node1)
        ring.addNode(node2)
        ring.addNode(node3)

        val key = CacheKey("test-key")

        val replicas1 = ring.getReplicaNodes(key, replicationFactor = 1)
        assertEquals(1, replicas1.size)

        val replicas2 = ring.getReplicaNodes(key, replicationFactor = 2)
        assertEquals(2, replicas2.size)

        val replicas3 = ring.getReplicaNodes(key, replicationFactor = 3)
        assertEquals(3, replicas3.size)
    }

    @Test
    fun `getReplicaNodes returns distinct physical nodes`() {
        ring.addNode(node1)
        ring.addNode(node2)
        ring.addNode(node3)

        val key = CacheKey("test-key")
        val replicas = ring.getReplicaNodes(key, replicationFactor = 3)

        // All replicas should be distinct
        assertEquals(3, replicas.toSet().size)

        // Should contain our three nodes
        assertTrue(replicas.contains(node1))
        assertTrue(replicas.contains(node2))
        assertTrue(replicas.contains(node3))
    }

    @Test
    fun `getReplicaNodes with RF greater than cluster size returns all nodes`() {
        ring.addNode(node1)
        ring.addNode(node2)

        val key = CacheKey("test-key")
        val replicas = ring.getReplicaNodes(key, replicationFactor = 5)

        // Can only return 2 nodes even though RF=5
        assertEquals(2, replicas.size)
    }

    @Test
    fun `getReplicaNodes with zero RF returns empty list`() {
        ring.addNode(node1)
        ring.addNode(node2)

        val key = CacheKey("test-key")
        val replicas = ring.getReplicaNodes(key, replicationFactor = 0)

        assertTrue(replicas.isEmpty())
    }

    @Test
    fun `removeNode removes node from ring`() {
        ring.addNode(node1)
        ring.addNode(node2)

        assertEquals(2, ring.size())

        ring.removeNode(node1.id)

        assertEquals(1, ring.size())
        assertEquals(256, ring.getStats().virtualNodes)
    }

    @Test
    fun `removing non-existent node is safe`() {
        ring.addNode(node1)
        ring.removeNode("non-existent-node")
        assertEquals(1, ring.size())
    }

    @Test
    fun `keys redistribute when node is removed`() {
        ring.addNode(node1)
        ring.addNode(node2)
        ring.addNode(node3)

        val key = CacheKey("test-key")
        val originalNode = ring.getNode(key)

        // Remove the node that owns the key (might not be originalNode)
        if (originalNode == node1) {
            ring.removeNode(node1.id)
            val newNode = ring.getNode(key)
            assertNotNull(newNode)
            assertNotEquals(node1, newNode)
            assertTrue(newNode == node2 || newNode == node3)
        }
    }

    @Test
    fun `getAllNodes returns all physical nodes`() {
        ring.addNode(node1)
        ring.addNode(node2)
        ring.addNode(node3)

        val allNodes = ring.getAllNodes()
        assertEquals(3, allNodes.size)
        assertTrue(allNodes.contains(node1))
        assertTrue(allNodes.contains(node2))
        assertTrue(allNodes.contains(node3))
    }

    @Test
    fun `clear removes all nodes`() {
        ring.addNode(node1)
        ring.addNode(node2)
        ring.addNode(node3)

        ring.clear()

        assertEquals(0, ring.size())
        assertTrue(ring.isEmpty())
        assertTrue(ring.getAllNodes().isEmpty())
    }

    @Test
    fun `different keys distribute across different nodes`() {
        ring.addNode(node1)
        ring.addNode(node2)
        ring.addNode(node3)

        val nodeAssignments = mutableSetOf<Node>()

        // Try 1000 different keys - should hit multiple nodes
        repeat(1000) { i ->
            val key = CacheKey("key-$i")
            val node = ring.getNode(key)
            nodeAssignments.add(node!!)
        }

        // With 1000 keys and 3 nodes, we should hit all 3 nodes
        // (probability of missing a node is astronomically low)
        assertTrue(nodeAssignments.size >= 3, "Expected all 3 nodes to be used, got ${nodeAssignments.size}")
    }

    @Test
    fun `virtual nodes improve distribution`() {
        // Create ring with only 1 vnode per node (poor distribution)
        val poorRing = ConsistentHashRing(vNodesPerNode = 1)
        poorRing.addNode(node1)
        poorRing.addNode(node2)
        poorRing.addNode(node3)

        // Create ring with 256 vnodes per node (good distribution)
        val goodRing = ConsistentHashRing(vNodesPerNode = 256)
        goodRing.addNode(node1)
        goodRing.addNode(node2)
        goodRing.addNode(node3)

        val keyCount = 1000
        val poorDistribution = countDistribution(poorRing, keyCount)
        val goodDistribution = countDistribution(goodRing, keyCount)

        // Calculate variance (measure of how evenly distributed the keys are)
        val poorVariance = calculateVariance(poorDistribution.values)
        val goodVariance = calculateVariance(goodDistribution.values)

        // More vnodes should lead to better (lower) variance
        assertTrue(
            goodVariance < poorVariance,
            "Expected better distribution with more vnodes. Poor variance: $poorVariance, Good variance: $goodVariance",
        )
    }

    @Test
    fun `getStats returns correct statistics`() {
        ring.addNode(node1)
        ring.addNode(node2)

        val stats = ring.getStats()
        assertEquals(2, stats.physicalNodes)
        assertEquals(512, stats.virtualNodes) // 2 * 256
        assertEquals(256, stats.vNodesPerNode)
    }

    @Test
    fun `getReplicaNodes skips non-alive nodes`() {
        val deadNode = Node("dead", InetSocketAddress("127.0.0.1", 9999), NodeStatus.DOWN)
        ring.addNode(node1)
        ring.addNode(deadNode)
        ring.addNode(node2)

        val key = CacheKey("test-key")
        val replicas = ring.getReplicaNodes(key, replicationFactor = 3)

        // Should only get alive nodes (node1 and node2), not the dead node
        assertEquals(2, replicas.size)
        assertFalse(replicas.contains(deadNode))
    }

    @Test
    fun `getReplicaNodes with all dead nodes returns empty list`() {
        val deadNode1 = Node("dead1", InetSocketAddress("127.0.0.1", 9991), NodeStatus.DOWN)
        val deadNode2 = Node("dead2", InetSocketAddress("127.0.0.1", 9992), NodeStatus.DOWN)
        ring.addNode(deadNode1)
        ring.addNode(deadNode2)

        val key = CacheKey("test-key")
        val replicas = ring.getReplicaNodes(key, replicationFactor = 2)

        assertTrue(replicas.isEmpty())
    }

    // Helper functions

    private fun countDistribution(
        hashRing: ConsistentHashRing,
        keyCount: Int,
    ): Map<String, Int> {
        val distribution = mutableMapOf<String, Int>()

        repeat(keyCount) { i ->
            val key = CacheKey("key-$i")
            val node = hashRing.getNode(key)
            if (node != null) {
                distribution[node.id] = distribution.getOrDefault(node.id, 0) + 1
            }
        }

        return distribution
    }

    private fun calculateVariance(values: Collection<Int>): Double {
        if (values.isEmpty()) return 0.0

        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }
}
