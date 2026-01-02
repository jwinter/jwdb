package domain.gossip

import domain.replication.Node
import domain.replication.NodeStatus
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * SWIM-based failure detection state machine.
 * Tracks node states and transitions based on gossip protocol events.
 *
 * State transitions:
 * - ALIVE -> SUSPECTED (missed heartbeats)
 * - SUSPECTED -> ALIVE (refutation via incarnation number)
 * - SUSPECTED -> DOWN (suspicion timeout expires)
 * - ALIVE/SUSPECTED -> LEAVING (graceful shutdown announced)
 * - LEAVING -> LEFT (shutdown completed)
 * - DOWN/LEFT -> ALIVE (node rejoins with higher incarnation)
 */
class FailureDetector(
    private val config: FailureDetectorConfig
) {
    private val nodeStates = ConcurrentHashMap<String, NodeState>()
    private val incarnationNumbers = ConcurrentHashMap<String, AtomicLong>()

    /**
     * Represents the internal state of a node for failure detection.
     */
    data class NodeState(
        val node: Node,
        val incarnation: Long,
        val status: NodeStatus,
        val lastUpdate: Instant,
        val suspectedAt: Instant? = null,
        val missedHeartbeats: Int = 0
    ) {
        fun isSuspicionExpired(timeout: Duration): Boolean {
            return suspectedAt != null &&
                Duration.between(suspectedAt, Instant.now()) > timeout
        }

        fun isHeartbeatExpired(timeout: Duration): Boolean {
            return Duration.between(lastUpdate, Instant.now()) > timeout
        }
    }

    /**
     * Update node status based on gossip message.
     */
    fun updateNode(
        nodeId: String,
        address: String,
        port: Int,
        status: NodeStatus,
        incarnation: Long,
        timestamp: Instant = Instant.now()
    ): StateTransition {
        val currentState = nodeStates[nodeId]

        // If this is a new node, add it
        if (currentState == null) {
            val socketAddress = InetSocketAddress(address, port)
            val node = Node(nodeId, socketAddress, status)
            val suspectedAt = if (status == NodeStatus.SUSPECTED) timestamp else null
            val newState = NodeState(node, incarnation, status, timestamp, suspectedAt)
            nodeStates[nodeId] = newState
            incarnationNumbers.putIfAbsent(nodeId, AtomicLong(incarnation))
            return StateTransition.NewNode(node)
        }

        // Ignore outdated updates (lower incarnation number)
        if (incarnation < currentState.incarnation) {
            return StateTransition.Ignored("Outdated incarnation: $incarnation < ${currentState.incarnation}")
        }

        // If incarnation is higher, accept the update
        if (incarnation > currentState.incarnation) {
            val socketAddress = InetSocketAddress(address, port)
            val node = Node(nodeId, socketAddress, status)
            val suspectedAt = if (status == NodeStatus.SUSPECTED) timestamp else null
            val newState = NodeState(node, incarnation, status, timestamp, suspectedAt)
            nodeStates[nodeId] = newState
            incarnationNumbers[nodeId]?.set(incarnation)
            return StateTransition.StatusChanged(currentState.status, status, node)
        }

        // Same incarnation - only accept status updates in specific directions
        val allowedTransition = isAllowedTransition(currentState.status, status)
        if (!allowedTransition) {
            return StateTransition.Ignored("Invalid transition: ${currentState.status} -> $status")
        }

        val socketAddress = InetSocketAddress(address, port)
        val node = Node(nodeId, socketAddress, status)
        val suspectedAt = if (status == NodeStatus.SUSPECTED) timestamp else null
        val newState = NodeState(node, incarnation, status, timestamp, suspectedAt)
        nodeStates[nodeId] = newState

        return StateTransition.StatusChanged(currentState.status, status, node)
    }

    /**
     * Record a successful heartbeat from a node.
     */
    fun recordHeartbeat(nodeId: String): StateTransition {
        val currentState = nodeStates[nodeId] ?: return StateTransition.Ignored("Unknown node: $nodeId")

        // Reset missed heartbeats counter
        val newState = currentState.copy(
            lastUpdate = Instant.now(),
            missedHeartbeats = 0
        )
        nodeStates[nodeId] = newState

        // If node was suspected, it's now alive (refutation)
        if (currentState.status == NodeStatus.SUSPECTED) {
            val incarnation = incrementIncarnation(nodeId)
            return updateNode(
                nodeId,
                currentState.node.address.hostString,
                currentState.node.address.port,
                NodeStatus.ALIVE,
                incarnation
            )
        }

        return StateTransition.HeartbeatRecorded(currentState.node)
    }

    /**
     * Record a missed heartbeat for a node.
     */
    fun recordMissedHeartbeat(nodeId: String): StateTransition {
        val currentState = nodeStates[nodeId] ?: return StateTransition.Ignored("Unknown node: $nodeId")

        val newMissedCount = currentState.missedHeartbeats + 1
        val newState = currentState.copy(missedHeartbeats = newMissedCount)
        nodeStates[nodeId] = newState

        // If we've exceeded the threshold, mark as suspected
        if (newMissedCount >= config.missedHeartbeatThreshold && currentState.status == NodeStatus.ALIVE) {
            return updateNode(
                nodeId,
                currentState.node.address.hostString,
                currentState.node.address.port,
                NodeStatus.SUSPECTED,
                currentState.incarnation,
                Instant.now()
            )
        }

        return StateTransition.MissedHeartbeat(currentState.node, newMissedCount)
    }

    /**
     * Check all suspected nodes and mark as down if suspicion timeout has expired.
     */
    fun checkSuspicionTimeouts(): List<StateTransition> {
        val transitions = mutableListOf<StateTransition>()

        nodeStates.values
            .filter { it.status == NodeStatus.SUSPECTED }
            .forEach { state ->
                if (state.isSuspicionExpired(config.suspicionTimeout)) {
                    val transition = updateNode(
                        state.node.id,
                        state.node.address.hostString,
                        state.node.address.port,
                        NodeStatus.DOWN,
                        state.incarnation
                    )
                    transitions.add(transition)
                }
            }

        return transitions
    }

    /**
     * Increment the incarnation number for a node (used for refuting suspicions).
     */
    fun incrementIncarnation(nodeId: String): Long {
        return incarnationNumbers
            .computeIfAbsent(nodeId) { AtomicLong(0) }
            .incrementAndGet()
    }

    /**
     * Get the current incarnation number for a node.
     */
    fun getIncarnation(nodeId: String): Long {
        return incarnationNumbers[nodeId]?.get() ?: 0L
    }

    /**
     * Get the current state of a node.
     */
    fun getNodeState(nodeId: String): NodeState? {
        return nodeStates[nodeId]
    }

    /**
     * Get all known nodes.
     */
    fun getAllNodes(): List<Node> {
        return nodeStates.values.map { it.node }
    }

    /**
     * Get all nodes with a specific status.
     */
    fun getNodesByStatus(status: NodeStatus): List<Node> {
        return nodeStates.values
            .filter { it.status == status }
            .map { it.node }
    }

    /**
     * Get all alive nodes (not suspected, down, leaving, or left).
     */
    fun getAliveNodes(): List<Node> {
        return getNodesByStatus(NodeStatus.ALIVE)
    }

    /**
     * Remove a node from the failure detector.
     */
    fun removeNode(nodeId: String): Boolean {
        nodeStates.remove(nodeId)
        incarnationNumbers.remove(nodeId)
        return true
    }

    /**
     * Check if a state transition is allowed.
     */
    private fun isAllowedTransition(from: NodeStatus, to: NodeStatus): Boolean {
        return when (from) {
            NodeStatus.ALIVE -> to in setOf(NodeStatus.SUSPECTED, NodeStatus.LEAVING)
            NodeStatus.SUSPECTED -> to in setOf(NodeStatus.ALIVE, NodeStatus.DOWN, NodeStatus.LEAVING)
            NodeStatus.DOWN -> to in setOf(NodeStatus.ALIVE) // Allow rejoin
            NodeStatus.LEAVING -> to in setOf(NodeStatus.LEFT)
            NodeStatus.LEFT -> to in setOf(NodeStatus.ALIVE) // Allow rejoin
        }
    }

    /**
     * Sealed class representing state transitions.
     */
    sealed class StateTransition {
        data class NewNode(val node: Node) : StateTransition()
        data class StatusChanged(val oldStatus: NodeStatus, val newStatus: NodeStatus, val node: Node) : StateTransition()
        data class HeartbeatRecorded(val node: Node) : StateTransition()
        data class MissedHeartbeat(val node: Node, val count: Int) : StateTransition()
        data class Ignored(val reason: String) : StateTransition()
    }
}

/**
 * Configuration for failure detection.
 */
data class FailureDetectorConfig(
    val missedHeartbeatThreshold: Int = 3,
    val suspicionTimeout: Duration = Duration.ofSeconds(10),
    val heartbeatInterval: Duration = Duration.ofSeconds(1)
) {
    init {
        require(missedHeartbeatThreshold > 0) { "Missed heartbeat threshold must be positive" }
        require(!suspicionTimeout.isNegative && !suspicionTimeout.isZero) {
            "Suspicion timeout must be positive"
        }
        require(!heartbeatInterval.isNegative && !heartbeatInterval.isZero) {
            "Heartbeat interval must be positive"
        }
    }
}
