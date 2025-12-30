package domain.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

@Tag("unit")
class CacheTTLTest {
    @Test
    fun `expired entries should be removed automatically`() {
        // Create cache with 1-second cleanup interval
        val cache =
            InMemoryCache<String>(
                enableAutoCleanup = true,
                cleanupIntervalSeconds = 1,
            )

        try {
            // Add entry that expires in 500ms
            cache.put(
                CacheKey("key1"),
                CacheValue(
                    "value1",
                    expiresAt = Instant.now().plus(Duration.ofMillis(500)),
                ),
            )

            // Verify entry exists
            assertEquals(1, cache.size())

            // Wait for entry to expire and cleanup to run
            Thread.sleep(2000)

            // Verify entry was automatically removed
            assertEquals(0, cache.size())
            val stats = cache.getStats()
            assertTrue(stats.expiredEntriesRemoved > 0)
            assertTrue(stats.cleanupCount > 0)
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `cleanup interval should be configurable`() {
        // Create cache with 2-second cleanup interval
        val cache =
            InMemoryCache<String>(
                enableAutoCleanup = true,
                cleanupIntervalSeconds = 2,
            )

        try {
            // Add expired entry
            cache.put(
                CacheKey("key1"),
                CacheValue(
                    "value1",
                    expiresAt = Instant.now().minus(Duration.ofSeconds(1)),
                ),
            )

            // Wait less than cleanup interval - entry should still be there
            Thread.sleep(1000)
            assertEquals(1, cache.size())

            // Wait for cleanup interval to pass
            Thread.sleep(1500)

            // Entry should now be removed
            assertEquals(0, cache.size())
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `cleanup can be disabled`() {
        val cache =
            InMemoryCache<String>(
                enableAutoCleanup = false,
            )

        try {
            // Add expired entry
            cache.put(
                CacheKey("key1"),
                CacheValue(
                    "value1",
                    expiresAt = Instant.now().minus(Duration.ofSeconds(1)),
                ),
            )

            // Wait to ensure no cleanup runs
            Thread.sleep(2000)

            // Entry should still be in storage (though expired)
            assertEquals(1, cache.size())

            // Stats should show no automatic cleanup
            val stats = cache.getStats()
            assertEquals(0, stats.cleanupCount)
            assertEquals(0, stats.expiredEntriesRemoved)
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `shutdown should stop cleanup task`() {
        val cache =
            InMemoryCache<String>(
                enableAutoCleanup = true,
                cleanupIntervalSeconds = 1,
            )

        // Add entry
        cache.put(CacheKey("key1"), CacheValue("value1"))

        // Get initial cleanup count
        Thread.sleep(1500)
        val cleanupCountBeforeShutdown = cache.getStats().cleanupCount

        // Shutdown cache
        cache.shutdown()

        // Wait and verify cleanup doesn't run anymore
        Thread.sleep(2000)
        val cleanupCountAfterShutdown = cache.getStats().cleanupCount

        assertEquals(cleanupCountBeforeShutdown, cleanupCountAfterShutdown)
    }

    @Test
    fun `cleanup task should handle errors gracefully`() {
        val cache =
            InMemoryCache<String>(
                enableAutoCleanup = true,
                cleanupIntervalSeconds = 1,
            )

        try {
            // Add some entries
            cache.put(CacheKey("key1"), CacheValue("value1"))
            cache.put(
                CacheKey("key2"),
                CacheValue(
                    "value2",
                    expiresAt = Instant.now().plus(Duration.ofMillis(500)),
                ),
            )

            // Wait for cleanup to run
            Thread.sleep(2000)

            // Cleanup should have run without crashing
            val stats = cache.getStats()
            assertTrue(stats.cleanupCount > 0)
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `cleanup metrics should be updated correctly`() {
        val cache =
            InMemoryCache<String>(
                enableAutoCleanup = true,
                cleanupIntervalSeconds = 1,
            )

        try {
            // Add multiple expired entries
            cache.put(
                CacheKey("key1"),
                CacheValue(
                    "value1",
                    expiresAt = Instant.now().plus(Duration.ofMillis(300)),
                ),
            )
            cache.put(
                CacheKey("key2"),
                CacheValue(
                    "value2",
                    expiresAt = Instant.now().plus(Duration.ofMillis(300)),
                ),
            )
            cache.put(
                CacheKey("key3"),
                CacheValue(
                    "value3",
                    expiresAt = Instant.now().plus(Duration.ofMillis(300)),
                ),
            )

            // Wait for cleanup
            Thread.sleep(2000)

            val stats = cache.getStats()

            // Verify cleanup metrics
            assertEquals(3, stats.expiredEntriesRemoved)
            assertTrue(stats.cleanupCount >= 1)
            assertNotNull(stats.lastCleanupTime)
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `manual removeExpired should not interfere with automatic cleanup metrics`() {
        // Long interval so it doesn't run during test
        val cache =
            InMemoryCache<String>(
                enableAutoCleanup = true,
                cleanupIntervalSeconds = 10,
            )

        try {
            // Add expired entry
            cache.put(
                CacheKey("key1"),
                CacheValue(
                    "value1",
                    expiresAt = Instant.now().minus(Duration.ofSeconds(1)),
                ),
            )

            // Manual removal doesn't count towards automatic cleanup metrics
            val removed = cache.removeExpired()
            assertEquals(1, removed)

            val stats = cache.getStats()
            assertEquals(0, stats.cleanupCount) // No automatic cleanup ran
            assertEquals(0, stats.expiredEntriesRemoved) // Manual removal doesn't increment this
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `non-expired entries should not be removed by cleanup`() {
        val cache =
            InMemoryCache<String>(
                enableAutoCleanup = true,
                cleanupIntervalSeconds = 1,
            )

        try {
            // Add entry that won't expire
            cache.put(CacheKey("key1"), CacheValue("value1"))

            // Add entry that expires later
            cache.put(
                CacheKey("key2"),
                CacheValue(
                    "value2",
                    expiresAt = Instant.now().plus(Duration.ofHours(1)),
                ),
            )

            // Wait for cleanup to run
            Thread.sleep(2000)

            // Both entries should still be there
            assertEquals(2, cache.size())

            val stats = cache.getStats()
            assertEquals(0, stats.expiredEntriesRemoved)
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `lastCleanupTime should be updated after each cleanup`() {
        val cache =
            InMemoryCache<String>(
                enableAutoCleanup = true,
                cleanupIntervalSeconds = 1,
            )

        try {
            // Initial state - no cleanup yet
            assertNull(cache.getStats().lastCleanupTime)

            // Add expired entry
            cache.put(
                CacheKey("key1"),
                CacheValue(
                    "value1",
                    expiresAt = Instant.now().minus(Duration.ofSeconds(1)),
                ),
            )

            // Wait for first cleanup
            Thread.sleep(1500)
            val firstCleanupTime = cache.getStats().lastCleanupTime
            assertNotNull(firstCleanupTime)

            // Wait for second cleanup
            Thread.sleep(1500)
            val secondCleanupTime = cache.getStats().lastCleanupTime
            assertNotNull(secondCleanupTime)

            // Second cleanup time should be after first
            assertTrue(secondCleanupTime!! > firstCleanupTime!!)
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `resetStats should clear cleanup metrics`() {
        val cache =
            InMemoryCache<String>(
                enableAutoCleanup = true,
                cleanupIntervalSeconds = 1,
            )

        try {
            // Add expired entry
            cache.put(
                CacheKey("key1"),
                CacheValue(
                    "value1",
                    expiresAt = Instant.now().plus(Duration.ofMillis(500)),
                ),
            )

            // Wait for cleanup
            Thread.sleep(2000)

            // Verify cleanup ran
            var stats = cache.getStats()
            assertTrue(stats.cleanupCount > 0)
            assertTrue(stats.expiredEntriesRemoved > 0)
            assertNotNull(stats.lastCleanupTime)

            // Reset stats
            cache.resetStats()

            // Verify cleanup metrics are cleared
            stats = cache.getStats()
            assertEquals(0, stats.cleanupCount)
            assertEquals(0, stats.expiredEntriesRemoved)
            assertNull(stats.lastCleanupTime)
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `cache with no automatic cleanup should work normally`() {
        val cache =
            InMemoryCache<String>(
                enableAutoCleanup = false,
            )

        try {
            // Normal operations should work
            cache.put(CacheKey("key1"), CacheValue("value1"))
            val result = cache.get(CacheKey("key1"))

            assertTrue(result is CacheResult.Hit)
            assertEquals("value1", (result as CacheResult.Hit).value.data)

            // Manual removeExpired should still work
            cache.put(
                CacheKey("key2"),
                CacheValue(
                    "value2",
                    expiresAt = Instant.now().minus(Duration.ofSeconds(1)),
                ),
            )
            val removed = cache.removeExpired()
            assertEquals(1, removed)

            // Shutdown should work even without cleanup task
            cache.shutdown() // Should not throw
        } finally {
            // Ensure cleanup in case of test failure
            try {
                cache.shutdown()
            } catch (e: Exception) {
                // Ignore if already shutdown
            }
        }
    }

    @Test
    fun `multiple cleanups should accumulate expired entries removed`() {
        val cache =
            InMemoryCache<String>(
                enableAutoCleanup = true,
                cleanupIntervalSeconds = 1,
            )

        try {
            // First batch of expired entries
            cache.put(
                CacheKey("key1"),
                CacheValue(
                    "value1",
                    expiresAt = Instant.now().plus(Duration.ofMillis(300)),
                ),
            )
            cache.put(
                CacheKey("key2"),
                CacheValue(
                    "value2",
                    expiresAt = Instant.now().plus(Duration.ofMillis(300)),
                ),
            )

            // Wait for first cleanup
            Thread.sleep(1500)

            val statsAfterFirst = cache.getStats()
            val firstCleanupRemoved = statsAfterFirst.expiredEntriesRemoved

            assertEquals(2, firstCleanupRemoved)

            // Second batch of expired entries
            cache.put(
                CacheKey("key3"),
                CacheValue(
                    "value3",
                    expiresAt = Instant.now().plus(Duration.ofMillis(300)),
                ),
            )

            // Wait for second cleanup
            Thread.sleep(1500)

            val statsAfterSecond = cache.getStats()

            // Total removed should be cumulative
            assertEquals(3, statsAfterSecond.expiredEntriesRemoved)
            assertTrue(statsAfterSecond.cleanupCount >= 2)
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `cleanup should handle mixed expired and non-expired entries`() {
        val cache =
            InMemoryCache<String>(
                enableAutoCleanup = true,
                cleanupIntervalSeconds = 1,
            )

        try {
            // Add mix of expired and non-expired entries
            cache.put(CacheKey("keep1"), CacheValue("value1")) // No expiration
            cache.put(
                CacheKey("expire1"),
                CacheValue(
                    "value2",
                    expiresAt = Instant.now().plus(Duration.ofMillis(300)),
                ),
            )
            cache.put(
                CacheKey("keep2"),
                CacheValue(
                    "value3",
                    expiresAt = Instant.now().plus(Duration.ofHours(1)),
                ),
            )
            cache.put(
                CacheKey("expire2"),
                CacheValue(
                    "value4",
                    expiresAt = Instant.now().plus(Duration.ofMillis(300)),
                ),
            )

            // Wait for cleanup
            Thread.sleep(2000)

            // Should have 2 entries left (keep1 and keep2)
            assertEquals(2, cache.size())

            // Verify correct entries remain
            assertTrue(cache.contains(CacheKey("keep1")))
            assertTrue(cache.contains(CacheKey("keep2")))
            assertFalse(cache.contains(CacheKey("expire1")))
            assertFalse(cache.contains(CacheKey("expire2")))

            val stats = cache.getStats()
            assertEquals(2, stats.expiredEntriesRemoved)
        } finally {
            cache.shutdown()
        }
    }
}
