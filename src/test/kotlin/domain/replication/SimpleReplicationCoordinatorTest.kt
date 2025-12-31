package domain.replication

import domain.cache.Cache
import domain.cache.CacheKey
import domain.cache.CacheResult
import domain.cache.CacheValue
import domain.cache.InMemoryCache
import domain.cache.WriteResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.time.Duration

@Tag("unit")
class SimpleReplicationCoordinatorTest {
    private lateinit var hashRing: ConsistentHashRing
    private lateinit var node1: Node
    private lateinit var node2: Node
    private lateinit var node3: Node
    private lateinit var cache1: Cache<String>
    private lateinit var cache2: Cache<String>
    private lateinit var cache3: Cache<String>
    private lateinit var nodeStores: Map<String, Cache<String>>
    private lateinit var coordinator: SimpleReplicationCoordinator<String>

    @BeforeEach
    fun setup() {
        // Create 3 nodes
        node1 = Node(id = "node1", address = InetSocketAddress("localhost", 8081), status = NodeStatus.ALIVE)
        node2 = Node(id = "node2", address = InetSocketAddress("localhost", 8082), status = NodeStatus.ALIVE)
        node3 = Node(id = "node3", address = InetSocketAddress("localhost", 8083), status = NodeStatus.ALIVE)

        // Create hash ring with 3 nodes
        hashRing = ConsistentHashRing(vNodesPerNode = 256)
        hashRing.addNode(node1)
        hashRing.addNode(node2)
        hashRing.addNode(node3)

        // Create cache instances for each node
        cache1 = InMemoryCache(maxSize = 100)
        cache2 = InMemoryCache(maxSize = 100)
        cache3 = InMemoryCache(maxSize = 100)

        nodeStores =
            mapOf(
                "node1" to cache1,
                "node2" to cache2,
                "node3" to cache3,
            )

        // Create coordinator with RF=3
        val config = ReplicationConfig(replicationFactor = 3)
        coordinator = SimpleReplicationCoordinator(cache1, hashRing, config, LastWriteWinsResolver(), nodeStores)
    }

    // Write Tests

    @Test
    fun `replicatedPut with ONE consistency writes to 1 replica`() {
        val key = CacheKey("test-key")
        val value = CacheValue("test-value", version = Version.now("node1"))

        val result = coordinator.replicatedPut(key, value, ConsistencyLevel.ONE)

        assertTrue(result is WriteResult.Success)

        // At least one replica should have the value
        val replicas = hashRing.getReplicaNodes(key, 3)
        val hasValue =
            replicas.any { node ->
                val cache = nodeStores[node.id]!!
                cache.get(key) is CacheResult.Hit
            }
        assertTrue(hasValue)
    }

    @Test
    fun `replicatedPut with QUORUM consistency writes to majority of replicas`() {
        val key = CacheKey("test-key")
        val value = CacheValue("test-value", version = Version.now("node1"))

        val result = coordinator.replicatedPut(key, value, ConsistencyLevel.QUORUM)

        assertTrue(result is WriteResult.Success)

        // At least quorum (2 out of 3) replicas should have the value
        val replicas = hashRing.getReplicaNodes(key, 3)
        val successCount =
            replicas.count { node ->
                val cache = nodeStores[node.id]!!
                cache.get(key) is CacheResult.Hit
            }
        assertTrue(successCount >= 2, "Expected at least 2 replicas, got $successCount")
    }

    @Test
    fun `replicatedPut with ALL consistency writes to all replicas`() {
        val key = CacheKey("test-key")
        val value = CacheValue("test-value", version = Version.now("node1"))

        val result = coordinator.replicatedPut(key, value, ConsistencyLevel.ALL)

        assertTrue(result is WriteResult.Success)

        // All 3 replicas should have the value
        val replicas = hashRing.getReplicaNodes(key, 3)
        assertEquals(3, replicas.size)

        replicas.forEach { node ->
            val cache = nodeStores[node.id]!!
            val readResult = cache.get(key)
            assertTrue(readResult is CacheResult.Hit, "Expected hit on ${node.id}")
        }
    }

    @Test
    fun `replicatedPut throws exception when no nodes available`() {
        val emptyRing = ConsistentHashRing()
        val emptyCoordinator =
            SimpleReplicationCoordinator(
                cache1,
                emptyRing,
                ReplicationConfig(),
                LastWriteWinsResolver(),
                emptyMap(),
            )

        val key = CacheKey("test-key")
        val value = CacheValue("test-value")

        assertThrows<ReplicationException> {
            emptyCoordinator.replicatedPut(key, value, ConsistencyLevel.ONE)
        }
    }

    // Read Tests

    @Test
    fun `replicatedGet with ONE consistency reads from 1 replica`() {
        // Setup: Write value to all replicas directly
        val key = CacheKey("test-key")
        val value = CacheValue("test-value", version = Version.now("node1"))

        val replicas = hashRing.getReplicaNodes(key, 3)
        replicas.forEach { node ->
            nodeStores[node.id]!!.put(key, value)
        }

        // Test: Read with ONE
        val result = coordinator.replicatedGet(key, ConsistencyLevel.ONE)

        assertTrue(result is CacheResult.Hit)
        assertEquals("test-value", (result as CacheResult.Hit).value.data)
    }

    @Test
    fun `replicatedGet with QUORUM consistency reads from majority`() {
        // Setup: Write value to all replicas
        val key = CacheKey("test-key")
        val value = CacheValue("test-value", version = Version.now("node1"))

        val replicas = hashRing.getReplicaNodes(key, 3)
        replicas.forEach { node ->
            nodeStores[node.id]!!.put(key, value)
        }

        // Test: Read with QUORUM
        val result = coordinator.replicatedGet(key, ConsistencyLevel.QUORUM)

        assertTrue(result is CacheResult.Hit)
        assertEquals("test-value", (result as CacheResult.Hit).value.data)
    }

    @Test
    fun `replicatedGet returns latest version when replicas have different versions`() {
        val key = CacheKey("test-key")

        // Setup: Write different versions to different replicas
        val now = System.currentTimeMillis()
        val oldVersion = Version(timestamp = now - 10000, nodeId = "node1")
        val newVersion = Version(timestamp = now, nodeId = "node2")

        val oldValue = CacheValue("old-value", version = oldVersion)
        val newValue = CacheValue("new-value", version = newVersion)

        val replicas = hashRing.getReplicaNodes(key, 3)

        // Put old value on first two replicas, new value on third
        nodeStores[replicas[0].id]!!.put(key, oldValue)
        nodeStores[replicas[1].id]!!.put(key, oldValue)
        nodeStores[replicas[2].id]!!.put(key, newValue)

        // Test: Read should return the newest version
        val result = coordinator.replicatedGet(key, ConsistencyLevel.ALL)

        assertTrue(result is CacheResult.Hit)
        assertEquals("new-value", (result as CacheResult.Hit).value.data)
        assertEquals(newVersion, result.value.version)
    }

    @Test
    fun `replicatedGet returns Miss when key not found on any replica`() {
        val key = CacheKey("non-existent-key")

        val result = coordinator.replicatedGet(key, ConsistencyLevel.ONE)

        assertTrue(result is CacheResult.Miss)
    }

    @Test
    fun `replicatedGet throws exception when no nodes available`() {
        val emptyRing = ConsistentHashRing()
        val emptyCoordinator =
            SimpleReplicationCoordinator(
                cache1,
                emptyRing,
                ReplicationConfig(),
                LastWriteWinsResolver(),
                emptyMap(),
            )

        val key = CacheKey("test-key")

        assertThrows<ReplicationException> {
            emptyCoordinator.replicatedGet(key, ConsistencyLevel.ONE)
        }
    }

    // Delete Tests

    @Test
    fun `replicatedDelete with QUORUM removes from majority of replicas`() {
        // Setup: Write value to all replicas
        val key = CacheKey("test-key")
        val value = CacheValue("test-value", version = Version.now("node1"))

        val replicas = hashRing.getReplicaNodes(key, 3)
        replicas.forEach { node ->
            nodeStores[node.id]!!.put(key, value)
        }

        // Test: Delete with QUORUM
        val result = coordinator.replicatedDelete(key, ConsistencyLevel.QUORUM)

        assertTrue(result is WriteResult.Success)

        // At least quorum (2 out of 3) should have deleted the value
        val deletedCount =
            replicas.count { node ->
                nodeStores[node.id]!!.get(key) is CacheResult.Miss
            }
        assertTrue(deletedCount >= 2, "Expected at least 2 deletes, got $deletedCount")
    }

    @Test
    fun `replicatedDelete with ALL removes from all replicas`() {
        // Setup: Write value to all replicas
        val key = CacheKey("test-key")
        val value = CacheValue("test-value", version = Version.now("node1"))

        val replicas = hashRing.getReplicaNodes(key, 3)
        replicas.forEach { node ->
            nodeStores[node.id]!!.put(key, value)
        }

        // Test: Delete with ALL
        val result = coordinator.replicatedDelete(key, ConsistencyLevel.ALL)

        assertTrue(result is WriteResult.Success)

        // All replicas should have deleted the value
        replicas.forEach { node ->
            val readResult = nodeStores[node.id]!!.get(key)
            assertTrue(readResult is CacheResult.Miss, "Expected miss on ${node.id}")
        }
    }

    // Configuration Tests

    @Test
    fun `getConfig returns replication configuration`() {
        val config = coordinator.getConfig()

        assertEquals(3, config.replicationFactor)
        assertEquals(ConsistencyLevel.QUORUM, config.defaultReadConsistency)
        assertEquals(ConsistencyLevel.QUORUM, config.defaultWriteConsistency)
        assertTrue(config.hintedHandoffEnabled)
        assertTrue(config.readRepairEnabled)
    }

    @Test
    fun `ReplicationConfig calculates quorum size correctly`() {
        assertEquals(1, ReplicationConfig(replicationFactor = 1).quorumSize)
        assertEquals(2, ReplicationConfig(replicationFactor = 2).quorumSize)
        assertEquals(2, ReplicationConfig(replicationFactor = 3).quorumSize)
        assertEquals(3, ReplicationConfig(replicationFactor = 4).quorumSize)
        assertEquals(3, ReplicationConfig(replicationFactor = 5).quorumSize)
    }

    @Test
    fun `ReplicationConfig validates replication factor`() {
        assertThrows<IllegalArgumentException> {
            ReplicationConfig(replicationFactor = 0)
        }

        assertThrows<IllegalArgumentException> {
            ReplicationConfig(replicationFactor = -1)
        }
    }

    // Edge Cases

    @Test
    fun `coordinator handles timeout gracefully`() {
        val key = CacheKey("test-key")
        val value = CacheValue("test-value", version = Version.now("node1"))

        // Use very short timeout to force timeout scenario
        // Note: In this simple implementation, timeouts may not always trigger
        // This test documents expected behavior for future enhancements
        val shortTimeout = Duration.ofMillis(1)

        // This may succeed or timeout depending on execution speed
        // The important part is it doesn't hang or crash
        try {
            coordinator.replicatedPut(key, value, ConsistencyLevel.ALL, timeout = shortTimeout)
        } catch (e: ReplicationException) {
            // Expected in timeout scenario
            assertTrue(e.message?.contains("timeout") == true || e.message?.contains("fail") == true)
        }
    }
}
