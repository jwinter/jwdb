package domain.replication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@Tag("unit")
class ConsistencyLevelTest {
    @Test
    fun `ONE consistency level requires 1 response`() {
        assertEquals(1, ConsistencyLevel.ONE.requiredResponses(1))
        assertEquals(1, ConsistencyLevel.ONE.requiredResponses(3))
        assertEquals(1, ConsistencyLevel.ONE.requiredResponses(5))
    }

    @Test
    fun `QUORUM consistency level calculates majority correctly`() {
        assertEquals(1, ConsistencyLevel.QUORUM.requiredResponses(1))
        assertEquals(2, ConsistencyLevel.QUORUM.requiredResponses(2))
        assertEquals(2, ConsistencyLevel.QUORUM.requiredResponses(3))
        assertEquals(3, ConsistencyLevel.QUORUM.requiredResponses(4))
        assertEquals(3, ConsistencyLevel.QUORUM.requiredResponses(5))
        assertEquals(4, ConsistencyLevel.QUORUM.requiredResponses(6))
    }

    @Test
    fun `ALL consistency level requires all responses`() {
        assertEquals(1, ConsistencyLevel.ALL.requiredResponses(1))
        assertEquals(3, ConsistencyLevel.ALL.requiredResponses(3))
        assertEquals(5, ConsistencyLevel.ALL.requiredResponses(5))
    }

    @Test
    fun `requiredResponses throws exception for invalid replication factor`() {
        assertThrows<IllegalArgumentException> {
            ConsistencyLevel.ONE.requiredResponses(0)
        }

        assertThrows<IllegalArgumentException> {
            ConsistencyLevel.QUORUM.requiredResponses(-1)
        }

        assertThrows<IllegalArgumentException> {
            ConsistencyLevel.ALL.requiredResponses(0)
        }
    }

    @Test
    fun `QUORUM with RF=3 requires 2 responses`() {
        // Standard Cassandra-style replication with RF=3
        val rf = 3
        val quorum = ConsistencyLevel.QUORUM.requiredResponses(rf)

        assertEquals(2, quorum)
    }

    @Test
    fun `consistency levels have correct ordering for availability vs consistency`() {
        // ONE is most available (lowest requirement)
        val oneRequired = ConsistencyLevel.ONE.requiredResponses(5)

        // QUORUM is balanced
        val quorumRequired = ConsistencyLevel.QUORUM.requiredResponses(5)

        // ALL is strongest consistency (highest requirement)
        val allRequired = ConsistencyLevel.ALL.requiredResponses(5)

        assert(oneRequired < quorumRequired)
        assert(quorumRequired < allRequired)
    }
}
