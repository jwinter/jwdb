# Distributed In-Memory Cache

A high-performance, production-ready distributed in-memory cache written in Kotlin, combining concepts from Apache Cassandra and Couchbase. Built for learning AI LLM-assisted programming with a focus on real-world backend service development.

## Overview

This project implements a distributed caching system with cross-datacenter replication capabilities, designed for low-latency, high-traffic data storage. The architecture follows a **functional core, imperative shell** pattern (Gary Bernhardt's approach) with pure business logic at the core and imperative I/O at the boundaries.

### Current Status: Phase 1 Complete

Phase 1 (Single-Node Production Ready) is **COMPLETE**. The system currently provides a fully functional single-node cache server with comprehensive observability, automatic resource management, and network protocol support.

## Features

### Completed (Phase 1)

- **High-Performance In-Memory Cache**
  - Thread-safe operations using `ConcurrentHashMap`
  - Multiple eviction policies (LRU, FIFO, RANDOM)
  - Configurable capacity limits
  - Type-safe generic interface

- **Comprehensive Statistics & Monitoring**
  - Hit/miss rates with detailed tracking
  - Operation counts (get, put, delete, clear)
  - Eviction metrics by policy type
  - Cleanup operation statistics
  - Formatted output for logging and monitoring
  - Statistics reset capability

- **Automatic TTL-based Expiration**
  - Background cleanup of expired entries
  - Configurable cleanup intervals (default: 60s)
  - Graceful lifecycle management
  - Memory efficiency without manual intervention

- **Protocol Buffers Serialization**
  - High-performance binary serialization
  - Multi-language client support
  - Schema evolution capabilities
  - Type-safe serialization interface

- **Netty-based Network Server**
  - Production-grade TCP server
  - Protocol Buffers message encoding/decoding
  - Configurable thread pools
  - Graceful start/stop lifecycle
  - Support for Get, Put, Delete operations
  - Default port: 8080

### Planned (Future Phases)

**Phase 2A: Single-Datacenter Replication**
- Peer-to-peer replication with gossip protocol
- Consistent hashing for data distribution
- Tunable consistency (ONE, QUORUM, ALL)
- Read repair and hinted handoff
- High availability within datacenter

**Phase 2B: Cross-Datacenter Replication (XDCR)**
- DC-aware topology and replica placement
- Cross-datacenter asynchronous replication
- DC-local consistency levels (LOCAL_QUORUM, EACH_QUORUM)
- Datacenter failover and disaster recovery
- Geographic distribution

**Phase 3: Durability & Recovery**
- Write-ahead log (WAL) for crash recovery
- Snapshot-based persistence
- Fast recovery without full replication
- Backup and restore capabilities

## Quick Start

### Prerequisites

- Java 21 (Temurin distribution recommended)
- Gradle (wrapper included)
- Make (optional, for convenience targets)

### Build and Run

```bash
# Build the project
./gradlew build

# Or using Make
make build

# Run the server
./gradlew run

# The server will start on port 8080
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run only unit tests (fast, isolated)
./gradlew testUnit

# Run only integration tests (some dependencies)
./gradlew testIntegration

# Run only end-to-end tests (full system)
./gradlew testE2e
```

### Code Quality

```bash
# Format code with ktlint
./gradlew ktlintFormat
# Or: make format

# Check code style
./gradlew ktlintCheck
# Or: make check
```

## Architecture

### Package Structure

The project uses a hybrid package organization:

- **`domain/`** - Feature-based organization for pure business logic
  - `cache/` - Core cache operations, eviction policies, statistics

- **`infrastructure/`** - Layer-based organization for I/O boundaries
  - `network/` - Netty server, protocol handlers, message codecs
  - `serialization/` - Protocol Buffers serialization layer

### Design Principles

1. **Functional Core, Imperative Shell** - Pure functions for business logic, side effects at boundaries
2. **Thread Safety** - Concurrent access support using proven patterns
3. **Type Safety** - Generic interfaces with compile-time guarantees
4. **Performance** - High performance is important, but stability comes first
5. **Observability** - Comprehensive metrics and monitoring built-in

## Technology Stack

- **Language**: Kotlin (latest LTS version)
- **Build Tool**: Gradle (Kotlin DSL)
- **JVM**: Temurin Java 21
- **Testing**: JUnit 5 with Google-style test classification
- **Networking**: Netty (high-performance async I/O)
- **Serialization**: Protocol Buffers
- **Code Quality**: ktlint for Kotlin style enforcement

## Development

### Test Classification

Tests are organized using Google-style classification:

- **Unit Tests** (`@Tag("unit")`) - Fast, isolated tests with no external dependencies
- **Integration Tests** (`@Tag("integration")`) - Tests with some dependencies or component interactions
- **End-to-End Tests** (`@Tag("e2e")`) - Full system tests with real dependencies

Future phases will add:
- **Chaos Tests** (`@Tag("chaos")`) - Random failure injection and stress testing
- **Longevity Tests** (`@Tag("longevity")`) - Multi-day stability testing

### Development Environment

A DevContainer configuration is included for consistent development environments:

```bash
# Using the DevContainer
# Open in VS Code with Dev Containers extension
# Or manually with Docker:
cd .devcontainer
docker-compose up -d
```

See [.devcontainer/](.devcontainer/) for Docker alternatives (Colima, Rancher Desktop, etc.)

### Makefile Targets

```bash
make help          # Show all available targets
make build         # Build the project
make test          # Run all tests
make format        # Format code with ktlint
make check         # Check code style
make clean         # Clean build artifacts

# Docker/Colima management (macOS)
make colima-install   # Install Colima
make colima-start     # Start Colima with recommended resources
make colima-stop      # Stop Colima
make colima-status    # Check Colima status
```

## API Usage

### Starting the Server

```kotlin
import domain.cache.InMemoryCache
import infrastructure.network.CacheServer
import infrastructure.network.ServerConfig

// Create cache instance
val cache = InMemoryCache<ByteArray>(
    maxSize = 10000,
    enableAutoCleanup = true,
    cleanupIntervalSeconds = 60
)

// Configure and start server
val config = ServerConfig(port = 8080, workerThreads = 4)
val server = CacheServer(cache, config)
server.start()

// Graceful shutdown
Runtime.getRuntime().addShutdownHook(Thread {
    server.stop()
    cache.shutdown()
})
```

### Cache Operations

```kotlin
import domain.cache.InMemoryCache
import domain.cache.CacheKey
import domain.cache.CacheValue
import java.time.Duration

val cache = InMemoryCache<String>(maxSize = 1000)

// Put with optional TTL
val key = CacheKey("user:123")
val value = CacheValue("John Doe", ttl = Duration.ofMinutes(5))
cache.put(key, value)

// Get
when (val result = cache.get(key)) {
    is CacheResult.Hit -> println("Value: ${result.value.data}")
    is CacheResult.Miss -> println("Key not found")
}

// Delete
cache.delete(key)

// Get statistics
val stats = cache.getStats()
println(cache.getStatsFormatted())
```

## Documentation

- [PROJECT_STATUS.md](PROJECT_STATUS.md) - Current implementation status and roadmap
- [docs/CACHE_STATISTICS.md](docs/CACHE_STATISTICS.md) - Cache statistics and monitoring
- [docs/CACHE_TTL.md](docs/CACHE_TTL.md) - TTL and expiration mechanisms
- [docs/SERIALIZATION.md](docs/SERIALIZATION.md) - Protocol Buffers serialization
- [docs/NETWORK_SERVER.md](docs/NETWORK_SERVER.md) - Netty server implementation
- [docs/TEST_CLASSIFICATION.md](docs/TEST_CLASSIFICATION.md) - Testing strategy and guidelines

### OpenSpec Documentation

This project uses OpenSpec for specification-driven development:

- [openspec/project.md](openspec/project.md) - Project context and conventions
- [openspec/specs/](openspec/specs/) - Living specifications for all features
  - [cache-operations](openspec/specs/cache-operations/spec.md) - Core cache behavior
  - [serialization](openspec/specs/serialization/spec.md) - Serialization layer
  - [test-classification](openspec/specs/test-classification/spec.md) - Test organization
  - [development-workflow](openspec/specs/development-workflow/spec.md) - Build and tooling
  - [project-foundation](openspec/specs/project-foundation/spec.md) - Build configuration

## Project Roadmap

### Phase 1: Single-Node Production Ready (COMPLETE)

All Phase 1 features are implemented and tested:

1. Enhanced cache statistics with comprehensive metrics
2. Automatic TTL-based expiration cleanup
3. Protocol Buffers serialization layer
4. Netty-based network server with TCP protocol

**Status**: Production-ready single-node cache server

### Phase 2A: Single-Datacenter Replication (Proposed)

Transform to multi-node distributed cache within a single datacenter:

- Peer-to-peer replication with gossip protocol
- Consistent hashing for data distribution
- Tunable consistency (ONE, QUORUM, ALL)
- Read repair and hinted handoff
- Node failure detection and recovery

**Dependencies**: Phase 1 complete (network layer + serialization)

### Phase 2B: Cross-Datacenter Replication (Proposed)

Extend to cross-datacenter replication for geographic distribution:

- DC-aware topology and rack placement
- Asynchronous cross-DC replication
- DC-local consistency (LOCAL_QUORUM, EACH_QUORUM)
- Datacenter failover and split-brain handling
- Cross-DC conflict resolution

**Dependencies**: Phase 2A complete (single-DC replication)

### Phase 3: Durability & Recovery (Proposed)

Add persistence for crash recovery:

- Write-ahead log (WAL) for operation durability
- Snapshot-based state persistence
- Fast recovery without full cluster sync
- Coordinated snapshots across cluster
- Backup and restore capabilities

**Dependencies**: Phase 2A recommended (coordinated cluster snapshots)

## Performance Characteristics

Current single-node performance (Phase 1):

- Thread-safe concurrent operations
- O(1) get/put/delete operations
- Background TTL cleanup (non-blocking)
- Configurable eviction policies
- Low-latency network protocol

## Contributing

This is a learning project for AI LLM-assisted programming. The development follows:

- Specification-driven development with OpenSpec
- Test-driven development with comprehensive test coverage
- Functional core with imperative shell architecture
- Code review and quality checks via ktlint

## License

[License information to be added]

## Acknowledgments

Built as a learning project exploring:
- Kotlin backend service development
- Distributed systems concepts (Cassandra, Couchbase patterns)
- AI LLM-assisted programming workflows
- Specification-driven development with OpenSpec
