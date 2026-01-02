package domain.gossip

import java.time.Duration

/**
 * Configuration for the gossip protocol.
 *
 * @property gossipInterval Interval between gossip rounds (default: 1 second)
 * @property pingTimeout Timeout for ping/ACK round trip (default: 500ms)
 * @property suspicionTimeout How long to wait before confirming a suspected node as down (default: 10 seconds)
 * @property indirectPingNodes Number of nodes to use for indirect pings (default: 3)
 * @property fanout Number of nodes to propagate rumors to (default: 3)
 * @property piggybackSize Number of membership entries to piggyback on messages (default: 5)
 */
data class GossipConfig(
    val gossipInterval: Duration = Duration.ofSeconds(1),
    val pingTimeout: Duration = Duration.ofMillis(500),
    val suspicionTimeout: Duration = Duration.ofSeconds(10),
    val indirectPingNodes: Int = 3,
    val fanout: Int = 3,
    val piggybackSize: Int = 5
) {
    init {
        require(!gossipInterval.isNegative && !gossipInterval.isZero) {
            "Gossip interval must be positive"
        }
        require(!pingTimeout.isNegative && !pingTimeout.isZero) {
            "Ping timeout must be positive"
        }
        require(!suspicionTimeout.isNegative && !suspicionTimeout.isZero) {
            "Suspicion timeout must be positive"
        }
        require(indirectPingNodes > 0) {
            "Indirect ping nodes must be positive"
        }
        require(fanout > 0) {
            "Fanout must be positive"
        }
        require(piggybackSize > 0) {
            "Piggyback size must be positive"
        }
        require(pingTimeout < gossipInterval) {
            "Ping timeout must be less than gossip interval"
        }
    }
}
