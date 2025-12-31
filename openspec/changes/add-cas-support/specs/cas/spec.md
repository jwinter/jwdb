## ADDED Requirements

### Requirement: Atomic Compare-And-Set Operation
The system SHALL provide an atomic compare-and-set operation for optimistic concurrency control.

#### Scenario: Successful CAS with matching version
- **GIVEN** a cache contains key "counter" with value 10 and version V1
- **WHEN** a client performs CAS with expectedVersion=V1 and newValue=11
- **THEN** the operation succeeds
- **AND** the cache contains key "counter" with value 11 and new version V2
- **AND** the response includes status SUCCESS and the new version V2

#### Scenario: Failed CAS with version mismatch
- **GIVEN** a cache contains key "counter" with value 10 and version V1
- **WHEN** a client performs CAS with expectedVersion=V0 (old version) and newValue=11
- **THEN** the operation fails with VERSION_MISMATCH status
- **AND** the cache still contains key "counter" with value 10 and version V1
- **AND** the response includes the current value and version for client retry

#### Scenario: CAS on non-existent key with null expected version
- **GIVEN** a cache does not contain key "new-key"
- **WHEN** a client performs CAS with expectedVersion=null and newValue="initial"
- **THEN** the operation succeeds (insert)
- **AND** the cache contains key "new-key" with value "initial" and new version V1
- **AND** the response includes status SUCCESS

#### Scenario: CAS on non-existent key with non-null expected version
- **GIVEN** a cache does not contain key "missing-key"
- **WHEN** a client performs CAS with expectedVersion=V1 and newValue="data"
- **THEN** the operation fails with KEY_NOT_FOUND status
- **AND** the cache remains unchanged

### Requirement: CAS with Expired Entries
The system SHALL handle CAS operations on expired entries correctly.

#### Scenario: CAS fails on expired entry
- **GIVEN** a cache contains key "session" with version V1 that expired 1 minute ago
- **WHEN** a client performs CAS with expectedVersion=V1
- **THEN** the operation fails with KEY_NOT_FOUND status
- **AND** the expired entry is treated as non-existent

### Requirement: Concurrent CAS Operations
The system SHALL handle concurrent CAS operations correctly without lost updates.

#### Scenario: Two clients CAS the same key concurrently
- **GIVEN** a cache contains key "counter" with value 0 and version V0
- **WHEN** client A and client B both read the counter (value=0, version=V0)
- **AND** client A performs CAS with expectedVersion=V0, newValue=1
- **AND** client B performs CAS with expectedVersion=V0, newValue=1
- **THEN** exactly one CAS succeeds
- **AND** exactly one CAS fails with VERSION_MISMATCH
- **AND** the counter has value 1 (no lost update)
- **AND** the failing client receives the current version for retry

#### Scenario: CAS retry loop successfully increments counter
- **GIVEN** a cache contains key "counter" with value 5
- **WHEN** 10 clients concurrently increment the counter using CAS retry loops
- **THEN** all 10 increments eventually succeed
- **AND** the final counter value is 15
- **AND** no increments are lost

### Requirement: Distributed CAS with Consistency Levels
The system SHALL support CAS operations across replicated data with configurable consistency.

#### Scenario: CAS with QUORUM consistency
- **GIVEN** a 3-node cluster with replication factor 3
- **AND** key "session" exists with version V1 on all replicas
- **WHEN** a client performs CAS with expectedVersion=V1 and consistency=QUORUM
- **THEN** the coordinator queries 2 out of 3 replicas
- **AND** if both return version V1, the write is sent to all 3 replicas
- **AND** the CAS succeeds when 2 out of 3 replicas acknowledge the write
- **AND** the response is sent to the client

#### Scenario: CAS fails when replicas have divergent versions
- **GIVEN** a 3-node cluster with replication factor 3
- **AND** key "data" has version V1 on replica1, V2 on replica2, V3 on replica3
- **WHEN** a client performs CAS with expectedVersion=V2 and consistency=QUORUM
- **THEN** the coordinator queries 2 replicas
- **AND** detects version mismatch between replicas
- **AND** the CAS fails with VERSION_MISMATCH status
- **AND** read repair is triggered to reconcile the versions
- **AND** the current version (after read repair) is returned to client

#### Scenario: CAS with ONE consistency is fast but risky
- **GIVEN** a 3-node cluster with replication factor 3
- **WHEN** a client performs CAS with consistency=ONE
- **THEN** the coordinator queries only 1 replica
- **AND** if the version matches, writes to all 3 replicas
- **AND** returns success after 1 replica acknowledges
- **AND** there is risk of conflict if replicas have divergent versions

### Requirement: CAS Statistics and Monitoring
The system SHALL track CAS operation statistics for monitoring and debugging.

#### Scenario: Track CAS success and failure rates
- **WHEN** CAS operations are performed
- **THEN** the system tracks total CAS operations count
- **AND** tracks CAS success count
- **AND** tracks CAS failure count by reason (VERSION_MISMATCH, KEY_NOT_FOUND, ERROR)
- **AND** tracks CAS operation latency (p50, p95, p99)

#### Scenario: CAS contention metrics
- **WHEN** multiple clients perform CAS on the same key
- **THEN** the system tracks retry attempts per key
- **AND** tracks contention level (failures per key)
- **AND** exposes metrics for identifying hot keys

### Requirement: CAS Protocol Messages
The system SHALL define clear protocol messages for CAS operations.

#### Scenario: CAS request message contains required fields
- **WHEN** a client sends a CAS request
- **THEN** the request includes cache key
- **AND** includes expected version (nullable)
- **AND** includes new cache entry (data + metadata)

#### Scenario: CAS response indicates success or failure reason
- **WHEN** a CAS operation completes
- **THEN** the response includes status (SUCCESS, VERSION_MISMATCH, KEY_NOT_FOUND, ERROR)
- **AND** includes current version (after operation)
- **AND** includes current entry on failure (for client retry)
- **AND** includes error message on ERROR status

### Requirement: CAS Error Handling
The system SHALL handle errors during CAS operations gracefully.

#### Scenario: CAS timeout during distributed operation
- **GIVEN** a 3-node cluster with CAS consistency=QUORUM
- **WHEN** one replica times out during CAS
- **THEN** the coordinator waits for 2 successful responses
- **AND** if quorum achieved, CAS proceeds
- **AND** if quorum not achieved within timeout, CAS fails with ERROR status

#### Scenario: Network partition during CAS
- **GIVEN** a 3-node cluster partitioned into 2+1 nodes
- **WHEN** a client on the majority side performs CAS with QUORUM
- **THEN** the CAS can succeed using the 2-node majority
- **AND** the minority node receives updates after partition heals
- **AND** hinted handoff ensures consistency

### Requirement: CAS and TTL Interaction
The system SHALL handle CAS operations with TTL correctly.

#### Scenario: CAS sets new value with TTL
- **GIVEN** a cache contains key "session" with no TTL
- **WHEN** a client performs CAS with newValue including TTL=300s
- **THEN** the CAS succeeds
- **AND** the new value has TTL=300s
- **AND** the entry expires after 300 seconds

#### Scenario: CAS on entry near expiration
- **GIVEN** a cache contains key "token" that expires in 1 second
- **WHEN** a client performs CAS before expiration
- **THEN** the CAS can succeed if version matches
- **AND** after expiration, subsequent CAS fails with KEY_NOT_FOUND

## MODIFIED Requirements

None - CAS is a new additive feature that doesn't modify existing requirements.

## REMOVED Requirements

None - CAS doesn't remove any existing functionality.
