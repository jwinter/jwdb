package domain.gossip

import domain.replication.Node
import domain.replication.NodeStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration

class FailureDetectorTest {
    private lateinit var failureDetector: FailureDetector
    private lateinit var config: FailureDetectorConfig

    @BeforeEach
    fun setup() {
        config = FailureDetectorConfig(
            missedHeartbeatThreshold = 3,
            suspicionTimeout = Duration.ofSeconds(10),
            heartbeatInterval = Duration.ofSeconds(1)
        )
        failureDetector = FailureDetector(config)
    }

    @Test
    fun `test new node is added correctly`() {
        val transition = failureDetector.updateNode(
            "node1",
            "localhost",
            8080,
            NodeStatus.ALIVE,
            0
        )

        assertTrue(transition is FailureDetector.StateTransition.NewNode)
        val state = failureDetector.getNodeState("node1")
        assertNotNull(state)
        assertEquals(NodeStatus.ALIVE, state?.status)
        assertEquals(0L, state?.incarnation)
    }

    @Test
    fun `test heartbeat resets missed count for alive node`() {
        // Add a node
        failureDetector.updateNode("node1", "localhost", 8080, NodeStatus.ALIVE, 0)

        // Miss some heartbeats
        failureDetector.recordMissedHeartbeat("node1")
        failureDetector.recordMissedHeartbeat("node1")

        var state = failureDetector.getNodeState("node1")
        assertEquals(2, state?.missedHeartbeats)

        // Record successful heartbeat
        val transition = failureDetector.recordHeartbeat("node1")

        assertTrue(transition is FailureDetector.StateTransition.HeartbeatRecorded)
        state = failureDetector.getNodeState("node1")
        assertEquals(0, state?.missedHeartbeats)
        assertEquals(NodeStatus.ALIVE, state?.status)
    }

    @Test
    fun `test suspected node refutes with heartbeat`() {
        // Add a node
        failureDetector.updateNode("node1", "localhost", 8080, NodeStatus.ALIVE, 0)

        // Miss enough heartbeats to become suspected
        repeat(3) { failureDetector.recordMissedHeartbeat("node1") }

        var state = failureDetector.getNodeState("node1")
        assertEquals(NodeStatus.SUSPECTED, state?.status)
        val oldIncarnation = state?.incarnation ?: 0

        // Record heartbeat - should refute suspicion
        val transition = failureDetector.recordHeartbeat("node1")

        assertTrue(transition is FailureDetector.StateTransition.StatusChanged)
        val statusChanged = transition as FailureDetector.StateTransition.StatusChanged
        assertEquals(NodeStatus.SUSPECTED, statusChanged.oldStatus)
        assertEquals(NodeStatus.ALIVE, statusChanged.newStatus)

        state = failureDetector.getNodeState("node1")
        assertEquals(NodeStatus.ALIVE, state?.status)
        assertTrue((state?.incarnation ?: 0) > oldIncarnation, "Incarnation should increment on refutation")
        assertEquals(0, state?.missedHeartbeats, "Missed heartbeats should be reset")
    }

    @Test
    fun `test missed heartbeats transition to suspected`() {
        failureDetector.updateNode("node1", "localhost", 8080, NodeStatus.ALIVE, 0)

        // Miss heartbeats below threshold
        repeat(2) {
            val transition = failureDetector.recordMissedHeartbeat("node1")
            assertTrue(transition is FailureDetector.StateTransition.MissedHeartbeat)
        }

        var state = failureDetector.getNodeState("node1")
        assertEquals(NodeStatus.ALIVE, state?.status)
        assertEquals(2, state?.missedHeartbeats)

        // One more missed heartbeat should trigger SUSPECTED
        val transition = failureDetector.recordMissedHeartbeat("node1")
        assertTrue(transition is FailureDetector.StateTransition.StatusChanged)

        state = failureDetector.getNodeState("node1")
        assertEquals(NodeStatus.SUSPECTED, state?.status)
    }

    @Test
    fun `test incarnation number prevents outdated updates`() {
        failureDetector.updateNode("node1", "localhost", 8080, NodeStatus.ALIVE, 5)

        // Try to update with lower incarnation - should be ignored
        val transition = failureDetector.updateNode("node1", "localhost", 8080, NodeStatus.SUSPECTED, 3)

        assertTrue(transition is FailureDetector.StateTransition.Ignored)
        val state = failureDetector.getNodeState("node1")
        assertEquals(NodeStatus.ALIVE, state?.status)
        assertEquals(5L, state?.incarnation)
    }

    @Test
    fun `test higher incarnation overwrites state`() {
        failureDetector.updateNode("node1", "localhost", 8080, NodeStatus.ALIVE, 5)

        // Update with higher incarnation
        val transition = failureDetector.updateNode("node1", "localhost", 8080, NodeStatus.SUSPECTED, 10)

        assertTrue(transition is FailureDetector.StateTransition.StatusChanged)
        val state = failureDetector.getNodeState("node1")
        assertEquals(NodeStatus.SUSPECTED, state?.status)
        assertEquals(10L, state?.incarnation)
    }

    @Test
    fun `test invalid state transitions are rejected`() {
        failureDetector.updateNode("node1", "localhost", 8080, NodeStatus.DOWN, 5)

        // Try invalid transition DOWN -> SUSPECTED (should be ignored)
        val transition = failureDetector.updateNode("node1", "localhost", 8080, NodeStatus.SUSPECTED, 5)

        assertTrue(transition is FailureDetector.StateTransition.Ignored)
        val state = failureDetector.getNodeState("node1")
        assertEquals(NodeStatus.DOWN, state?.status)
    }

    @Test
    fun `test suspicion timeout detection`() {
        // Create config with very short timeout for testing
        val shortConfig = FailureDetectorConfig(
            missedHeartbeatThreshold = 3,
            suspicionTimeout = Duration.ofMillis(100),
            heartbeatInterval = Duration.ofSeconds(1)
        )
        val detector = FailureDetector(shortConfig)

        // Add suspected node
        detector.updateNode("node1", "localhost", 8080, NodeStatus.SUSPECTED, 0)

        // Wait for timeout to expire
        Thread.sleep(150)

        // Check for suspicion timeouts
        val transitions = detector.checkSuspicionTimeouts()

        assertEquals(1, transitions.size)
        val transition = transitions[0]
        assertTrue(transition is FailureDetector.StateTransition.StatusChanged)

        val state = detector.getNodeState("node1")
        assertEquals(NodeStatus.DOWN, state?.status)
    }

    @Test
    fun `test get nodes by status`() {
        failureDetector.updateNode("node1", "localhost", 8081, NodeStatus.ALIVE, 0)
        failureDetector.updateNode("node2", "localhost", 8082, NodeStatus.SUSPECTED, 0)
        failureDetector.updateNode("node3", "localhost", 8083, NodeStatus.DOWN, 0)
        failureDetector.updateNode("node4", "localhost", 8084, NodeStatus.ALIVE, 0)

        val aliveNodes = failureDetector.getAliveNodes()
        assertEquals(2, aliveNodes.size)
        assertTrue(aliveNodes.all { it.status == NodeStatus.ALIVE })

        val suspectedNodes = failureDetector.getNodesByStatus(NodeStatus.SUSPECTED)
        assertEquals(1, suspectedNodes.size)

        val allNodes = failureDetector.getAllNodes()
        assertEquals(4, allNodes.size)
    }

    @Test
    fun `test node removal`() {
        failureDetector.updateNode("node1", "localhost", 8080, NodeStatus.ALIVE, 0)

        val removed = failureDetector.removeNode("node1")
        assertTrue(removed)

        val state = failureDetector.getNodeState("node1")
        assertNull(state)
    }
}
