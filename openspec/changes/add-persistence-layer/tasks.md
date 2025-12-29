## 1. Write-Ahead Log (WAL)
- [ ] 1.1 Define WAL entry protobuf schema (operation type, key, value, timestamp)
- [ ] 1.2 Create WALWriter class for append-only log writing
- [ ] 1.3 Implement WAL segment rotation (new file after size threshold)
- [ ] 1.4 Add WAL buffer for batching writes
- [ ] 1.5 Implement periodic flush (async mode)
- [ ] 1.6 Implement immediate fsync (sync mode)
- [ ] 1.7 Add WAL reader for recovery

## 2. Snapshot Mechanism
- [ ] 2.1 Define snapshot file format (protobuf or binary)
- [ ] 2.2 Create SnapshotWriter for dumping cache state
- [ ] 2.3 Implement background snapshot creation (copy-on-write friendly)
- [ ] 2.4 Add snapshot compression (optional gzip)
- [ ] 2.5 Create SnapshotReader for loading snapshots
- [ ] 2.6 Implement snapshot metadata (timestamp, entry count, checksum)
- [ ] 2.7 Add snapshot retention policy (keep last N snapshots)

## 3. Persistence Coordinator
- [ ] 3.1 Create PersistenceCoordinator interface
- [ ] 3.2 Implement async persistence mode
- [ ] 3.3 Implement sync persistence mode
- [ ] 3.4 Add disabled mode (in-memory only)
- [ ] 3.5 Create background thread pool for persistence operations
- [ ] 3.6 Add graceful shutdown with flush

## 4. Cache Integration
- [ ] 4.1 Modify put() to log to WAL
- [ ] 4.2 Modify delete() to log to WAL
- [ ] 4.3 Modify clear() to log to WAL
- [ ] 4.4 Add persistence hooks without blocking cache operations
- [ ] 4.5 Handle persistence failures gracefully

## 5. Recovery Mechanism
- [ ] 5.1 Create RecoveryManager class
- [ ] 5.2 Implement snapshot discovery (find latest valid snapshot)
- [ ] 5.3 Load snapshot into cache
- [ ] 5.4 Replay WAL entries since snapshot timestamp
- [ ] 5.5 Handle corrupted WAL entries
- [ ] 5.6 Verify recovery with checksums
- [ ] 5.7 Log recovery statistics

## 6. Log Compaction
- [ ] 6.1 Implement WAL compaction (remove entries before last snapshot)
- [ ] 6.2 Add background compaction task
- [ ] 6.3 Delete old WAL segments safely
- [ ] 6.4 Handle compaction during recovery

## 7. Configuration
- [ ] 7.1 Add persistence mode configuration (sync/async/disabled)
- [ ] 7.2 Add WAL flush interval configuration
- [ ] 7.3 Add snapshot interval configuration
- [ ] 7.4 Add data directory configuration
- [ ] 7.5 Add WAL segment size threshold
- [ ] 7.6 Add snapshot retention count

## 8. Error Handling
- [ ] 8.1 Handle disk full errors
- [ ] 8.2 Handle I/O errors during WAL write
- [ ] 8.3 Handle corrupted snapshot files
- [ ] 8.4 Handle corrupted WAL entries
- [ ] 8.5 Add retry logic for transient failures

## 9. Metrics and Monitoring
- [ ] 9.1 Track WAL write rate and latency
- [ ] 9.2 Track snapshot creation time
- [ ] 9.3 Track disk space usage
- [ ] 9.4 Track recovery time and entry count
- [ ] 9.5 Add persistence health checks

## 10. Testing
- [ ] 10.1 Unit tests for WAL entry serialization/deserialization
- [ ] 10.2 Unit tests for WAL segment rotation logic
- [ ] 10.3 Unit tests for snapshot creation and compression
- [ ] 10.4 Unit tests for recovery manager logic
- [ ] 10.5 Integration test: Write operations logged to WAL
- [ ] 10.6 Integration test: Snapshot creation and loading
- [ ] 10.7 Integration test: Recovery from snapshot + WAL replay
- [ ] 10.8 Integration test: WAL compaction after snapshot
- [ ] 10.9 E2E test: Crash recovery (single-node)
- [ ] 10.10 E2E test: Crash recovery in multi-node cluster
- [ ] 10.11 E2E test: Coordinated snapshot across replicas
- [ ] 10.12 E2E test: Disk full scenario handling
- [ ] 10.13 E2E test: Corrupted WAL entry recovery
- [ ] 10.14 E2E test: Corrupted snapshot fallback
- [ ] 10.15 Performance test: Async vs sync mode latency
- [ ] 10.16 Performance test: WAL flush throughput
- [ ] 10.17 Longevity test: 24-hour run with persistence
- [ ] 10.18 Longevity test: Periodic crashes over 72 hours
- [ ] 10.19 Add `@Tag("longevity")` for long-running tests
- [ ] 10.20 Add `@Tag("crash-recovery")` for crash tests

## 11. Documentation
- [ ] 11.1 Document persistence architecture
- [ ] 11.2 Document recovery process
- [ ] 11.3 Add configuration guide
- [ ] 11.4 Document performance trade-offs
- [ ] 11.5 Add backup and restore procedures
