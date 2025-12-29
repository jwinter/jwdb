## ADDED Requirements

### Requirement: Write-Ahead Logging
The system SHALL persist write operations to a write-ahead log for durability.

#### Scenario: Log write operation to WAL
- **WHEN** a put operation is performed on the cache
- **THEN** the operation is appended to the write-ahead log
- **AND** the log entry includes key, value, timestamp, and operation type
- **AND** the cache operation completes (async mode) or waits for flush (sync mode)
- **AND** the WAL entry is durable on disk

#### Scenario: Async WAL flush
- **WHEN** async persistence mode is enabled
- **THEN** write operations are buffered in memory
- **AND** the buffer is flushed to disk periodically (configurable interval)
- **AND** cache operations do not block waiting for disk I/O
- **AND** recent writes may be lost if process crashes before flush

#### Scenario: Sync WAL flush
- **WHEN** sync persistence mode is enabled
- **THEN** write operations are immediately flushed to disk
- **AND** fsync is called to ensure durability
- **AND** cache operations wait for disk confirmation
- **AND** no writes are lost on process crash

#### Scenario: WAL segment rotation
- **WHEN** the current WAL file reaches size threshold
- **THEN** a new WAL segment is created
- **AND** subsequent writes go to the new segment
- **AND** old segments are retained until compaction
- **AND** segment metadata tracks sequence numbers

### Requirement: Snapshot Creation
The system SHALL create periodic snapshots of cache state for fast recovery.

#### Scenario: Create background snapshot
- **WHEN** the snapshot interval elapses
- **THEN** a background task initiates snapshot creation
- **AND** a consistent point-in-time view of cache is captured
- **AND** the snapshot is written to disk asynchronously
- **AND** cache operations continue without blocking

#### Scenario: Compress snapshot
- **WHEN** snapshot compression is enabled
- **THEN** the snapshot data is compressed before writing
- **AND** compression reduces disk space usage
- **AND** compression metadata is stored with snapshot
- **AND** decompression happens transparently during recovery

#### Scenario: Maintain snapshot retention
- **WHEN** a new snapshot is successfully created
- **THEN** old snapshots beyond retention count are deleted
- **AND** at least one valid snapshot is always retained
- **AND** disk space is managed automatically
- **AND** snapshot deletion is logged

### Requirement: Recovery from Persistence
The system SHALL recover cache state from snapshots and WAL on startup.

#### Scenario: Recover from snapshot and WAL
- **WHEN** the cache starts after a restart
- **THEN** the recovery manager finds the latest valid snapshot
- **AND** it loads the snapshot into the cache
- **AND** it replays WAL entries created after the snapshot
- **AND** the cache state is restored to last persisted state

#### Scenario: Handle corrupted snapshot
- **WHEN** the latest snapshot is corrupted or incomplete
- **THEN** the recovery manager tries the previous snapshot
- **AND** it logs a warning about the corrupted file
- **AND** recovery proceeds with an older valid snapshot
- **AND** WAL replay fills in the gap

#### Scenario: Handle corrupted WAL entry
- **WHEN** a corrupted WAL entry is encountered during replay
- **THEN** the corrupted entry is logged and skipped
- **AND** recovery continues with the next valid entry
- **AND** the cache state reflects all valid entries
- **AND** administrators are notified of data loss

#### Scenario: Fast recovery with recent snapshot
- **WHEN** a snapshot was created recently
- **THEN** only a small number of WAL entries need replay
- **AND** recovery completes quickly (seconds, not minutes)
- **AND** the cache becomes available faster than rebuilding from replicas

### Requirement: WAL Compaction
The system SHALL compact write-ahead logs to reclaim disk space.

#### Scenario: Compact WAL after snapshot
- **WHEN** a snapshot is successfully created
- **THEN** WAL entries older than the snapshot timestamp can be removed
- **AND** old WAL segments are deleted by background compaction
- **AND** disk space is reclaimed
- **AND** only recent WAL segments are retained

#### Scenario: Safe WAL deletion
- **WHEN** deleting old WAL segments
- **THEN** only segments fully covered by a snapshot are deleted
- **AND** at least one full recovery path (snapshot + WAL) is maintained
- **AND** deletion does not interfere with ongoing writes
- **AND** recovery remains possible at all times

### Requirement: Persistence Modes
The system SHALL support multiple persistence modes for different durability requirements.

#### Scenario: Disabled persistence mode
- **WHEN** persistence is disabled
- **THEN** no WAL or snapshots are created
- **AND** cache operates entirely in memory
- **AND** maximum performance is achieved
- **AND** data is lost on restart

#### Scenario: Async persistence mode
- **WHEN** async persistence is enabled
- **THEN** WAL writes are buffered and flushed periodically
- **AND** snapshots are created on schedule
- **AND** cache operations have minimal latency impact
- **AND** recent writes may be lost on crash (within flush interval)

#### Scenario: Sync persistence mode
- **WHEN** sync persistence is enabled
- **THEN** every write is immediately flushed to disk
- **AND** snapshots are created on schedule
- **AND** cache operations wait for disk confirmation
- **AND** no writes are lost on crash (highest durability)

### Requirement: Persistence Monitoring
The system SHALL provide metrics and health checks for persistence operations.

#### Scenario: Monitor WAL performance
- **WHEN** persistence metrics are requested
- **THEN** statistics include WAL write rate and latency
- **AND** statistics include WAL segment count and size
- **AND** statistics include flush lag (async mode)
- **AND** statistics help identify performance issues

#### Scenario: Monitor disk space usage
- **WHEN** persistence is enabled
- **THEN** the system tracks disk space used by WAL and snapshots
- **AND** it warns when disk space is low
- **AND** it prevents writes when disk is full (configurable)
- **AND** disk usage metrics are exposed for monitoring

#### Scenario: Monitor recovery health
- **WHEN** recovery completes after restart
- **THEN** recovery metrics include duration and entry count
- **AND** recovery metrics include snapshot loaded and WAL entries replayed
- **AND** recovery metrics indicate any errors or data loss
- **AND** metrics help troubleshoot recovery issues
