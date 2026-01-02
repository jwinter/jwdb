package domain.cluster

import domain.gossip.*
import domain.replication.Node
import infrastructure.network.NettyGossipTransport
import java.net.InetSocketAddress
import java.time.Duration

/**
 * ClusterNode orchestrates all gossip protocol components for a single node in the cluster.
 *
 * This class wires together:
 * - FailureDetector: Tracks node states and detects failures
 * - GossipService: Handles periodic heartbeats and message processing
 * - MembershipManager: Manages joining and leaving the cluster
 * - NettyGossipTransport: Network layer for sending gossip messages
 *
 * Usage example:
 * ```kotlin
 * val clusterNode = ClusterNode(
 *     localNodeId = "node1",
 *     localAddress = InetSocketAddress("localhost", 7000),
 *     seedNodes = listOf(InetSocketAddress("localhost", 7001)),
 *     gossipConfig = GossipConfig()
 * )
 *
 * clusterNode.start()
 * // ... use the cluster ...
 * clusterNode.stop()
 * ```
 */
class ClusterNode(
    private val localNodeId: String,
    private val localAddress: InetSocketAddress,
    private val seedNodes: List<InetSocketAddress> = emptyList(),
    private val gossipConfig: GossipConfig = GossipConfig(),
    private val failureDetectorConfig: FailureDetectorConfig = FailureDetectorConfig()
) {
    // Core components
    private val failureDetector = FailureDetector(failureDetectorConfig)
    private val gossipTransport = NettyGossipTransport()

    private val localNode = Node(
        id = localNodeId,
        address = localAddress,
        status = domain.replication.NodeStatus.ALIVE
    )

    private val gossipService = GossipService(
        localNode = localNode,
        failureDetector = failureDetector,
        gossipTransport = gossipTransport,
        config = gossipConfig
    )

    private val membershipManager = MembershipManager(
        localNode = localNode,
        gossipService = gossipService,
        failureDetector = failureDetector,
        gossipTransport = gossipTransport,
        seedNodes = seedNodes
    )

    @Volatile
    private var running = false

    /**
     * Start the cluster node.
     *
     * This will:
     * 1. Join the cluster via seed nodes (if any)
     * 2. Start the gossip service for periodic heartbeats
     * 3. Begin failure detection
     */
    fun start(joinTimeout: Duration = Duration.ofSeconds(10)): Boolean {
        if (running) {
            println("ClusterNode $localNodeId is already running")
            return false
        }

        println("Starting ClusterNode $localNodeId at $localAddress")

        // Join the cluster
        val joined = membershipManager.joinCluster(joinTimeout)

        if (!joined && seedNodes.isNotEmpty()) {
            println("Warning: Failed to join cluster via seed nodes")
        }

        running = true
        println("ClusterNode $localNodeId started successfully")

        return true
    }

    /**
     * Stop the cluster node gracefully.
     *
     * This will:
     * 1. Announce departure to other nodes
     * 2. Stop the gossip service
     * 3. Close all network connections
     */
    fun stop(leaveTimeout: Duration = Duration.ofSeconds(5)) {
        if (!running) {
            return
        }

        println("Stopping ClusterNode $localNodeId")

        // Leave the cluster gracefully
        membershipManager.leaveCluster(leaveTimeout)

        // Shutdown transport
        gossipTransport.shutdown()

        running = false
        println("ClusterNode $localNodeId stopped")
    }

    /**
     * Get the current cluster members.
     */
    fun getClusterMembers(): List<Node> {
        return membershipManager.getClusterMembers()
    }

    /**
     * Get alive cluster members (excluding local node).
     */
    fun getAliveMembers(): List<Node> {
        return membershipManager.getAliveMembers()
    }

    /**
     * Check if the node is part of a cluster.
     */
    fun isInCluster(): Boolean {
        return membershipManager.isInCluster()
    }

    /**
     * Get the local node information.
     */
    fun getLocalNode(): Node {
        return localNode
    }

    /**
     * Get the gossip service (for advanced use cases).
     */
    fun getGossipService(): GossipService {
        return gossipService
    }

    /**
     * Get the failure detector (for monitoring/debugging).
     */
    fun getFailureDetector(): FailureDetector {
        return failureDetector
    }
}
