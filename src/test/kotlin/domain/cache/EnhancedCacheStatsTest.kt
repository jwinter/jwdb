package domain.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

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
}
