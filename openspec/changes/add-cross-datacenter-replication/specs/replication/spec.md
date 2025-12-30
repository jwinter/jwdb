## MODIFIED Requirements

This spec extends the `replication` capability with cross-datacenter (XDCR) features. It assumes single-datacenter replication is already implemented.

### Requirement: Datacenter-Aware Topology
The system SHALL manage datacenter and rack awareness for replica placement.

#### Scenario: Node configured with datacenter and rack
- **WHEN** a node starts with datacenter and rack configuration
- **THEN** it registers itself with DC and rack metadata
- **AND** the cluster topology tracks DC membership
- **AND** gossip propagates DC and rack information
- **AND** other nodes become aware of the DC topology

#### Scenario: DC-aware replica placement
- **WHEN** replicas are selected for a key
- **THEN** the system prefers to distribute replicas across datacenters
- **AND** within each DC, replicas are spread across racks when possible
- **AND** the same key has replicas in multiple DCs for disaster recovery
- **AND** replica placement respects per-DC replication factor configuration

### Requirement: Cross-Datacenter Consistency Levels
The system SHALL support datacenter-aware consistency levels for reads and writes.

#### Scenario: Write with LOCAL_QUORUM consistency
- **WHEN** a write is performed with LOCAL_QUORUM in a multi-DC cluster
- **THEN** the coordinator waits for quorum in local DC only
- **AND** the write returns success after local DC quorum
- **AND** cross-DC replication happens asynchronously
- **AND** low latency is achieved for local writes

#### Scenario: Write with EACH_QUORUM consistency
- **WHEN** a write is performed with EACH_QUORUM
- **THEN** the coordinator waits for quorum in each datacenter
- **AND** the write succeeds only if all DCs achieve quorum
- **AND** this provides strong consistency across DCs
- **AND** higher latency is expected due to WAN round-trips

#### Scenario: Read with LOCAL_ONE consistency
- **WHEN** a read is performed with LOCAL_ONE
- **THEN** the coordinator queries one replica in local DC only
- **AND** the read returns immediately without cross-DC queries
- **AND** this provides lowest latency for reads
- **AND** stale data may be returned if cross-DC replication is lagging

#### Scenario: Read with LOCAL_QUORUM consistency
- **WHEN** a read is performed with LOCAL_QUORUM in multi-DC cluster
- **THEN** the coordinator queries quorum in local DC only
- **AND** it returns the latest version among local replicas
- **AND** cross-DC replicas are not queried
- **AND** read repair updates local DC replicas only

### Requirement: Asynchronous Cross-Datacenter Replication
The system SHALL replicate writes across datacenters asynchronously by default.

#### Scenario: Write replicated across DCs asynchronously
- **WHEN** a write succeeds in local DC
- **THEN** the write is queued for cross-DC replication
- **AND** background workers propagate to remote DCs
- **AND** the client does not wait for cross-DC propagation
- **AND** replication lag is monitored per DC

#### Scenario: Cross-DC replication with remote DC unavailable
- **WHEN** a remote DC is unavailable during replication
- **THEN** writes continue to succeed in available DCs
- **AND** replication to unavailable DC is retried with backoff
- **AND** replication backlog is tracked
- **AND** writes propagate when remote DC recovers

#### Scenario: Cross-DC replication catch-up after recovery
- **WHEN** a DC recovers from isolation
- **THEN** it receives queued writes from other DCs
- **AND** replication backlog is processed in order
- **AND** the DC catches up to current state
- **AND** replication lag decreases to normal levels

### Requirement: Cross-Datacenter Conflict Resolution
The system SHALL detect and resolve conflicts that occur across datacenters.

#### Scenario: Detect cross-DC conflicting writes
- **WHEN** the same key is written in different DCs concurrently
- **THEN** each DC stores the write with DC ID and timestamp
- **AND** conflict is detected when replication propagates
- **AND** version comparison identifies the conflict
- **AND** conflict resolver determines winning version

#### Scenario: Resolve cross-DC conflict with DC-aware LWW
- **WHEN** conflicting versions exist across DCs
- **THEN** the version with latest timestamp wins
- **AND** ties are broken using DC ID comparison (deterministic)
- **AND** the winning version propagates to all DCs
- **AND** stale versions are replaced during replication

### Requirement: Datacenter Failover
The system SHALL support datacenter failover for high availability.

#### Scenario: Detect datacenter failure
- **WHEN** all nodes in a datacenter become unreachable
- **THEN** other DCs detect the failure via cross-DC gossip
- **AND** the failed DC is marked as unavailable
- **AND** requests are routed to healthy DCs only
- **AND** DC failure is logged and alerted

#### Scenario: Client failover to healthy datacenter
- **WHEN** local DC is unavailable
- **THEN** clients automatically failover to next-closest DC
- **AND** reads and writes succeed in the healthy DC
- **AND** data is still available via replication
- **AND** applications continue operating

#### Scenario: Datacenter recovery
- **WHEN** a failed DC recovers
- **THEN** nodes rejoin via gossip
- **AND** the DC catches up via replication backlog
- **AND** requests are gradually routed back to recovered DC
- **AND** the cluster returns to normal multi-DC operation

### Requirement: Cross-Datacenter Read Repair
The system SHALL repair inconsistencies across datacenters discovered during reads.

#### Scenario: Cross-DC read repair with version mismatch
- **WHEN** a cross-DC read detects version mismatch
- **THEN** the coordinator identifies stale DCs
- **AND** it returns latest version to client
- **AND** it asynchronously repairs stale replicas in remote DCs
- **AND** repair is queued for cross-DC propagation

#### Scenario: Prefer local DC for reads
- **WHEN** a read is performed without DC-specific consistency
- **THEN** the coordinator queries local DC replicas first
- **AND** cross-DC queries only happen if local replicas unavailable
- **AND** this minimizes read latency for local clients
- **AND** read routing is transparent to applications

### Requirement: Cross-Datacenter Gossip
The system SHALL extend gossip protocol for cross-datacenter cluster state.

#### Scenario: Cross-DC gossip propagation
- **WHEN** gossip runs in multi-DC cluster
- **THEN** within-DC gossip uses fast intervals (default: 1 second)
- **AND** cross-DC gossip uses slower intervals (default: 10 seconds)
- **AND** cluster state converges across all DCs
- **AND** DC-aware timeouts prevent false failure detection

#### Scenario: Detect datacenter network partition
- **WHEN** network partition isolates datacenters
- **THEN** each DC continues operating independently
- **AND** cross-DC gossip fails with timeout
- **AND** DC isolation is detected and logged
- **AND** within-DC operations continue normally

#### Scenario: Heal datacenter network partition
- **WHEN** network partition between DCs heals
- **THEN** cross-DC gossip resumes
- **AND** conflicting states are resolved via version comparison
- **AND** replication backlogs are exchanged and processed
- **AND** cluster state re-converges across DCs
