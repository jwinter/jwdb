package domain.cache

import domain.replication.Version
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
        assertNull(value.version) // Version is null by default in distributed mode
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
    fun `should update data with new version`() {
        val v1 = Version(timestamp = 1000L, nodeId = "node1")
        val original = CacheValue("original data", version = v1)

        val v2 = Version(timestamp = 2000L, nodeId = "node1")
        val updated = original.withData("new data", v2)

        assertEquals("new data", updated.data)
        assertNotNull(updated.version)
        assertEquals(2000L, updated.version?.timestamp)
        assertEquals("node1", updated.version?.nodeId)
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
