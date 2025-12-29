package domain.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant

@Tag("unit")
class CacheValueTest {
    @Test
    fun `should create cache value with data`() {
        val value = CacheValue("test data")

        assertEquals("test data", value.data)
        assertEquals(1L, value.version)
    }

    @Test
    fun `should not be expired when no expiration is set`() {
        val value = CacheValue("test data")

        assertFalse(value.isExpired())
    }

    @Test
    fun `should be expired when expiration time has passed`() {
        val pastTime = Instant.now().minusSeconds(60)
        val value = CacheValue("test data", expiresAt = pastTime)

        assertTrue(value.isExpired())
    }

    @Test
    fun `should not be expired when expiration time is in future`() {
        val futureTime = Instant.now().plusSeconds(60)
        val value = CacheValue("test data", expiresAt = futureTime)

        assertFalse(value.isExpired())
    }

    @Test
    fun `should increment version when updating data`() {
        val original = CacheValue("original data")
        val updated = original.withData("new data")

        assertEquals("new data", updated.data)
        assertEquals(2L, updated.version)
    }

    @Test
    fun `should set TTL correctly`() {
        val value = CacheValue("test data")
        val withTtl = value.withTtl(300)

        assertTrue(withTtl.expiresAt != null)
        assertFalse(withTtl.isExpired())
    }

    @Test
    fun `should preserve data when setting TTL`() {
        val value = CacheValue("test data")
        val withTtl = value.withTtl(300)

        assertEquals("test data", withTtl.data)
    }
}
