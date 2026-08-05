# jwdb

[![CI](https://github.com/jwinter/jwdb/actions/workflows/ci.yml/badge.svg)](https://github.com/jwinter/jwdb/actions/workflows/ci.yml)

A distributed in-memory cache in Kotlin — built on personal time to learn database internals and practice AI-assisted, spec-driven development.

Inspired by ideas from Cassandra and Couchbase. Uses a functional-core / imperative-shell architecture: pure business logic at the center, I/O at the boundaries.

## Status

**Single-node cache server with Protobuf-over-TCP wire protocol.** Replication and durability are sketched as future work, not implemented.

What works today:

- Thread-safe in-memory cache backed by `ConcurrentHashMap`
- Pluggable eviction (LRU, FIFO, RANDOM) with configurable capacity
- TTL-based expiration with background cleanup
- Hit/miss, operation, and eviction statistics
- Protocol Buffers serialization
- Netty TCP server (Get / Put / Delete), default port 8080
- JUnit 5 test suite, tagged unit / integration / e2e

## Tech stack

Kotlin · Java 21 (Temurin) · Gradle (Kotlin DSL) · Netty · Protocol Buffers · JUnit 5 · ktlint

## Quick start

```bash
./gradlew build       # build
./gradlew run         # start the server on :8080
./gradlew test        # run all tests
./gradlew ktlintFormat
```

Make targets (`make build`, `make test`, `make format`, `make check`) wrap the same commands.

A DevContainer is included for a reproducible dev environment — see [`.devcontainer/`](.devcontainer/).

## Architecture

```
domain/          # pure logic, no I/O
  cache/         # core cache, eviction, stats, TTL

infrastructure/  # I/O boundaries
  network/       # Netty server, codecs
  serialization/ # Protobuf
```

Design principles: functional core / imperative shell, thread-safe by construction, observable by default, type-safe generics.

## Example

```kotlin
val cache = InMemoryCache<String>(
    maxSize = 1000,
    enableAutoCleanup = true,
)

cache.put(CacheKey("user:123"), CacheValue("John Doe", ttl = Duration.ofMinutes(5)))

when (val result = cache.get(CacheKey("user:123"))) {
    is CacheResult.Hit  -> println(result.value.data)
    is CacheResult.Miss -> println("not found")
}
```

## What's next (sketched, not promised)

Possible directions, in roughly the order I'd explore them:

| Phase | Change | Tasks | Status |
|---|---|---|---|
| 1 — single node | stats, TTL, protobuf, Netty server | 100/100 | ✅ shipped |
| 2A — single-DC replication | `add-single-dc-replication` | 28/98 | 🔨 in progress |
| 2B — cross-DC replication | `add-cross-datacenter-replication` | 0/99 | 📋 proposed |
| 3 — durability | `add-persistence-layer` | 0/77 | 📋 proposed |

Phase 2A has the domain logic — consistent hashing with virtual nodes, versioning, last-write-wins conflict resolution, and an in-process replication coordinator — but no wire protocol, so nodes can't yet talk to each other. Gossip and inter-node messaging are the critical path. See [PHASE_2A_REVIEW.md](PHASE_2A_REVIEW.md) for a component-level breakdown.

Also proposed, unscheduled: compare-and-swap (`add-cas-support`) and a smart client library (`add-smart-client-library`).

Task counts come from `openspec/changes/*/tasks.md`, which is the source of truth — not this table.

## Documentation

Deeper docs live in [`docs/`](docs/) (statistics, TTL, serialization, network server, test classification). Specs, proposals, and roadmap detail live in [`openspec/`](openspec/).

## Development approach

This is a personal learning project, not a product. It exists to get hands-on with several things at once:

- **Database internals** — moving past user-of-DB familiarity into how caches, eviction, TTL cleanup, and (eventually) replication actually work under the hood
- **Distributed systems concepts** — Cassandra- and Couchbase-style patterns for replication, consistency, and topology
- **Kotlin backend service development** — Netty, Protocol Buffers, modern JVM tooling
- **Spec-first, AI-assisted development workflows** — specifications managed with [OpenSpec](openspec/), test-driven development with classified unit / integration / e2e tests, functional-core / imperative-shell as the architectural discipline, and ktlint as the style gate
