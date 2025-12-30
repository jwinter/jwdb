# Change: Add Cross-Datacenter Replication (XDCR)

**Status: Proposed**

## Why
After establishing single-datacenter replication, we need cross-datacenter replication (XDCR) to provide geographic distribution, disaster recovery, and low-latency access for globally distributed clients. This is Phase 2B - adding datacenter awareness and cross-DC capabilities on top of the single-DC foundation.

Cross-datacenter replication introduces unique challenges: higher latency, network partitions between DCs, and the need for different consistency guarantees. By implementing this after single-DC replication, we build on proven distributed systems primitives while adding DC-specific complexity.

## What Changes
- Add datacenter and rack awareness to cluster topology
- Implement cross-datacenter replication strategies
- Add DC-local and DC-global consistency levels
- Implement WAN-optimized replication protocols
- Add cross-DC conflict resolution strategies
- Implement datacenter failover and disaster recovery
- Add DC-aware replica placement (prefer local DC, distribute across racks)
- Extend monitoring for cross-DC metrics (latency, bandwidth)

## Impact
- Affected specs: Extends `replication` capability with XDCR features
- Affected code: Updates domain/replication package, new DC topology, updated coordinator
- Dependencies: **Requires** single-datacenter replication (add-single-dc-replication)
- Architecture: Adds multi-datacenter awareness on top of multi-node foundation
- Consistency: Introduces DC-local vs DC-global consistency trade-offs
- Performance: Higher latency for cross-DC writes, optimized for local reads
- Deployment: Requires multi-datacenter infrastructure for testing

## Design Decisions

### Datacenter Topology
- **DC awareness**: Nodes know their datacenter and rack
- **Topology configuration**: Manual DC/rack assignment via configuration
- **Ring per DC**: Each DC has its own consistent hash ring
- **Global coordination**: Cross-DC coordination for global operations

### Cross-DC Replication Strategy
- **Asynchronous by default**: Cross-DC writes are async to minimize latency
- **Configurable per-keyspace**: Some data can use sync cross-DC replication
- **Conflict resolution**: Enhanced LWW with DC-aware timestamps
- **Replication topologies**: Full mesh, star, chain (configurable)

### Consistency Levels - Extended
- **LOCAL_ONE**: Wait for one replica in local DC only
- **LOCAL_QUORUM**: Wait for quorum in local DC only
- **EACH_QUORUM**: Wait for quorum in each DC
- **GLOBAL_QUORUM**: Wait for quorum across all DCs
- **Existing levels**: ONE, QUORUM, ALL still work but are DC-aware

### Replica Placement
- **DC-aware placement**: Replicas distributed across DCs
- **Rack-aware placement**: Within DC, spread across racks
- **Preference ordering**: Local DC → same region → other regions
- **Configurable RF per DC**: Different replication factors per datacenter

### Write Path - Cross-DC
- **Local coordinator**: Accept writes in any DC
- **Local replication**: Synchronous within local DC
- **Cross-DC replication**: Asynchronous to remote DCs
- **Conflict timestamps**: Include DC and local timestamp
- **Failure handling**: Local success even if remote DC unavailable

### Read Path - Cross-DC
- **Local reads preferred**: Read from local DC replicas first
- **Cross-DC reads**: Only if local replicas unavailable
- **Digest reads**: Compare versions across DCs efficiently
- **DC-aware read repair**: Repair stale replicas in remote DCs

### Failure Scenarios
- **DC isolation**: One DC can operate independently
- **DC failover**: Clients redirect to healthy DC
- **Split-brain handling**: Conflict resolution when partition heals
- **DC recovery**: Catch up from other DCs when recovered

## Out of Scope (Future Enhancements)
- Multi-region mesh topologies (more than 2-3 DCs)
- Bandwidth throttling and QoS for cross-DC traffic
- Encryption for cross-DC communication
- Compression for cross-DC data transfer
- Custom conflict resolution strategies beyond LWW

## Dependencies
- ✅ Netty network server (completed)
- ✅ Protocol Buffers serialization (completed)
- ⏳ **Single-datacenter replication** (add-single-dc-replication) - REQUIRED FIRST

## Testing Strategy
- Unit tests: DC topology, DC-aware placement, conflict resolution
- Integration tests: 2-DC cluster with RF=3 per DC
- E2E tests: Cross-DC writes, local reads, DC failover
- Chaos tests: DC isolation, split-brain scenarios, WAN latency simulation
- Multi-DC docker-compose setup or cloud-based testing

## Success Criteria
- 2-DC cluster (3 nodes per DC) successfully replicates across DCs
- Write to DC1 with LOCAL_QUORUM returns quickly
- Read from DC2 eventually sees DC1's writes
- DC1 failure allows DC2 to continue operating
- Conflicting writes across DCs resolve consistently
- Cross-DC replication lag monitored and bounded
- All tests pass including DC-specific chaos tests

## Phasing Note
This change should **NOT** be started until `add-single-dc-replication` is complete and stable. Single-DC replication provides the foundation (gossip, consistent hashing, replication coordinator) that XDCR extends.
