## ADDED Requirements

### Requirement: Automatic Expiration Cleanup
The cache SHALL automatically remove expired entries in the background.

#### Scenario: Background cleanup removes expired entries
- **WHEN** entries expire in the cache
- **THEN** a background task periodically scans for expired entries
- **AND** expired entries are removed automatically
- **AND** memory is freed without requiring explicit Get operations
- **AND** cleanup metrics are tracked and available

#### Scenario: Configure cleanup interval
- **WHEN** creating an InMemoryCache instance
- **THEN** the cleanup interval can be configured in seconds
- **AND** the default interval is reasonable for production use (60 seconds)
- **AND** the interval can be adjusted for different use cases

#### Scenario: Disable automatic cleanup
- **WHEN** automatic cleanup is not desired
- **THEN** it can be disabled via configuration
- **AND** the cache operates without background tasks
- **AND** expired entries are only removed on access

### Requirement: Cache Lifecycle Management
The cache SHALL support proper startup and shutdown.

#### Scenario: Start cache with automatic cleanup
- **WHEN** an InMemoryCache is created with auto cleanup enabled
- **THEN** the background cleanup task is scheduled immediately
- **AND** cleanup runs at the configured interval
- **AND** the task handles errors without crashing

#### Scenario: Shutdown cache gracefully
- **WHEN** the cache shutdown() method is called
- **THEN** the background cleanup task is stopped
- **AND** no new cleanup tasks are scheduled
- **AND** resources are released properly
- **AND** shutdown completes within a reasonable timeout

### Requirement: Cleanup Metrics
The cache SHALL track metrics about automatic cleanup operations.

#### Scenario: Track cleanup statistics
- **WHEN** automatic cleanup runs
- **THEN** CacheStats includes lastCleanupTime
- **AND** CacheStats includes cleanupCount (total cleanup cycles)
- **AND** CacheStats includes total expired entries removed
- **AND** these metrics help monitor cache health
