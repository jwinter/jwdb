## ADDED Requirements

### Requirement: Cluster Membership Management
The system SHALL manage cluster membership within a single datacenter using a gossip protocol.

#### Scenario: Node joins cluster
- **WHEN** a new node starts and joins the cluster
- **THEN** it contacts seed nodes to learn cluster topology
- **AND** its membership is propagated to all nodes via gossip
- **AND** it receives its position in the consistent hash ring
- **AND** other nodes become aware of it within gossip interval (default: 1 second)

#### Scenario: Node failure detection
- **WHEN** a node becomes unresponsive
- **THEN** neighboring nodes detect failure via gossip heartbeats
- **AND** the node is marked as suspected after missed heartbeats (default: 3 intervals)
- **AND** the node is marked as down after suspicion timeout (default: 10 seconds)
- **AND** failure status propagates via gossip to all nodes

#### Scenario: Node leaves gracefully
- **WHEN** a node shuts down gracefully
- **THEN** it announces departure via gossip
- **AND** it transfers its data to remaining replica nodes
- **AND** its departure is propagated to all cluster members
- **AND** the consistent hash ring is updated

### Requirement: Data Replication
The system SHALL replicate data across multiple nodes within a datacenter based on replication factor.

#### Scenario: Write with replication factor 3
- **WHEN** a write operation is performed
- **THEN** the coordinator determines 3 replica nodes via consistent hashing
- **AND** the replicas are adjacent nodes on the hash ring
- **AND** the write is sent to all 3 replicas
- **AND** each replica stores the data with version information (timestamp + node ID)
- **AND** success depends on configured consistency level

#### Scenario: Successful write with QUORUM consistency
- **WHEN** a write is performed with QUORUM consistency and RF=3
- **THEN** the coordinator waits for 2 out of 3 replicas to acknowledge
- **AND** the write returns success to the client
- **AND** remaining replicas are updated asynchronously
- **AND** version information ensures consistency

#### Scenario: Handle replica node failure during write
- **WHEN** a replica node is down during a write
- **THEN** the coordinator stores a hint for the failed replica
- **AND** the write succeeds if consistency level is met by available replicas
- **AND** the hint is replayed when the node recovers
- **AND** the client is not blocked by the failure

### Requirement: Tunable Consistency
The system SHALL support multiple consistency levels for reads and writes within a datacenter.

#### Scenario: Write with ONE consistency
- **WHEN** a write is performed with ONE consistency
- **THEN** the coordinator returns success after first replica acknowledges
- **AND** other replicas are updated asynchronously
- **AND** low latency is prioritized over durability

#### Scenario: Write with ALL consistency
- **WHEN** a write is performed with ALL consistency
- **THEN** the coordinator waits for all replicas to acknowledge
- **AND** the write fails if any replica is unavailable
- **AND** strong durability is guaranteed

#### Scenario: Read with QUORUM consistency
- **WHEN** a read is performed with QUORUM consistency and RF=3
- **THEN** the coordinator queries 2 out of 3 replicas
- **AND** it returns the value with the latest version
- **AND** it performs read repair if versions differ across replicas

#### Scenario: Read with ONE consistency
- **WHEN** a read is performed with ONE consistency
- **THEN** the coordinator queries the closest replica only
- **AND** it returns the value immediately
- **AND** no read repair is performed
- **AND** low latency is prioritized

### Requirement: Conflict Resolution
The system SHALL detect and resolve conflicts using versioning within a datacenter.

#### Scenario: Detect conflicting writes
- **WHEN** the same key is written to different replicas concurrently
- **THEN** each replica stores the write with timestamp and node ID
- **AND** version comparison detects the conflict during read or repair
- **AND** the conflict resolver determines the winning version

#### Scenario: Resolve conflict with last-write-wins
- **WHEN** conflicting versions exist for a key
- **THEN** the version with the latest timestamp wins
- **AND** ties are broken using node ID comparison (lexicographic)
- **AND** the winning version is propagated during read repair
- **AND** stale versions are discarded

### Requirement: Read Repair
The system SHALL repair inconsistencies discovered during reads within a datacenter.

#### Scenario: Detect version mismatch during read
- **WHEN** a read queries multiple replicas
- **THEN** the coordinator compares version information
- **AND** it detects if replicas have different versions
- **AND** it returns the latest version to the client
- **AND** it asynchronously updates stale replicas

#### Scenario: Read repair updates stale replica
- **WHEN** read repair detects a stale replica
- **THEN** it sends the latest version to the stale replica asynchronously
- **AND** the stale replica updates its local storage
- **AND** the repair does not block the client response
- **AND** repair failures are logged but do not fail the read

### Requirement: Hinted Handoff
The system SHALL store hints for temporarily unavailable nodes within a datacenter.

#### Scenario: Store hint for unavailable replica
- **WHEN** a write cannot reach a replica node
- **THEN** the coordinator stores a hint locally
- **AND** the hint includes the key, value, version, and target node
- **AND** the hint is persisted in memory with TTL (default: 3 hours)
- **AND** hints have configurable maximum storage limit

#### Scenario: Replay hints when node recovers
- **WHEN** a previously down node becomes available
- **THEN** nodes with hints for it detect recovery via gossip
- **AND** stored hints are replayed to the recovered node
- **AND** hints are deleted after successful replay
- **AND** the recovered node catches up on missed writes

#### Scenario: Hint expiration
- **WHEN** a hint reaches its TTL without replay
- **THEN** the hint is discarded
- **AND** the missed write will be repaired via read repair
- **AND** hint expiration is logged for monitoring

### Requirement: Consistent Hashing
The system SHALL use consistent hashing for data distribution within a datacenter.

#### Scenario: Determine replica nodes for a key
- **WHEN** a key needs to be stored or retrieved
- **THEN** the consistent hash function determines the primary node
- **AND** the replication factor determines N-1 additional replica nodes
- **AND** replicas are the next N-1 nodes clockwise on the ring
- **AND** the same key always maps to the same set of replicas

#### Scenario: Use virtual nodes for distribution
- **WHEN** a physical node joins the cluster
- **THEN** it is assigned multiple virtual nodes (default: 256)
- **AND** virtual nodes are distributed across the hash ring
- **AND** this improves load balancing across nodes
- **AND** each vnode owns a token range on the ring

#### Scenario: Handle node addition to cluster
- **WHEN** a new node joins the cluster
- **THEN** only keys in its token ranges are rebalanced
- **AND** data is streamed from previous owners to the new node
- **AND** minimal data movement occurs (approximately 1/N where N is cluster size)
- **AND** the cluster remains available during rebalancing

#### Scenario: Handle node removal from cluster
- **WHEN** a node leaves the cluster
- **THEN** its token ranges are reassigned to remaining nodes
- **AND** replicas are promoted to maintain replication factor
- **AND** data rebalancing happens automatically
- **AND** no data is lost if RF ≥ 2

### Requirement: Coordinator Role
The system SHALL allow any node to act as coordinator for any request.

#### Scenario: Client sends request to any node
- **WHEN** a client sends a request to any cluster node
- **THEN** that node acts as the coordinator for the request
- **AND** the coordinator determines replica nodes via consistent hashing
- **AND** the coordinator manages replication and consistency
- **AND** the coordinator returns the result to the client

#### Scenario: Coordinator handles replica timeout
- **WHEN** a replica does not respond within timeout (default: 1 second)
- **THEN** the coordinator treats it as failed for this request
- **AND** the coordinator tries next replica if needed for consistency
- **AND** the request fails if consistency level cannot be met
- **AND** timeout is configurable per consistency level
