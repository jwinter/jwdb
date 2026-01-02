package domain.gossip

import com.example.cache.proto.GossipMessage
import domain.replication.Node
import domain.replication.NodeStatus
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * MembershipManager handles node join and leave operations.
 *
 * Responsibilities:
 * - Join cluster by contacting seed nodes
 * - Graceful shutdown and departure notification
 * - Seed node management
 */
class MembershipManager(
    private val localNode: Node,
    private val gossipService: GossipService,
    private val failureDetector: FailureDetector,
    private val gossipTransport: GossipTransport,
    private val seedNodes: List<InetSocketAddress>
) {

    /**
     * Join the cluster by contacting seed nodes.
     *
     * @return true if successfully joined, false otherwise
     */
    fun joinCluster(timeout: Duration = Duration.ofSeconds(10)): Boolean {
        if (seedNodes.isEmpty()) {
            // No seed nodes - this is the first node or standalone mode
            println("No seed nodes configured. Starting as standalone node.")
            return true
        }

        // Try to contact each seed node
        val joinFutures = seedNodes.map { seedAddress ->
            CompletableFuture.supplyAsync {
                tryJoinViaSeed(seedAddress)
            }
        }

        // Wait for at least one successful join
        try {
            val result = CompletableFuture.anyOf(*joinFutures.toTypedArray())
                .get(timeout.toMillis(), TimeUnit.MILLISECONDS) as? Boolean ?: false

            if (result) {
                println("Successfully joined cluster via seed node")
                gossipService.start()
                return true
            }
        } catch (e: Exception) {
            println("Failed to join cluster: ${e.message}")
        }

        // If no seed node responded, try to start anyway (could be network partition)
        println("Warning: Could not contact any seed nodes. Starting in isolated mode.")
        gossipService.start()
        return false
    }

    /**
     * Try to join the cluster via a specific seed node.
     */
    private fun tryJoinViaSeed(seedAddress: InetSocketAddress): Boolean {
        return try {
            // Create JOIN message
            val joinMessage = createJoinMessage()

            // Send JOIN message to seed node
            val response = gossipTransport.sendMessage(
                seedAddress,
                joinMessage,
                Duration.ofSeconds(3)
            )

            // Process JOIN_RESPONSE
            if (response != null && response.type == GossipMessage.MessageType.JOIN_RESPONSE) {
                processJoinResponse(response)

                // Add the seed node to our membership
                val seedNodeId = response.sender.id
                failureDetector.updateNode(
                    seedNodeId,
                    response.sender.address,
                    response.sender.port,
                    NodeStatus.ALIVE,
                    response.sender.incarnation,
                    Instant.ofEpochMilli(response.sender.timestamp)
                )

                true
            } else {
                false
            }
        } catch (e: Exception) {
            println("Failed to join via seed node $seedAddress: ${e.message}")
            false
        }
    }

    /**
     * Create a JOIN message.
     */
    private fun createJoinMessage(): GossipMessage {
        val localNodeInfo = com.example.cache.proto.NodeInfo.newBuilder()
            .setId(localNode.id)
            .setAddress(localNode.address.hostString)
            .setPort(localNode.address.port)
            .setStatus(com.example.cache.proto.NodeStatus.ALIVE)
            .setIncarnation(failureDetector.getIncarnation(localNode.id))
            .setTimestamp(Instant.now().toEpochMilli())
            .build()

        return GossipMessage.newBuilder()
            .setType(GossipMessage.MessageType.JOIN)
            .setSender(localNodeInfo)
            .setSequenceNumber(0)
            .build()
    }

    /**
     * Process JOIN_RESPONSE message and learn cluster membership.
     */
    private fun processJoinResponse(response: GossipMessage) {
        println("Received JOIN_RESPONSE with ${response.membersCount} members")

        // Update our membership view with all members from the response
        response.membersList.forEach { memberInfo ->
            val status = when (memberInfo.status) {
                com.example.cache.proto.NodeStatus.ALIVE -> NodeStatus.ALIVE
                com.example.cache.proto.NodeStatus.SUSPECTED -> NodeStatus.SUSPECTED
                com.example.cache.proto.NodeStatus.DOWN -> NodeStatus.DOWN
                com.example.cache.proto.NodeStatus.LEAVING -> NodeStatus.LEAVING
                com.example.cache.proto.NodeStatus.LEFT -> NodeStatus.LEFT
                else -> NodeStatus.ALIVE
            }

            failureDetector.updateNode(
                memberInfo.id,
                memberInfo.address,
                memberInfo.port,
                status,
                memberInfo.incarnation,
                Instant.ofEpochMilli(memberInfo.timestamp)
            )
        }
    }

    /**
     * Gracefully leave the cluster.
     *
     * Announces departure to all known nodes and shuts down gossip service.
     */
    fun leaveCluster(timeout: Duration = Duration.ofSeconds(5)) {
        println("Node ${localNode.id} leaving cluster gracefully")

        // Mark ourselves as LEAVING
        failureDetector.updateNode(
            localNode.id,
            localNode.address.hostString,
            localNode.address.port,
            NodeStatus.LEAVING,
            failureDetector.incrementIncarnation(localNode.id),
            Instant.now()
        )

        // Create LEAVE message
        val leaveMessage = createLeaveMessage()

        // Send LEAVE message to all alive nodes
        val aliveNodes = failureDetector.getAliveNodes()
            .filter { it.id != localNode.id }

        val leaveFutures = aliveNodes.map { node ->
            CompletableFuture.runAsync {
                try {
                    gossipTransport.sendMessage(
                        node.address,
                        leaveMessage,
                        Duration.ofSeconds(1)
                    )
                    println("Sent LEAVE message to ${node.id}")
                } catch (e: Exception) {
                    println("Failed to send LEAVE to ${node.id}: ${e.message}")
                }
            }
        }

        // Wait for leave messages to be sent (with timeout)
        try {
            CompletableFuture.allOf(*leaveFutures.toTypedArray())
                .get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            println("Timeout waiting for LEAVE messages to be sent")
        }

        // Mark ourselves as LEFT
        failureDetector.updateNode(
            localNode.id,
            localNode.address.hostString,
            localNode.address.port,
            NodeStatus.LEFT,
            failureDetector.getIncarnation(localNode.id),
            Instant.now()
        )

        // Stop gossip service
        gossipService.stop()

        println("Node ${localNode.id} has left the cluster")
    }

    /**
     * Create a LEAVE message.
     */
    private fun createLeaveMessage(): GossipMessage {
        val localNodeInfo = com.example.cache.proto.NodeInfo.newBuilder()
            .setId(localNode.id)
            .setAddress(localNode.address.hostString)
            .setPort(localNode.address.port)
            .setStatus(com.example.cache.proto.NodeStatus.LEAVING)
            .setIncarnation(failureDetector.getIncarnation(localNode.id))
            .setTimestamp(Instant.now().toEpochMilli())
            .build()

        return GossipMessage.newBuilder()
            .setType(GossipMessage.MessageType.LEAVE)
            .setSender(localNodeInfo)
            .setSequenceNumber(0)
            .build()
    }

    /**
     * Get the current cluster members.
     */
    fun getClusterMembers(): List<Node> {
        return failureDetector.getAllNodes()
    }

    /**
     * Get alive cluster members (excluding local node).
     */
    fun getAliveMembers(): List<Node> {
        return failureDetector.getAliveNodes()
            .filter { it.id != localNode.id }
    }

    /**
     * Check if the node is part of a cluster.
     */
    fun isInCluster(): Boolean {
        return getAliveMembers().isNotEmpty()
    }
}
