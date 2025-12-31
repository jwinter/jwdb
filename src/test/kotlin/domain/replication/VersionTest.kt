package domain.replication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class VersionTest {
    @Test
    fun `should create version with timestamp and nodeId`() {
        val version = Version(timestamp = 1000L, nodeId = "node1")

        assertEquals(1000L, version.timestamp)
        assertEquals("node1", version.nodeId)
    }

    @Test
    fun `should compare versions by timestamp`() {
        val v1 = Version(timestamp = 1000L, nodeId = "node1")
        val v2 = Version(timestamp = 2000L, nodeId = "node1")

        assertTrue(v2 > v1)
        assertTrue(v1 < v2)
        assertFalse(v1 > v2)
    }

    @Test
    fun `should use nodeId for tie-breaking when timestamps are equal`() {
        val v1 = Version(timestamp = 1000L, nodeId = "node1")
        val v2 = Version(timestamp = 1000L, nodeId = "node2")

        // "node2" > "node1" lexicographically
        assertTrue(v2 > v1)
        assertTrue(v1 < v2)
    }

    @Test
    fun `should consider versions equal when timestamp and nodeId match`() {
        val v1 = Version(timestamp = 1000L, nodeId = "node1")
        val v2 = Version(timestamp = 1000L, nodeId = "node1")

        assertEquals(v1, v2)
        assertEquals(0, v1.compareTo(v2))
    }

    @Test
    fun `isNewerThan should return true for later timestamp`() {
        val v1 = Version(timestamp = 1000L, nodeId = "node1")
        val v2 = Version(timestamp = 2000L, nodeId = "node1")

        assertTrue(v2.isNewerThan(v1))
        assertFalse(v1.isNewerThan(v2))
    }

    @Test
    fun `isOlderThan should return true for earlier timestamp`() {
        val v1 = Version(timestamp = 1000L, nodeId = "node1")
        val v2 = Version(timestamp = 2000L, nodeId = "node1")

        assertTrue(v1.isOlderThan(v2))
        assertFalse(v2.isOlderThan(v1))
    }

    @Test
    fun `should create version with current timestamp using now()`() {
        val before = System.currentTimeMillis()
        val version = Version.now("node1")
        val after = System.currentTimeMillis()

        assertTrue(version.timestamp >= before)
        assertTrue(version.timestamp <= after)
        assertEquals("node1", version.nodeId)
    }

    @Test
    fun `max should return newer version`() {
        val v1 = Version(timestamp = 1000L, nodeId = "node1")
        val v2 = Version(timestamp = 2000L, nodeId = "node1")

        assertEquals(v2, Version.max(v1, v2))
        assertEquals(v2, Version.max(v2, v1))
    }

    @Test
    fun `min should return older version`() {
        val v1 = Version(timestamp = 1000L, nodeId = "node1")
        val v2 = Version(timestamp = 2000L, nodeId = "node1")

        assertEquals(v1, Version.min(v1, v2))
        assertEquals(v1, Version.min(v2, v1))
    }

    @Test
    fun `toString should include timestamp and nodeId`() {
        val version = Version(timestamp = 1000L, nodeId = "node1")

        assertEquals("1000@node1", version.toString())
    }

    @Test
    fun `should handle very large timestamps`() {
        val v1 = Version(timestamp = Long.MAX_VALUE - 1, nodeId = "node1")
        val v2 = Version(timestamp = Long.MAX_VALUE, nodeId = "node1")

        assertTrue(v2 > v1)
    }

    @Test
    fun `should handle lexicographic nodeId comparison correctly`() {
        val versions =
            listOf(
                Version(timestamp = 1000L, nodeId = "node-03"),
                Version(timestamp = 1000L, nodeId = "node-01"),
                Version(timestamp = 1000L, nodeId = "node-10"),
                Version(timestamp = 1000L, nodeId = "node-02"),
            )

        val sorted = versions.sorted()

        assertEquals("node-01", sorted[0].nodeId)
        assertEquals("node-02", sorted[1].nodeId)
        assertEquals("node-03", sorted[2].nodeId)
        assertEquals("node-10", sorted[3].nodeId)
    }
}
