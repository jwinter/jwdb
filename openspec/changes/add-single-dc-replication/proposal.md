# Change: Add Single-Datacenter Replication

**Status: Proposed**

## Why
A distributed cache must replicate data across multiple nodes within a datacenter to provide high availability and fault tolerance. This is Phase 2A - establishing the core distributed systems foundation (gossip, consistent hashing, replication) in a single datacenter before adding the complexity of cross-datacenter replication. This follows the pattern from Cassandra and Couchbase, where single-DC replication is the fundamental building block.

Without multi-node replication, the cache is a single point of failure. By implementing single-DC replication first, we can validate and refine our distributed systems implementation in a simpler environment with lower latency and fewer failure modes.

## What Changes
- Implement gossip protocol for cluster membership and failure detection within a datacenter
- Add consistent hashing for data distribution across nodes
- Implement write replication with tunable consistency (ONE, QUORUM, ALL)
- Add read repair mechanism to fix inconsistencies
- Implement versioning (timestamp + node ID) for conflict detection
- Add last-write-wins (LWW) conflict resolution strategy
- Create replication coordinator for managing replica operations
- Implement hinted handoff for temporarily unavailable nodes
- Add node health monitoring and failure detection

## Impact
- Affected specs: New capability `replication` will be created
- Affected code: New domain/replication package, updated Cache interface, new network protocol handlers
- Dependencies: Requires network layer (Netty) and serialization (protobuf) - both complete
- Architecture: Transforms from single-node to multi-node distributed system (single DC only)
- Consistency: Introduces tunable consistency levels
- Performance: Network overhead for replication, but enables high availability
- Testing: Requires multi-node testing infrastructure (docker-compose or testcontainers)

## Design Decisions

### Replication Strategy
- **Peer-to-peer**: No master node, all nodes are equal (Cassandra-style)
- **Replication factor**: Configurable number of replicas (default: 3)
- **Consistency levels**: ONE, QUORUM, ALL for reads and writes
- **Conflict resolution**: Last-write-wins using timestamp + node ID
- **Scope**: Single datacenter only - no datacenter awareness yet

### Cluster Membership
- **Gossip protocol**: SWIM-based failure detection
- **Ring topology**: Consistent hashing with virtual nodes (vnodes)
- **Seed nodes**: Bootstrap mechanism for new nodes joining
- **Failure detection**: Suspicion → failure transition with configurable timeouts

### Data Distribution
- **Consistent hashing**: Minimal data movement when nodes join/leave
- **Virtual nodes**: Improve load balancing (256 vnodes per physical node)
- **Replica placement**: Adjacent nodes on the ring (no datacenter/rack awareness)
- **Coordinator node**: Any node can coordinate any request

### Write Path
- **Coordinator**: Hash key to determine replicas, send to N replicas
- **Consistency levels**: Return when ONE/QUORUM/ALL replicas acknowledge
- **Async replication**: Remaining replicas updated asynchronously
- **Hinted handoff**: Store hints when replica is unavailable

### Read Path
- **Coordinator**: Query replicas based on consistency level
- **Version comparison**: Return latest version to client
- **Read repair**: Asynchronously repair stale replicas
- **Digest queries**: Efficient version checking before fetching full data

## Out of Scope (Future: Cross-Datacenter Replication)
- Datacenter/rack awareness and topology
- Cross-datacenter replication and consistency
- Different replication strategies per datacenter
- DC-local vs global consistency levels
- WAN-optimized protocols and conflict resolution

## Dependencies
- ✅ Netty network server (completed in add-netty-server)
- ✅ Protocol Buffers serialization (completed in add-protobuf-support)

## Testing Strategy
- Unit tests: Consistent hashing, version comparison, conflict resolution
- Integration tests: Multi-node operations, gossip, read repair, hinted handoff
- E2E tests: 3-node cluster scenarios with various failure modes
- Chaos tests: Random failures, network delays (new test category)
- Use docker-compose or testcontainers for multi-node test infrastructure

## Success Criteria
- 3-node cluster successfully replicates data with RF=3
- Write with QUORUM consistency waits for 2/3 nodes
- Read repair fixes inconsistencies discovered during reads
- Node failure triggers hinted handoff
- Gossip protocol detects failed nodes within expected timeout
- All tests pass including new chaos tests
