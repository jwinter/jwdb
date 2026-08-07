package domain.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.time.Duration
import java.time.Instant

/**
 * Tests for TTL expiration and background cleanup.
 *
 * Tests that exercise cleanup *logic* disable the background task and drive
 * [InMemoryCache.runCleanupCycle] directly against already-expired entries, which makes them
 * synchronous and deterministic. Tests that exercise the *scheduler* keep real timing but
 * poll via [awaitUntil], returning as soon as the condition holds.
 */
@Tag("unit")
class CacheTTLTest {
    /** A value whose expiration is already in the past. */
    private fun expiredValue(data: String) = CacheValue(data, expiresAt = Instant.now().minusSeconds(1))

    /** A value that will not expire during the test. */
    private fun liveValue(data: String) = CacheValue(data, expiresAt = Instant.now().plus(Duration.ofHours(1)))

    /** A value that expires [millis] from now, for exercising a real TTL elapsing. */
    private fun expiringValue(
        data: String,
        millis: Long,
    ) = CacheValue(data, expiresAt = Instant.now().plus(Duration.ofMillis(millis)))

    /** Polls [condition] until it holds or [timeout] elapses, failing the test on timeout. */
    private fun awaitUntil(
        timeout: Duration = Duration.ofSeconds(5),
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(25)
        }
        fail("Condition not met within $timeout")
    }

    // ---------------------------------------------------------------------
    // Background scheduler — real timing, polled rather than slept through
    // ---------------------------------------------------------------------

    @Test
    fun `expired entries should be removed automatically`() {
        val cache =
            InMemoryCache<String>(
                enableAutoCleanup = true,
                cleanupIntervalSeconds = 1,
            )

        try {
            // Entry is live when written and expires while resident, so this covers the
            // real TTL transition rather than starting from an already-expired value.
            cache.put(CacheKey("key1"), expiringValue("value1", 200))
            assertEquals(1, cache.size())

            awaitUntil { cache.size() == 0L }

            val stats = cache.getStats()
            assertTrue(stats.expiredEntriesRemoved > 0)
            assertTrue(stats.cleanupCount > 0)
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `cleanup interval should be configurable`() {
        val cache =
            InMemoryCache<String>(
                enableAutoCleanup = true,
                cleanupIntervalSeconds = 2,
            )

        try {
            cache.put(CacheKey("key1"), expiredValue("value1"))

            // Well inside the configured 2s interval: the entry must still be present,
            // proving cleanup is governed by the interval and not run eagerly.
            Thread.sleep(300)
            assertEquals(1, cache.size())
            assertEquals(0, cache.getStats().cleanupCount)

            // Once the interval elapses, the entry goes.
            awaitUntil { cache.size() == 0L }
            assertTrue(cache.getStats().cleanupCount > 0)
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

        cache.put(CacheKey("key1"), CacheValue("value1"))
        awaitUntil { cache.getStats().cleanupCount > 0 }
        assertTrue(cache.isCleanupTaskRunning())

        val cleanupCountBeforeShutdown = cache.getStats().cleanupCount
        cache.shutdown()

        // shutdown() awaits termination, so once it returns the task cannot fire again.
        assertFalse(cache.isCleanupTaskRunning())
        assertEquals(cleanupCountBeforeShutdown, cache.getStats().cleanupCount)
    }

    // ---------------------------------------------------------------------
    // Cleanup logic — no scheduler involved, no waiting
    // ---------------------------------------------------------------------

    @Test
    fun `cleanup can be disabled`() {
        val cache = InMemoryCache<String>(enableAutoCleanup = false)

        try {
            cache.put(CacheKey("key1"), expiredValue("value1"))

            // No background task exists, so the expired entry stays in storage.
            assertFalse(cache.isCleanupTaskRunning())
            assertEquals(1, cache.size())

            val stats = cache.getStats()
            assertEquals(0, stats.cleanupCount)
            assertEquals(0, stats.expiredEntriesRemoved)
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `cleanup task should handle errors gracefully`() {
        val cache = InMemoryCache<String>(enableAutoCleanup = false)

        try {
            cache.put(CacheKey("key1"), CacheValue("value1"))
            cache.put(CacheKey("key2"), expiredValue("value2"))

            // A cycle over a mix of entries completes without throwing.
            cache.runCleanupCycle()

            val stats = cache.getStats()
            assertTrue(stats.cleanupCount > 0)
            assertEquals(1, cache.size())
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `cleanup metrics should be updated correctly`() {
        val cache = InMemoryCache<String>(enableAutoCleanup = false)

        try {
            cache.put(CacheKey("key1"), expiredValue("value1"))
            cache.put(CacheKey("key2"), expiredValue("value2"))
            cache.put(CacheKey("key3"), expiredValue("value3"))

            cache.runCleanupCycle()

            val stats = cache.getStats()
            assertEquals(3, stats.expiredEntriesRemoved)
            assertEquals(1, stats.cleanupCount)
            assertNotNull(stats.lastCleanupTime)
            assertEquals(0, cache.size())
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `manual removeExpired should not interfere with automatic cleanup metrics`() {
        val cache = InMemoryCache<String>(enableAutoCleanup = false)

        try {
            cache.put(CacheKey("key1"), expiredValue("value1"))

            val removed = cache.removeExpired()
            assertEquals(1, removed)

            val stats = cache.getStats()
            assertEquals(0, stats.cleanupCount) // No cleanup cycle ran
            assertEquals(0, stats.expiredEntriesRemoved) // Manual removal doesn't increment this
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `non-expired entries should not be removed by cleanup`() {
        val cache = InMemoryCache<String>(enableAutoCleanup = false)

        try {
            cache.put(CacheKey("key1"), CacheValue("value1"))
            cache.put(CacheKey("key2"), liveValue("value2"))

            cache.runCleanupCycle()

            assertEquals(2, cache.size())
            assertEquals(0, cache.getStats().expiredEntriesRemoved)
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `lastCleanupTime should be updated after each cleanup`() {
        val cache = InMemoryCache<String>(enableAutoCleanup = false)

        try {
            assertNull(cache.getStats().lastCleanupTime)

            cache.put(CacheKey("key1"), expiredValue("value1"))
            cache.runCleanupCycle()
            val firstCleanupTime = cache.getStats().lastCleanupTime
            assertNotNull(firstCleanupTime)

            cache.runCleanupCycle()
            val secondCleanupTime = cache.getStats().lastCleanupTime
            assertNotNull(secondCleanupTime)

            // Millisecond resolution means back-to-back cycles may share a timestamp; what
            // matters is that it is refreshed on every cycle and never moves backwards.
            assertTrue(secondCleanupTime!! >= firstCleanupTime!!)
            assertEquals(2, cache.getStats().cleanupCount)
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `resetStats should clear cleanup metrics`() {
        val cache = InMemoryCache<String>(enableAutoCleanup = false)

        try {
            cache.put(CacheKey("key1"), expiredValue("value1"))
            cache.runCleanupCycle()

            var stats = cache.getStats()
            assertTrue(stats.cleanupCount > 0)
            assertTrue(stats.expiredEntriesRemoved > 0)
            assertNotNull(stats.lastCleanupTime)

            cache.resetStats()

            // With no background task running, nothing can increment these between the
            // reset and the read — which is what made this assertion flaky before.
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
        val cache = InMemoryCache<String>(enableAutoCleanup = false)

        try {
            // Normal operations should work
            cache.put(CacheKey("key1"), CacheValue("value1"))
            val result = cache.get(CacheKey("key1"))

            assertTrue(result is CacheResult.Hit)
            assertEquals("value1", (result as CacheResult.Hit).value.data)

            // Manual removeExpired should still work
            cache.put(CacheKey("key2"), expiredValue("value2"))
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
        val cache = InMemoryCache<String>(enableAutoCleanup = false)

        try {
            cache.put(CacheKey("key1"), expiredValue("value1"))
            cache.put(CacheKey("key2"), expiredValue("value2"))

            cache.runCleanupCycle()
            assertEquals(2, cache.getStats().expiredEntriesRemoved)

            cache.put(CacheKey("key3"), expiredValue("value3"))
            cache.runCleanupCycle()

            val stats = cache.getStats()
            // Total removed should be cumulative
            assertEquals(3, stats.expiredEntriesRemoved)
            assertEquals(2, stats.cleanupCount)
        } finally {
            cache.shutdown()
        }
    }

    @Test
    fun `cleanup should handle mixed expired and non-expired entries`() {
        val cache = InMemoryCache<String>(enableAutoCleanup = false)

        try {
            cache.put(CacheKey("keep1"), CacheValue("value1")) // No expiration
            cache.put(CacheKey("expire1"), expiredValue("value2"))
            cache.put(CacheKey("keep2"), liveValue("value3"))
            cache.put(CacheKey("expire2"), expiredValue("value4"))

            cache.runCleanupCycle()

            // Should have 2 entries left (keep1 and keep2)
            assertEquals(2, cache.size())
            assertTrue(cache.contains(CacheKey("keep1")))
            assertTrue(cache.contains(CacheKey("keep2")))
            assertFalse(cache.contains(CacheKey("expire1")))
            assertFalse(cache.contains(CacheKey("expire2")))

            assertEquals(2, cache.getStats().expiredEntriesRemoved)
        } finally {
            cache.shutdown()
        }
    }
}
