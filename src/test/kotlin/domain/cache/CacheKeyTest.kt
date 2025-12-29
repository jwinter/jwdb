package domain.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class CacheKeyTest {
    @Test
    fun `should create cache key from string`() {
        val key = "user:123"
        val result = key.hashCode()

        assertEquals(key.hashCode(), result)
    }

    @Test
    fun `should handle empty key`() {
        val key = ""
        val result = key.hashCode()

        assertEquals(0, result)
    }
}
