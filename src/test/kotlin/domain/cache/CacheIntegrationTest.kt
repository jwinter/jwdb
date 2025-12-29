package domain.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("integration")
class CacheIntegrationTest {
    @Test
    fun `should store and retrieve value from in-memory cache`() {
        val cache = mutableMapOf<String, String>()
        cache["user:123"] = "John"

        val result = cache["user:123"]

        assertEquals("John", result)
    }

    @Test
    fun `should return null for missing key`() {
        val cache = mutableMapOf<String, String>()

        val result = cache["user:999"]

        assertNull(result)
    }
}
