package domain.replication

import java.net.InetSocketAddress

/**
 * Represents a node in the distributed cache cluster.
 *
 * @property id Unique identifier for the node (typically UUID or hostname)
 * @property address Network address of the node
 * @property status Current status of the node
 */
data class Node(
    val id: String,
    val address: InetSocketAddress,
    val status: NodeStatus = NodeStatus.ALIVE,
) {
    override fun toString(): String = "$id@${address.hostString}:${address.port} [$status]"
}

/**
 * Represents the operational status of a node in the cluster.
 */
enum class NodeStatus {
    /**
     * Node is alive and responding to requests
     */
    ALIVE,

    /**
     * Node is suspected to have failed (missed heartbeats)
     * but not yet confirmed as down
     */
    SUSPECTED,

    /**
     * Node has been confirmed as down/failed
     */
    DOWN,

    /**
     * Node is in the process of leaving the cluster gracefully
     */
    LEAVING,

    /**
     * Node has left the cluster
     */
    LEFT,
}
