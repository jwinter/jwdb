# Project Context

## Purpose
This project is designed to learn AI LLM-assisted programming with a focus on Kotlin backend services. The goal is to build a distributed in-memory cache with cross datacenter replication, combining concepts from Apache Cassandra and Couchbase.

## Tech Stack
- Kotlin (latest LTS version)
- Gradle (Kotlin DSL)
- Netty (for networking)
- Protocol Buffers (for serialization)
- JVM: Temurin Java 21
- Testing framework: JUnit 5

## Project Conventions

### Code Style
- Follow official Kotlin style guide
- Use ktlint for code formatting
- Follow standard Kotlin naming conventions

### Architecture Patterns
- **Functional core, imperative shell**: Pure functions at the core with imperative I/O at the boundaries (Gary Bernhardt's approach)
- Package structure: Hybrid approach
  - `domain/` - Feature-based organization for pure business logic (cache, replication, consistency)
  - `infrastructure/` - Layer-based organization for I/O boundaries (network, persistence, monitoring)

### Testing Strategy
- **Google-style test classification**: Small, medium, and large tests based on execution time and dependency requirements
  - Small: Fast, isolated unit tests
  - Medium: Integration tests with some dependencies
  - Large: End-to-end tests with full system dependencies
- Test types: Both unit and integration tests required
- Test organization: Mirror source structure
- Testing framework: JUnit 5

### Git Workflow
- Branching: Feature branches off `main`
- Commit message format: [To be decided - conventional commits vs simple messages]

## Domain Context
This is a low-latency, high-traffic data store designed to handle many concurrent connections. The system must support distributed in-memory caching with cross-datacenter replication capabilities.

### Serialization Strategy
- **Protocol Buffers (protobuf)** for all cache value serialization
  - Rationale: Multi-language client support, high performance, compact binary format, schema evolution
  - Cache keys: String-based (UTF-8 encoded)
  - Cache values: Generic type `T` internally, serialized to protobuf for network transmission
  - Benefits: Type safety across language boundaries, backward/forward compatibility, industry-standard for distributed systems
  - Future: Serialization layer will be added in the infrastructure package when implementing network protocol

## Important Constraints
- High performance is important
- Stability is more important than performance
- Must compile and run on Temurin JVM Java 21
- Kubernetes-native design preferred

## External Dependencies
- Kubernetes (for orchestration and deployment)
- [Additional external services/APIs to be documented as they are added]

## Implementation Roadmap

This project follows a phased approach, building from single-node to distributed system.

### Phase 1: Single-Node Production Ready
Build a production-quality single-node cache with comprehensive observability and automatic management.

**Goals**:
- Production-ready single-node cache
- Network protocol for client access
- Comprehensive monitoring and observability
- Automatic resource management

**Changes**:
1. `enhance-cache-stats` - Comprehensive metrics and monitoring
2. `add-cache-ttl-support` - Automatic expiration cleanup
3. `add-protobuf-support` - Serialization foundation
4. `add-netty-server` - Network protocol (single-node mode)

**Why this order**: Start simple. Build observability first (stats), complete core functionality (TTL), then add network layer. Each feature builds on the previous.

### Phase 2A: Single-Datacenter Replication
Transform to multi-node distributed cache within a single datacenter.

**Goals**:
- High availability through replication
- Fault tolerance via peer-to-peer architecture
- Tunable consistency guarantees (ONE, QUORUM, ALL)
- Learn distributed systems fundamentals in simpler environment

**Changes**:
1. `add-single-dc-replication` - Multi-node clustering with gossip protocol, consistent hashing, replication

**Dependencies**: Requires Phase 1 (network layer + serialization)

**Why after Phase 1**: Replication adds significant complexity (gossip, consistent hashing, conflict resolution). Must have solid single-node foundation and network protocol first. Start with single-DC to validate distributed primitives before adding cross-DC complexity.

### Phase 2B: Cross-Datacenter Replication
Extend to cross-datacenter replication for geographic distribution.

**Goals**:
- Geographic distribution and disaster recovery
- Low-latency local reads with eventual cross-DC consistency
- DC-aware consistency levels (LOCAL_QUORUM, EACH_QUORUM)
- DC failover and split-brain handling

**Changes**:
1. `add-cross-datacenter-replication` - DC topology, cross-DC replication, DC-aware consistency

**Dependencies**: Requires Phase 2A (single-DC replication)

**Why after Phase 2A**: Cross-DC adds WAN latency, network partitions, and complex failure modes. Must have working single-DC replication first to build upon proven gossip, consistent hashing, and replication coordinator.

### Phase 3: Durability & Recovery
Add disk-based persistence for crash recovery and long-term durability.

**Goals**:
- Survive node crashes and restarts
- Fast recovery without full replication
- Backup and restore capabilities
- Protection against cluster-wide failures

**Changes**:
1. `add-persistence-layer` - Write-ahead log and snapshots

**Dependencies**: Best implemented after replication (coordinated snapshots across cluster)

**Why last**: Persistence is independent of replication but benefits from coordinated cluster snapshots. Single-node persistence is simpler but less valuable. Multi-node persistence provides better fault tolerance. Can be implemented after Phase 2A (doesn't require cross-DC).

### Testing Philosophy by Phase

**Phase 1 Testing**:
- Unit tests: Core cache logic, eviction policies, statistics
- Integration tests: TTL cleanup, network protocol, protobuf serialization
- E2E tests: Complete request/response cycles through Netty server

**Phase 2A Testing** (single-DC distributed testing):
- Unit tests: Consistent hashing, conflict resolution, version comparison
- Integration tests: Gossip protocol, replication coordinator, hinted handoff
- E2E tests: 3-node clusters, node failures, failover scenarios
- Chaos tests: Random node failures, network delays

**Phase 2B Testing** (cross-DC distributed testing):
- Unit tests: DC topology, DC-aware placement, cross-DC conflict resolution
- Integration tests: Cross-DC replication, DC failover, DC gossip
- E2E tests: Multi-DC clusters, DC isolation, split-brain scenarios
- Chaos tests: DC failures, WAN latency simulation, cross-DC partitions

**Phase 3 Testing** (adds durability testing):
- Unit tests: WAL writing, snapshot creation, recovery logic
- Integration tests: WAL replay, snapshot loading, compaction
- E2E tests: Crash recovery, coordinated snapshots, backup/restore
- Longevity tests: Multi-day runs with persistence enabled
