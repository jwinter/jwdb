# Change: Add Cache TTL Support

**Status: Proposed**

## Why
The current cache implementation has expiration support in CacheValue but lacks automatic background cleanup of expired entries. Without automatic expiration handling, the cache will grow unbounded with expired entries until they are accessed. A background TTL cleanup mechanism is essential for a production cache to prevent memory exhaustion and maintain performance.

## What Changes
- Add configurable background task for removing expired entries
- Implement scheduled executor for periodic cleanup
- Add configuration for cleanup interval
- Expose metrics for expired entries removed
- Add tests for TTL behavior and cleanup
- Update InMemoryCache to support automatic cleanup on/off

## Impact
- Affected specs: Modify existing `cache-operations` capability (or create new spec)
- Affected code: InMemoryCache class
- Runtime: Background thread will run periodic cleanup
- Configuration: New setting for cleanup interval (default: 60 seconds)
- Performance: Slight CPU overhead for periodic scans, but prevents memory growth
