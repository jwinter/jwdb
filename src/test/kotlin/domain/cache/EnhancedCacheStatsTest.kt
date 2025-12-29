package domain.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Tag("unit")
class EnhancedCacheStatsTest {
    @Test
    fun `put operations should increment putCount`() {
        val cache = InMemoryCache<String>()
        val key = CacheKey("test")
        val value = CacheValue("data")

        cache.put(key, value)
        cache.put(CacheKey("test2"), CacheValue("data2"))

        val stats = cache.getStats()
        assertEquals(2, stats.putCount)
    }

    @Test
    fun `delete operations should increment deleteCount`() {
        val cache = InMemoryCache<String>()
        cache.put(CacheKey("test"), CacheValue("data"))

        cache.delete(CacheKey("test"))
        cache.delete(CacheKey("nonexistent"))

        val stats = cache.getStats()
        assertEquals(2, stats.deleteCount)
    }

    @Test
    fun `clear operations should increment clearCount`() {
        val cache = InMemoryCache<String>()
        cache.put(CacheKey("test"), CacheValue("data"))

        cache.clear()
        cache.clear()

        val stats = cache.getStats()
        assertEquals(2, stats.clearCount)
    }

    @Test
    fun `totalOperations should sum all operations`() {
        val cache = InMemoryCache<String>()

        cache.put(CacheKey("key1"), CacheValue("val1"))
        cache.put(CacheKey("key2"), CacheValue("val2"))
        cache.get(CacheKey("key1")) // hit
        cache.get(CacheKey("missing")) // miss
        cache.delete(CacheKey("key1"))
        cache.clear()

        val stats = cache.getStats()
        // 2 puts + 1 hit + 1 miss + 1 delete + 1 clear = 6
        assertEquals(6, stats.totalOperations)
    }

    @Test
    fun `evictions should be tracked by policy`() {
        val cache = InMemoryCache<String>(maxSize = 2, evictionPolicy = EvictionPolicy.LRU)

        cache.put(CacheKey("key1"), CacheValue("val1"))
        cache.put(CacheKey("key2"), CacheValue("val2"))
        cache.put(CacheKey("key3"), CacheValue("val3")) // triggers eviction

        val stats = cache.getStats()
        assertEquals(1, stats.evictions)
        assertEquals(1, stats.evictionsByPolicy[EvictionPolicy.LRU])
    }

    @Test
    fun `resetStats should clear all counters`() {
        val cache = InMemoryCache<String>()

        cache.put(CacheKey("key1"), CacheValue("val1"))
        cache.get(CacheKey("key1"))
        cache.delete(CacheKey("key1"))

        cache.resetStats()

        val stats = cache.getStats()
        assertEquals(0, stats.hits)
        assertEquals(0, stats.misses)
        assertEquals(0, stats.putCount)
        assertEquals(0, stats.deleteCount)
        assertEquals(0, stats.evictions)
        assertTrue(stats.evictionsByPolicy.isEmpty())
    }

    @Test
    fun `getStatsFormatted should return readable output`() {
        val cache = InMemoryCache<String>()

        cache.put(CacheKey("key1"), CacheValue("val1"))
        cache.get(CacheKey("key1"))

        val formatted = cache.getStatsFormatted()

        assertTrue(formatted.contains("Cache Statistics:"))
        assertTrue(formatted.contains("Hits: 1"))
        assertTrue(formatted.contains("Puts: 1"))
        assertTrue(formatted.contains("Uptime:"))
    }

    @Test
    fun `createdAt timestamp should be set`() {
        val before = System.currentTimeMillis()
        val cache = InMemoryCache<String>()
        val after = System.currentTimeMillis()

        val stats = cache.getStats()

        assertTrue(stats.createdAt >= before)
        assertTrue(stats.createdAt <= after)
    }

    @Test
    fun `concurrent access to statistics should be thread-safe`() {
        val cache = InMemoryCache<String>()
        val threadCount = 10
        val operationsPerThread = 100
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        // Launch multiple threads performing various cache operations concurrently
        repeat(threadCount) { threadId ->
            executor.submit {
                try {
                    repeat(operationsPerThread) { opId ->
                        val key = CacheKey("thread-$threadId-key-$opId")
                        val value = CacheValue("data-$opId")

                        // Perform various operations
                        cache.put(key, value)
                        cache.get(key)
                        cache.get(CacheKey("missing-$opId"))
                        cache.delete(key)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        // Wait for all threads to complete
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent operations did not complete in time")
        executor.shutdown()

        // Verify that all operations were correctly counted
        val stats = cache.getStats()

        // Each thread does: operationsPerThread puts, gets (hit), gets (miss), deletes
        val expectedPuts = (threadCount * operationsPerThread).toLong()
        val expectedHits = (threadCount * operationsPerThread).toLong()
        val expectedMisses = (threadCount * operationsPerThread).toLong()
        val expectedDeletes = (threadCount * operationsPerThread).toLong()
        val expectedTotal = expectedPuts + expectedHits + expectedMisses + expectedDeletes

        assertEquals(expectedPuts, stats.putCount, "Put count mismatch")
        assertEquals(expectedHits, stats.hits, "Hits count mismatch")
        assertEquals(expectedMisses, stats.misses, "Misses count mismatch")
        assertEquals(expectedDeletes, stats.deleteCount, "Delete count mismatch")
        assertEquals(expectedTotal, stats.totalOperations, "Total operations mismatch")
    }
}
