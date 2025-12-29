package domain.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant

@Tag("integration")
class CacheIntegrationTest {
    private lateinit var cache: InMemoryCache<String>

    @BeforeEach
    fun setup() {
        cache = InMemoryCache()
    }

    @Test
    fun `should store and retrieve value from cache`() {
        val key = CacheKey("user:123")
        val value = CacheValue("John Doe")

        cache.put(key, value)
        val result = cache.get(key)

        assertTrue(result.isHit())
        assertEquals("John Doe", result.getOrNull()?.data)
    }

    @Test
    fun `should return miss for non-existent key`() {
        val key = CacheKey("user:999")

        val result = cache.get(key)

        assertTrue(result.isMiss())
    }

    @Test
    fun `should delete value from cache`() {
        val key = CacheKey("user:123")
        val value = CacheValue("John Doe")

        cache.put(key, value)
        cache.delete(key)
        val result = cache.get(key)

        assertTrue(result.isMiss())
    }

    @Test
    fun `should check if key exists in cache`() {
        val key = CacheKey("user:123")
        val value = CacheValue("John Doe")

        cache.put(key, value)

        assertTrue(cache.contains(key))
        assertFalse(cache.contains(CacheKey("user:999")))
    }

    @Test
    fun `should clear all entries from cache`() {
        cache.put(CacheKey("user:1"), CacheValue("Alice"))
        cache.put(CacheKey("user:2"), CacheValue("Bob"))

        cache.clear()

        assertEquals(0, cache.size())
    }

    @Test
    fun `should track cache statistics`() {
        val key = CacheKey("user:123")
        val value = CacheValue("John Doe")

        cache.put(key, value)
        cache.get(key)
        cache.get(CacheKey("user:999"))

        val stats = cache.getStats()

        assertEquals(1, stats.hits)
        assertEquals(1, stats.misses)
        assertEquals(0.5, stats.hitRate, 0.001)
    }

    @Test
    fun `should handle expired values`() {
        val key = CacheKey("user:123")
        val expiredValue = CacheValue("John Doe", expiresAt = Instant.now().minusSeconds(60))

        cache.put(key, expiredValue)
        val result = cache.get(key)

        assertTrue(result.isMiss())
    }

    @Test
    fun `should evict oldest entry when max size is reached with FIFO policy`() {
        val smallCache = InMemoryCache<String>(maxSize = 2, evictionPolicy = EvictionPolicy.FIFO)

        smallCache.put(CacheKey("key1"), CacheValue("value1"))
        smallCache.put(CacheKey("key2"), CacheValue("value2"))
        smallCache.put(CacheKey("key3"), CacheValue("value3"))

        assertTrue(smallCache.get(CacheKey("key1")).isMiss())
        assertTrue(smallCache.get(CacheKey("key2")).isHit())
        assertTrue(smallCache.get(CacheKey("key3")).isHit())
    }

    @Test
    fun `should evict least recently used entry when max size is reached with LRU policy`() {
        val smallCache = InMemoryCache<String>(maxSize = 2, evictionPolicy = EvictionPolicy.LRU)

        smallCache.put(CacheKey("key1"), CacheValue("value1"))
        smallCache.put(CacheKey("key2"), CacheValue("value2"))
        smallCache.get(CacheKey("key1"))
        smallCache.put(CacheKey("key3"), CacheValue("value3"))

        assertTrue(smallCache.get(CacheKey("key1")).isHit())
        assertTrue(smallCache.get(CacheKey("key2")).isMiss())
        assertTrue(smallCache.get(CacheKey("key3")).isHit())
    }

    @Test
    fun `should return all keys in cache`() {
        cache.put(CacheKey("user:1"), CacheValue("Alice"))
        cache.put(CacheKey("user:2"), CacheValue("Bob"))
        cache.put(CacheKey("user:3"), CacheValue("Charlie"))

        val keys = cache.keys()

        assertEquals(3, keys.size)
        assertTrue(keys.contains(CacheKey("user:1")))
        assertTrue(keys.contains(CacheKey("user:2")))
        assertTrue(keys.contains(CacheKey("user:3")))
    }

    @Test
    fun `should remove expired entries`() {
        val expiredValue = CacheValue("expired", expiresAt = Instant.now().minusSeconds(60))
        val validValue = CacheValue("valid", expiresAt = Instant.now().plusSeconds(60))

        cache.put(CacheKey("expired:1"), expiredValue)
        cache.put(CacheKey("expired:2"), expiredValue)
        cache.put(CacheKey("valid:1"), validValue)

        val removed = cache.removeExpired()

        assertEquals(2, removed)
        assertEquals(1, cache.size())
    }
}
