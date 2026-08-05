# Phase 2A: Single-Datacenter Replication - Status Review

**Date**: 2026-02-01
**Status**: In Progress (~30% complete)

## Overview

Phase 2A transforms the distributed cache from a single-node system to a multi-node replicated cluster within a single datacenter. This is the foundational distributed systems work that enables high availability and fault tolerance.

## What's Been Completed

### 1. Core Data Structures (100% complete)

| Component | File | Description |
|-----------|------|-------------|
| `Node` | `domain/replication/Node.kt` | Node representation with status tracking (ALIVE, SUSPECTED, DOWN, LEAVING, LEFT) |
| `Version` | `domain/replication/Version.kt` | Distributed versioning with timestamp + nodeId for LWW conflict resolution |
| `ConsistencyLevel` | `domain/replication/ConsistencyLevel.kt` | ONE, QUORUM, ALL with quorum calculation |

### 2. Consistent Hashing (100% complete)

| Component | File | Description |
|-----------|------|-------------|
| `ConsistentHashRing` | `domain/replication/ConsistentHashRing.kt` | Full implementation with virtual nodes (256 vnodes per physical node) |
| `VirtualNode` | (same file) | Virtual node wrapper for load distribution |
| `RingStats` | (same file) | Statistics for monitoring |

**Features implemented:**
- Thread-safe ring operations (ReentrantReadWriteLock)
- MD5-based hashing for consistent placement
- `getReplicaNodes()` returns N distinct physical nodes, skipping dead nodes
- Add/remove node with automatic vnode management

### 3. Conflict Resolution (100% complete)

| Component | File | Description |
|-----------|------|-------------|
| `ConflictResolver` | `domain/replication/ConflictResolver.kt` | Strategy interface |
| `LastWriteWinsResolver` | (same file) | LWW implementation using Version comparison |

### 4. Replication Coordinator (80% complete)

| Component | File | Description |
|-----------|------|-------------|
| `ReplicationCoordinator` | `domain/replication/ReplicationCoordinator.kt` | Interface defining replicated operations |
| `ReplicationConfig` | (same file) | Configuration with RF, consistency defaults, feature flags |
| `ReplicationException` | (same file) | Exception for replication failures |
| `SimpleReplicationCoordinator` | `domain/replication/SimpleReplicationCoordinator.kt` | In-process implementation for testing |

**What's implemented:**
- `replicatedPut()` with consistency levels
- `replicatedGet()` with conflict resolution
- `replicatedDelete()` with consistency levels
- Parallel replica operations using CompletableFuture
- Timeout handling

**What's stubbed (TODOs in code):**
- Hinted handoff storage for failed replicas
- Read repair when versions differ

### 5. Test Coverage (Solid for completed components)

| Test File | Coverage |
|-----------|----------|
| `ConsistentHashRingTest.kt` | 24 test cases |
| `VersionTest.kt` | Part of 27 test cases |
| `ConflictResolverTest.kt` | Part of 27 test cases |
| `ConsistencyLevelTest.kt` | Part of 31 test cases |
| `SimpleReplicationCoordinatorTest.kt` | Part of 31 test cases |

---

## What Remains to Be Done

### High Priority (Core Functionality)

#### 1. Gossip Protocol (0% complete) - Critical Path
The gossip protocol is essential for cluster membership and failure detection.

**Tasks:**
- [ ] Define `GossipMessage` protobuf schema
- [ ] Implement SWIM-based failure detection state machine
- [ ] Create `GossipService` for periodic state exchange
- [ ] Add suspicion and failure detection logic
- [ ] Implement node join protocol with seed nodes
- [ ] Implement node leave protocol (graceful shutdown)
- [ ] Add gossip message propagation and rumor spreading
- [ ] Configure gossip interval and timeouts

**Estimated scope:** ~500-800 lines of code + tests

#### 2. Network Protocol Extensions (0% complete) - Critical Path
Inter-node communication for replication operations.

**Tasks:**
- [ ] Add `ReplicationRequest/Response` protobuf messages
- [ ] Add `GossipRequest/Response` protobuf messages
- [ ] Implement inter-node communication handlers in Netty
- [ ] Add connection pooling for replica nodes
- [ ] Handle network failures and retries
- [ ] Add request routing to coordinator

**Dependencies:** Requires Netty server (complete) and protobuf (complete)

#### 3. Write Path Integration (0% complete)
Connect the replication coordinator to actual cache operations.

**Tasks:**
- [ ] Modify `put()` to coordinate writes across replicas
- [ ] Implement ONE consistency (return after first replica)
- [ ] Implement QUORUM consistency (wait for majority)
- [ ] Implement ALL consistency (wait for all replicas)
- [ ] Add asynchronous replication for remaining nodes
- [ ] Handle partial write failures gracefully
- [ ] Add write statistics and monitoring

#### 4. Read Path Integration (0% complete)
Connect reads to the replication coordinator.

**Tasks:**
- [ ] Modify `get()` to coordinate reads across replicas
- [ ] Implement ONE consistency for reads
- [ ] Implement QUORUM consistency for reads
- [ ] Implement read repair when versions differ
- [ ] Add digest queries for efficient version comparison
- [ ] Handle read failures and timeouts

### Medium Priority (Reliability Features)

#### 5. Hinted Handoff (0% complete)
Store hints for temporarily unavailable nodes.

**Tasks:**
- [ ] Create `HintedHandoff` storage interface
- [ ] Implement in-memory hint storage
- [ ] Store hints when replica is unavailable during write
- [ ] Add background hint replay when node recovers
- [ ] Implement hint expiration and cleanup (default: 3 hours)
- [ ] Add hint storage statistics

#### 6. Read Repair (0% complete)
Fix inconsistencies discovered during reads.

**Tasks:**
- [ ] Detect version mismatch during read
- [ ] Asynchronously update stale replicas
- [ ] Log repair operations for monitoring

#### 7. Cluster Topology Management (Partially complete)
- [x] Consistent hash ring
- [x] Replica placement strategy
- [ ] `ClusterTopology` class for managing ring membership
- [ ] Token range calculation and assignment

### Lower Priority (Infrastructure & Testing)

#### 8. Configuration (0% complete)
- [ ] Add replication configuration (factor, consistency defaults)
- [ ] Add gossip configuration (interval, timeouts, seed nodes)
- [ ] Add cluster configuration (node ID, listen address/port)
- [ ] Create configuration validation
- [ ] Add configuration file support (YAML or properties)

#### 9. Integration Tests (0% complete)
- [ ] Setup docker-compose for 3-node cluster
- [ ] Test 3-node cluster startup and gossip convergence
- [ ] Test write/read with various consistency levels
- [ ] Test node join/leave
- [ ] Add `@Tag("multi-node")` for distributed tests

#### 10. E2E Tests (0% complete)
- [ ] Test mixed consistency levels
- [ ] Test node failure during operations
- [ ] Test concurrent writes to same key
- [ ] Test full cluster restart

#### 11. Chaos Tests (0% complete)
- [ ] Random node failures during sustained load
- [ ] Network delays and packet loss
- [ ] Rolling restart of all nodes
- [ ] Add `@Tag("chaos")` for chaos tests

#### 12. Documentation (0% complete)
- [ ] Document single-DC replication architecture
- [ ] Document consistency levels and trade-offs
- [ ] Add cluster setup guide
- [ ] Create `docs/SINGLE_DC_REPLICATION.md`
- [ ] Update README

---

## Architecture Summary

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client Request                          │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Coordinator Node (any node)                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              ReplicationCoordinator                      │   │
│  │  • Determines replicas via ConsistentHashRing           │   │
│  │  • Manages consistency levels (ONE/QUORUM/ALL)          │   │
│  │  • Handles timeouts and failures                        │   │
│  │  • Triggers read repair (TODO)                          │   │
│  │  • Stores hints for failed nodes (TODO)                 │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        ▼                       ▼                       ▼
┌───────────────┐       ┌───────────────┐       ┌───────────────┐
│   Replica 1   │       │   Replica 2   │       │   Replica 3   │
│ (Primary)     │       │               │       │               │
│               │       │               │       │               │
│ ┌───────────┐ │       │ ┌───────────┐ │       │ ┌───────────┐ │
│ │   Cache   │ │       │ │   Cache   │ │       │ │   Cache   │ │
│ │ (Local)   │ │       │ │ (Local)   │ │       │ │ (Local)   │ │
│ └───────────┘ │       │ └───────────┘ │       │ └───────────┘ │
│               │       │               │       │               │
│ ┌───────────┐ │       │ ┌───────────┐ │       │ ┌───────────┐ │
│ │  Gossip   │◄┼──────►│ │  Gossip   │◄┼──────►│ │  Gossip   │ │
│ │  Service  │ │       │ │  Service  │ │       │ │  Service  │ │
│ └───────────┘ │       │ └───────────┘ │       │ └───────────┘ │
└───────────────┘       └───────────────┘       └───────────────┘
        │                       │                       │
        └───────────────────────┼───────────────────────┘
                                │
                    ┌───────────┴───────────┐
                    │  ConsistentHashRing   │
                    │  (256 vnodes/node)    │
                    └───────────────────────┘
```

---

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Topology | Peer-to-peer (no master) | Cassandra-style, avoids SPOF |
| Replication Factor | Configurable (default: 3) | Standard for fault tolerance |
| Conflict Resolution | Last-Write-Wins (LWW) | Simple, deterministic |
| Failure Detection | SWIM-based gossip | Scalable, proven in production |
| Virtual Nodes | 256 per physical node | Good load balancing |
| Hash Function | MD5 (first 8 bytes) | Consistent, well-distributed |

---

## Dependencies

| Dependency | Status | Notes |
|------------|--------|-------|
| Netty network server | ✅ Complete | Phase 1 |
| Protocol Buffers | ✅ Complete | Phase 1 |
| CacheValue with Version | ✅ Complete | Updated in Phase 2A |
| Protobuf Version message | ✅ Complete | Added in Phase 2A |

---

## Recommended Next Steps

1. **Gossip Protocol** - This is the critical path. Without gossip, nodes can't discover each other or detect failures.

2. **Network Protocol Extensions** - Once gossip works, need the actual network messages for replication.

3. **Write/Read Path Integration** - Connect the coordinator to real network operations.

4. **Docker-compose Setup** - Enable integration testing with real multi-node clusters.

5. **Hinted Handoff & Read Repair** - Add reliability features.

---

## Task Summary

| Category | Total Tasks | Completed | Remaining |
|----------|-------------|-----------|-----------|
| Cluster Membership | 6 | 4 | 2 |
| Gossip Protocol | 8 | 0 | 8 |
| Versioning & Conflict | 6 | 6 | 0 |
| Replication Coordinator | 7 | 7 | 0 |
| Write Path | 7 | 0 | 7 |
| Read Path | 6 | 0 | 6 |
| Hinted Handoff | 6 | 0 | 6 |
| Network Protocol | 6 | 0 | 6 |
| Configuration | 5 | 0 | 5 |
| Unit Tests | 12 | 9 | 3 |
| Integration Tests | 10 | 0 | 10 |
| E2E Tests | 7 | 0 | 7 |
| Chaos Tests | 5 | 0 | 5 |
| Documentation | 7 | 0 | 7 |
| **Total** | **98** | **26** | **72** |

**Overall Progress: ~27%**
