## 1. Configuration
- [x] 1.1 Add cleanupIntervalSeconds parameter to InMemoryCache constructor
- [x] 1.2 Add enableAutoCleanup flag to InMemoryCache constructor
- [x] 1.3 Set reasonable defaults (cleanup interval: 60s, auto cleanup: enabled)

## 2. Background Cleanup Implementation
- [x] 2.1 Add ScheduledExecutorService to InMemoryCache
- [x] 2.2 Create cleanup task that calls removeExpired()
- [x] 2.3 Schedule cleanup task at configured interval
- [x] 2.4 Ensure cleanup task handles exceptions gracefully
- [x] 2.5 Add proper shutdown of executor in cache cleanup

## 3. Lifecycle Management
- [x] 3.1 Start background cleanup when cache is created (if enabled)
- [x] 3.2 Add shutdown() method to stop background cleanup
- [x] 3.3 Ensure cleanup task stops on shutdown
- [x] 3.4 Add tests for lifecycle management

## 4. Metrics Enhancement
- [x] 4.1 Track total expired entries removed over lifetime
- [x] 4.2 Add lastCleanupTime to CacheStats
- [x] 4.3 Add cleanupCount to track number of cleanup cycles
- [x] 4.4 Update getStats() to include new metrics

## 5. Testing
- [x] 5.1 Test that expired entries are removed automatically
- [x] 5.2 Test cleanup interval configuration
- [x] 5.3 Test that cleanup can be disabled
- [x] 5.4 Test shutdown stops cleanup task
- [x] 5.5 Test cleanup task error handling
- [x] 5.6 Test metrics are updated correctly

## 6. Documentation
- [x] 6.1 Document TTL cleanup behavior
- [x] 6.2 Add examples of configuring cleanup interval
- [x] 6.3 Document shutdown/lifecycle management
- [x] 6.4 Add performance considerations to docs
