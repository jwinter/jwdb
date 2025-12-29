## 1. Configuration
- [ ] 1.1 Add cleanupIntervalSeconds parameter to InMemoryCache constructor
- [ ] 1.2 Add enableAutoCleanup flag to InMemoryCache constructor
- [ ] 1.3 Set reasonable defaults (cleanup interval: 60s, auto cleanup: enabled)

## 2. Background Cleanup Implementation
- [ ] 2.1 Add ScheduledExecutorService to InMemoryCache
- [ ] 2.2 Create cleanup task that calls removeExpired()
- [ ] 2.3 Schedule cleanup task at configured interval
- [ ] 2.4 Ensure cleanup task handles exceptions gracefully
- [ ] 2.5 Add proper shutdown of executor in cache cleanup

## 3. Lifecycle Management
- [ ] 3.1 Start background cleanup when cache is created (if enabled)
- [ ] 3.2 Add shutdown() method to stop background cleanup
- [ ] 3.3 Ensure cleanup task stops on shutdown
- [ ] 3.4 Add tests for lifecycle management

## 4. Metrics Enhancement
- [ ] 4.1 Track total expired entries removed over lifetime
- [ ] 4.2 Add lastCleanupTime to CacheStats
- [ ] 4.3 Add cleanupCount to track number of cleanup cycles
- [ ] 4.4 Update getStats() to include new metrics

## 5. Testing
- [ ] 5.1 Test that expired entries are removed automatically
- [ ] 5.2 Test cleanup interval configuration
- [ ] 5.3 Test that cleanup can be disabled
- [ ] 5.4 Test shutdown stops cleanup task
- [ ] 5.5 Test cleanup task error handling
- [ ] 5.6 Test metrics are updated correctly

## 6. Documentation
- [ ] 6.1 Document TTL cleanup behavior
- [ ] 6.2 Add examples of configuring cleanup interval
- [ ] 6.3 Document shutdown/lifecycle management
- [ ] 6.4 Add performance considerations to docs
