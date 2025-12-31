package domain.replication

import domain.cache.CacheValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

@Tag("unit")
class ConflictResolverTest {
    private val resolver = LastWriteWinsResolver()

    @Test
    fun `should throw exception when resolving empty list`() {
        assertThrows<IllegalArgumentException> {
            resolver.resolve(emptyList<CacheValue<String>>())
        }
    }

    @Test
    fun `should return single value when no conflict exists`() {
        val version = Version(timestamp = 1000L, nodeId = "node1")
        val value = CacheValue(data = "test", version = version)

        val result = resolver.resolve(listOf(value))

        assertEquals(value, result)
    }

    @Test
    fun `should resolve conflict by selecting latest timestamp`() {
        val v1 = Version(timestamp = 1000L, nodeId = "node1")
        val v2 = Version(timestamp = 2000L, nodeId = "node2")
        val v3 = Version(timestamp = 1500L, nodeId = "node3")

        val value1 = CacheValue(data = "old", version = v1)
        val value2 = CacheValue(data = "newest", version = v2)
        val value3 = CacheValue(data = "middle", version = v3)

        val result = resolver.resolve(listOf(value1, value2, value3))

        assertEquals("newest", result.data)
        assertEquals(v2, result.version)
    }

    @Test
    fun `should use nodeId for tie-breaking when timestamps are equal`() {
        val v1 = Version(timestamp = 1000L, nodeId = "node-a")
        val v2 = Version(timestamp = 1000L, nodeId = "node-z")
        val v3 = Version(timestamp = 1000L, nodeId = "node-m")

        val value1 = CacheValue(data = "from-a", version = v1)
        val value2 = CacheValue(data = "from-z", version = v2)
        val value3 = CacheValue(data = "from-m", version = v3)

        val result = resolver.resolve(listOf(value1, value2, value3))

        // "node-z" is lexicographically greatest
        assertEquals("from-z", result.data)
        assertEquals(v2, result.version)
    }

    @Test
    fun `should handle null versions by treating them as oldest`() {
        val v1 = Version(timestamp = 1000L, nodeId = "node1")

        val value1 = CacheValue(data = "without-version", version = null)
        val value2 = CacheValue(data = "with-version", version = v1)

        val result = resolver.resolve(listOf(value1, value2))

        assertEquals("with-version", result.data)
        assertEquals(v1, result.version)
    }

    @Test
    fun `should use creation time when both versions are null`() {
        val older = Instant.now().minusSeconds(60)
        val newer = Instant.now()

        val value1 = CacheValue(data = "older", createdAt = older, version = null)
        val value2 = CacheValue(data = "newer", createdAt = newer, version = null)

        val result = resolver.resolve(listOf(value1, value2))

        assertEquals("newer", result.data)
    }

    @Test
    fun `should handle realistic concurrent write scenario`() {
        // Simulate three nodes writing the same key concurrently
        val node1Write = CacheValue(data = "write-from-node1", version = Version(1000L, "node1"))
        val node2Write = CacheValue(data = "write-from-node2", version = Version(1001L, "node2"))
        val node3Write = CacheValue(data = "write-from-node3", version = Version(999L, "node3"))

        val result = resolver.resolve(listOf(node1Write, node2Write, node3Write))

        // node2's write was 1ms later, so it wins
        assertEquals("write-from-node2", result.data)
        assertEquals(Version(1001L, "node2"), result.version)
    }

    @Test
    fun `should be deterministic for same inputs`() {
        val v1 = Version(timestamp = 1000L, nodeId = "node1")
        val v2 = Version(timestamp = 2000L, nodeId = "node2")

        val value1 = CacheValue(data = "data1", version = v1)
        val value2 = CacheValue(data = "data2", version = v2)

        // Run multiple times with different orders
        val result1 = resolver.resolve(listOf(value1, value2))
        val result2 = resolver.resolve(listOf(value2, value1))
        val result3 = resolver.resolve(listOf(value1, value2, value1))

        assertEquals(result1.data, result2.data)
        assertEquals(result1.data, result3.data)
        assertEquals("data2", result1.data)
    }

    @Test
    fun `should handle many conflicting versions`() {
        val values =
            (1..100).map { i ->
                CacheValue(
                    data = "value-$i",
                    version = Version(timestamp = i.toLong() * 100, nodeId = "node-$i"),
                )
            }

        val result = resolver.resolve(values)

        // Highest timestamp should win
        assertEquals("value-100", result.data)
        assertEquals(10000L, result.version?.timestamp)
    }
}
