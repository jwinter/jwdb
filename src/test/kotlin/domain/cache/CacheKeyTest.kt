package domain.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@Tag("unit")
class CacheKeyTest {
    @Test
    fun `should create cache key from string`() {
        val key = CacheKey("user:123")

        assertEquals("user:123", key.value)
    }

    @Test
    fun `should fail on empty key`() {
        assertThrows<IllegalArgumentException> {
            CacheKey("")
        }
    }

    @Test
    fun `should have consistent hash code`() {
        val key1 = CacheKey("user:123")
        val key2 = CacheKey("user:123")

        assertEquals(key1.hashCode(), key2.hashCode())
    }

    @Test
    fun `should have different hash codes for different keys`() {
        val key1 = CacheKey("user:123")
        val key2 = CacheKey("user:456")

        assertNotEquals(key1.hashCode(), key2.hashCode())
    }

    @Test
    fun `should be equal when values are equal`() {
        val key1 = CacheKey("user:123")
        val key2 = CacheKey("user:123")

        assertEquals(key1, key2)
    }

    @Test
    fun `should convert to string`() {
        val key = CacheKey("user:123")

        assertEquals("user:123", key.toString())
    }
}
