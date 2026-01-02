package domain.gossip

import com.example.cache.proto.GossipMessage
import com.example.cache.proto.NodeInfo
import com.example.cache.proto.NodeStatus as ProtoNodeStatus
import domain.replication.Node
import domain.replication.NodeStatus
import java.net.InetSocketAddress
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * GossipService manages periodic state exchange between cluster nodes using the SWIM protocol.
 *
 * Responsibilities:
 * - Send periodic ping messages to random nodes
 * - Process incoming gossip messages
 * - Propagate membership changes to other nodes
 * - Coordinate with FailureDetector for state management
 */
class GossipService(
    private val localNode: Node,
    private val failureDetector: FailureDetector,
    private val gossipTransport: GossipTransport,
    private val config: GossipConfig
) {
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private val sequenceNumber = AtomicLong(0)
    private val pendingPings = mutableMapOf<Long, PingContext>()

    @Volatile
    private var running = false

    data class PingContext(
        val targetNodeId: String,
        val sentAt: Instant,
        val sequenceNumber: Long
    )

    /**
     * Start the gossip service.
     */
    fun start() {
        if (running) {
            return
        }

        running = true

        // Schedule periodic gossip rounds
        executor.scheduleAtFixedRate(
            { gossipRound() },
            0,
            config.gossipInterval.toMillis(),
            TimeUnit.MILLISECONDS
        )

        // Schedule periodic suspicion timeout checks
        executor.scheduleAtFixedRate(
            { checkSuspicionTimeouts() },
            config.suspicionTimeout.toMillis(),
            config.suspicionTimeout.toMillis() / 2,
            TimeUnit.MILLISECONDS
        )
    }

    /**
     * Stop the gossip service.
     */
    fun stop() {
        if (!running) {
            return
        }

        running = false
        executor.shutdown()

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Execute one round of gossip protocol.
     */
    private fun gossipRound() {
        try {
            // Get all alive nodes (excluding self)
            val aliveNodes = failureDetector.getAliveNodes()
                .filter { it.id != localNode.id }

            if (aliveNodes.isEmpty()) {
                return
            }

            // Select a random node to ping
            val targetNode = aliveNodes[Random.nextInt(aliveNodes.size)]

            // Send ping message
            sendPing(targetNode)

        } catch (e: Exception) {
            // Log error but don't stop gossip
            println("Error in gossip round: ${e.message}")
        }
    }

    /**
     * Send a PING message to a target node.
     */
    private fun sendPing(targetNode: Node) {
        val seqNum = sequenceNumber.incrementAndGet()
        val message = createGossipMessage(
            type = GossipMessage.MessageType.PING,
            subject = null,
            members = emptyList(),
            sequenceNumber = seqNum
        )

        pendingPings[seqNum] = PingContext(
            targetNodeId = targetNode.id,
            sentAt = Instant.now(),
            sequenceNumber = seqNum
        )

        // Send ping and wait for ACK
        executor.submit {
            try {
                val response = gossipTransport.sendMessage(targetNode.address, message, config.pingTimeout)

                if (response != null && response.type == GossipMessage.MessageType.ACK) {
                    handleAck(response)
                } else {
                    // No ACK received - try indirect ping
                    handlePingTimeout(targetNode, seqNum)
                }
            } catch (e: Exception) {
                handlePingTimeout(targetNode, seqNum)
            } finally {
                pendingPings.remove(seqNum)
            }
        }
    }

    /**
     * Handle ACK message received from a node.
     */
    private fun handleAck(message: GossipMessage) {
        val senderId = message.sender.id

        // Record successful heartbeat
        val transition = failureDetector.recordHeartbeat(senderId)

        // Update our view of the sender
        updateNodeFromMessage(message.sender)

        // Process any piggybacked membership updates
        message.membersList.forEach { memberInfo ->
            updateNodeFromMessage(memberInfo)
        }
    }

    /**
     * Handle ping timeout - initiate indirect ping.
     */
    private fun handlePingTimeout(targetNode: Node, seqNum: Long) {
        val context = pendingPings[seqNum] ?: return

        // Record missed heartbeat
        failureDetector.recordMissedHeartbeat(targetNode.id)

        // Try indirect ping through other nodes
        val aliveNodes = failureDetector.getAliveNodes()
            .filter { it.id != localNode.id && it.id != targetNode.id }
            .take(config.indirectPingNodes)

        if (aliveNodes.isNotEmpty()) {
            sendIndirectPing(targetNode, aliveNodes, seqNum)
        }
    }

    /**
     * Send indirect ping requests to proxy nodes.
     */
    private fun sendIndirectPing(targetNode: Node, proxyNodes: List<Node>, seqNum: Long) {
        proxyNodes.forEach { proxyNode ->
            executor.submit {
                try {
                    val message = createGossipMessage(
                        type = GossipMessage.MessageType.PING_REQ,
                        subject = nodeToProto(targetNode),
                        members = emptyList(),
                        sequenceNumber = seqNum
                    )

                    gossipTransport.sendMessage(proxyNode.address, message, config.pingTimeout)
                } catch (e: Exception) {
                    // Proxy node failed - continue with others
                }
            }
        }
    }

    /**
     * Process incoming gossip message.
     */
    fun handleMessage(message: GossipMessage): GossipMessage? {
        return when (message.type) {
            GossipMessage.MessageType.PING -> handlePing(message)
            GossipMessage.MessageType.ACK -> { handleAck(message); null }
            GossipMessage.MessageType.PING_REQ -> handlePingReq(message)
            GossipMessage.MessageType.SUSPECT -> { handleSuspect(message); null }
            GossipMessage.MessageType.ALIVE -> { handleAlive(message); null }
            GossipMessage.MessageType.CONFIRM -> { handleConfirm(message); null }
            GossipMessage.MessageType.JOIN -> handleJoin(message)
            GossipMessage.MessageType.JOIN_RESPONSE -> { handleJoinResponse(message); null }
            GossipMessage.MessageType.LEAVE -> { handleLeave(message); null }
            GossipMessage.MessageType.SYNC -> handleSync(message)
            else -> null
        }
    }

    /**
     * Handle incoming PING message.
     */
    private fun handlePing(message: GossipMessage): GossipMessage {
        // Update our view of the sender
        updateNodeFromMessage(message.sender)

        // Process piggybacked updates
        message.membersList.forEach { updateNodeFromMessage(it) }

        // Send ACK with our current membership view
        return createGossipMessage(
            type = GossipMessage.MessageType.ACK,
            subject = null,
            members = getRandomMembers(),
            sequenceNumber = message.sequenceNumber
        )
    }

    /**
     * Handle incoming PING_REQ (indirect ping request).
     */
    private fun handlePingReq(message: GossipMessage): GossipMessage? {
        val targetNodeInfo = message.subject ?: return null
        val targetNode = protoToNode(targetNodeInfo)

        return try {
            // Try to ping the target node on behalf of the requester
            val pingMessage = createGossipMessage(
                type = GossipMessage.MessageType.PING,
                subject = null,
                members = emptyList(),
                sequenceNumber = sequenceNumber.incrementAndGet()
            )

            val response = gossipTransport.sendMessage(
                targetNode.address,
                pingMessage,
                config.pingTimeout
            )

            // Forward the response back to the requester
            response
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Handle SUSPECT message.
     */
    private fun handleSuspect(message: GossipMessage) {
        val subjectInfo = message.subject ?: return

        // Update the suspected node's status
        val transition = failureDetector.updateNode(
            subjectInfo.id,
            subjectInfo.address,
            subjectInfo.port,
            NodeStatus.SUSPECTED,
            subjectInfo.incarnation,
            Instant.ofEpochMilli(subjectInfo.timestamp)
        )

        // Propagate the suspicion to other nodes
        if (transition is FailureDetector.StateTransition.StatusChanged) {
            propagateMembershipChange(message)
        }
    }

    /**
     * Handle ALIVE message (refutation of suspicion).
     */
    private fun handleAlive(message: GossipMessage) {
        val subjectInfo = message.subject ?: return

        // Update the node's status to alive
        failureDetector.updateNode(
            subjectInfo.id,
            subjectInfo.address,
            subjectInfo.port,
            NodeStatus.ALIVE,
            subjectInfo.incarnation,
            Instant.ofEpochMilli(subjectInfo.timestamp)
        )
    }

    /**
     * Handle CONFIRM message (confirmation of node failure).
     */
    private fun handleConfirm(message: GossipMessage) {
        val subjectInfo = message.subject ?: return

        // Mark the node as down
        failureDetector.updateNode(
            subjectInfo.id,
            subjectInfo.address,
            subjectInfo.port,
            NodeStatus.DOWN,
            subjectInfo.incarnation,
            Instant.ofEpochMilli(subjectInfo.timestamp)
        )
    }

    /**
     * Handle JOIN message from a new node.
     */
    private fun handleJoin(message: GossipMessage): GossipMessage {
        // Add the new node to our membership
        val senderInfo = message.sender
        failureDetector.updateNode(
            senderInfo.id,
            senderInfo.address,
            senderInfo.port,
            NodeStatus.ALIVE,
            senderInfo.incarnation,
            Instant.ofEpochMilli(senderInfo.timestamp)
        )

        // Send back full membership list
        val allMembers = failureDetector.getAllNodes().map { nodeToProto(it) }
        return createGossipMessage(
            type = GossipMessage.MessageType.JOIN_RESPONSE,
            subject = null,
            members = allMembers,
            sequenceNumber = message.sequenceNumber
        )
    }

    /**
     * Handle JOIN_RESPONSE message.
     */
    private fun handleJoinResponse(message: GossipMessage) {
        // Update our membership with the full cluster view
        message.membersList.forEach { updateNodeFromMessage(it) }
    }

    /**
     * Handle LEAVE message (graceful departure).
     */
    private fun handleLeave(message: GossipMessage) {
        val senderInfo = message.sender

        // Mark the node as leaving
        failureDetector.updateNode(
            senderInfo.id,
            senderInfo.address,
            senderInfo.port,
            NodeStatus.LEAVING,
            senderInfo.incarnation,
            Instant.ofEpochMilli(senderInfo.timestamp)
        )

        // Propagate the leave message
        propagateMembershipChange(message)
    }

    /**
     * Handle SYNC message (full state synchronization).
     */
    private fun handleSync(message: GossipMessage): GossipMessage {
        // Update our view with the sender's membership
        message.membersList.forEach { updateNodeFromMessage(it) }

        // Send back our full membership view
        val allMembers = failureDetector.getAllNodes().map { nodeToProto(it) }
        return createGossipMessage(
            type = GossipMessage.MessageType.SYNC,
            subject = null,
            members = allMembers,
            sequenceNumber = message.sequenceNumber
        )
    }

    /**
     * Check for suspicion timeouts and mark nodes as down.
     */
    private fun checkSuspicionTimeouts() {
        try {
            val transitions = failureDetector.checkSuspicionTimeouts()

            transitions.forEach { transition ->
                if (transition is FailureDetector.StateTransition.StatusChanged &&
                    transition.newStatus == NodeStatus.DOWN) {
                    // Propagate the confirmation of failure
                    broadcastConfirmation(transition.node)
                }
            }
        } catch (e: Exception) {
            println("Error checking suspicion timeouts: ${e.message}")
        }
    }

    /**
     * Broadcast confirmation of node failure.
     */
    private fun broadcastConfirmation(node: Node) {
        val message = createGossipMessage(
            type = GossipMessage.MessageType.CONFIRM,
            subject = nodeToProto(node),
            members = emptyList(),
            sequenceNumber = sequenceNumber.incrementAndGet()
        )

        // Send to a subset of random nodes
        val aliveNodes = failureDetector.getAliveNodes()
            .filter { it.id != localNode.id }
            .shuffled()
            .take(config.fanout)

        aliveNodes.forEach { targetNode ->
            executor.submit {
                try {
                    gossipTransport.sendMessage(targetNode.address, message, config.pingTimeout)
                } catch (e: Exception) {
                    // Continue with other nodes
                }
            }
        }
    }

    /**
     * Propagate membership change to other nodes.
     */
    private fun propagateMembershipChange(message: GossipMessage) {
        val aliveNodes = failureDetector.getAliveNodes()
            .filter { it.id != localNode.id }
            .shuffled()
            .take(config.fanout)

        aliveNodes.forEach { targetNode ->
            executor.submit {
                try {
                    gossipTransport.sendMessage(targetNode.address, message, config.pingTimeout)
                } catch (e: Exception) {
                    // Continue with other nodes
                }
            }
        }
    }

    /**
     * Update node state from protobuf message.
     */
    private fun updateNodeFromMessage(nodeInfo: NodeInfo) {
        failureDetector.updateNode(
            nodeInfo.id,
            nodeInfo.address,
            nodeInfo.port,
            protoStatusToStatus(nodeInfo.status),
            nodeInfo.incarnation,
            Instant.ofEpochMilli(nodeInfo.timestamp)
        )
    }

    /**
     * Get random subset of known members for piggybacking.
     */
    private fun getRandomMembers(): List<NodeInfo> {
        return failureDetector.getAllNodes()
            .shuffled()
            .take(config.piggybackSize)
            .map { nodeToProto(it) }
    }

    /**
     * Create a gossip message.
     */
    private fun createGossipMessage(
        type: GossipMessage.MessageType,
        subject: NodeInfo?,
        members: List<NodeInfo>,
        sequenceNumber: Long
    ): GossipMessage {
        val builder = GossipMessage.newBuilder()
            .setType(type)
            .setSender(nodeToProto(localNode))
            .setSequenceNumber(sequenceNumber)
            .addAllMembers(members)

        if (subject != null) {
            builder.setSubject(subject)
        }

        return builder.build()
    }

    /**
     * Convert Node to protobuf NodeInfo.
     */
    private fun nodeToProto(node: Node): NodeInfo {
        return NodeInfo.newBuilder()
            .setId(node.id)
            .setAddress(node.address.hostString)
            .setPort(node.address.port)
            .setStatus(statusToProtoStatus(node.status))
            .setIncarnation(failureDetector.getIncarnation(node.id))
            .setTimestamp(Instant.now().toEpochMilli())
            .build()
    }

    /**
     * Convert protobuf NodeInfo to Node.
     */
    private fun protoToNode(nodeInfo: NodeInfo): Node {
        return Node(
            id = nodeInfo.id,
            address = InetSocketAddress(nodeInfo.address, nodeInfo.port),
            status = protoStatusToStatus(nodeInfo.status)
        )
    }

    /**
     * Convert NodeStatus to protobuf NodeStatus.
     */
    private fun statusToProtoStatus(status: NodeStatus): ProtoNodeStatus {
        return when (status) {
            NodeStatus.ALIVE -> ProtoNodeStatus.ALIVE
            NodeStatus.SUSPECTED -> ProtoNodeStatus.SUSPECTED
            NodeStatus.DOWN -> ProtoNodeStatus.DOWN
            NodeStatus.LEAVING -> ProtoNodeStatus.LEAVING
            NodeStatus.LEFT -> ProtoNodeStatus.LEFT
        }
    }

    /**
     * Convert protobuf NodeStatus to NodeStatus.
     */
    private fun protoStatusToStatus(status: ProtoNodeStatus): NodeStatus {
        return when (status) {
            ProtoNodeStatus.ALIVE -> NodeStatus.ALIVE
            ProtoNodeStatus.SUSPECTED -> NodeStatus.SUSPECTED
            ProtoNodeStatus.DOWN -> NodeStatus.DOWN
            ProtoNodeStatus.LEAVING -> NodeStatus.LEAVING
            ProtoNodeStatus.LEFT -> NodeStatus.LEFT
            else -> NodeStatus.ALIVE
        }
    }
}

/**
 * Transport layer interface for sending gossip messages.
 */
interface GossipTransport {
    /**
     * Send a gossip message to a target address and wait for a response.
     *
     * @return The response message, or null if timeout occurs
     */
    fun sendMessage(
        targetAddress: InetSocketAddress,
        message: GossipMessage,
        timeout: java.time.Duration
    ): GossipMessage?
}
