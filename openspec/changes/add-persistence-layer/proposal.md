# Change: Add Persistence Layer

**Status: Proposed**

## Why
While the cache is primarily in-memory for performance, persistence provides critical benefits: (1) durability against node failures and restarts, (2) faster recovery by reloading from disk instead of from replicas, (3) snapshot capability for backups, and (4) protection against cluster-wide failures. This is inspired by Redis's AOF/RDB persistence and Cassandra's commit log + SSTable architecture.

## What Changes
- Implement write-ahead log (WAL/commit log) for durability
- Add periodic snapshot mechanism (RDB-style) for fast recovery
- Create asynchronous persistence to avoid blocking cache operations
- Implement log compaction and cleanup
- Add snapshot compression
- Create recovery mechanism to replay WAL and load snapshots
- Add configurable persistence modes (sync, async, disabled)
- Implement background persistence thread pool

## Impact
- Affected specs: New capability `persistence` will be created
- Affected code: New infrastructure/persistence package
- Storage: Disk space required for WAL and snapshots
- Performance: Minimal impact with async mode, higher durability with sync mode
- Recovery: Faster node restart with snapshots
- Dependencies: File I/O, optional compression library

## Design Decisions

### Persistence Strategy
- **Write-ahead log (WAL)**: Append-only log of all write operations
- **Snapshots**: Periodic full dumps of cache state
- **Hybrid recovery**: Load latest snapshot + replay WAL entries since snapshot
- **Async by default**: Writes buffered and flushed periodically (configurable)

### Storage Layout
```
data/
  node-{id}/
    wal/
      wal-0001.log
      wal-0002.log
    snapshots/
      snapshot-20250101-120000.rdb
      snapshot-20250102-120000.rdb
    metadata/
      recovery.info
```

### Performance Considerations
- Async writes: batch flush every 1 second (configurable)
- Sync writes: fsync after each operation (highest durability, lower throughput)
- Compression: Optional for snapshots (gzip/lz4)
- Log rotation: New WAL segment after size threshold
