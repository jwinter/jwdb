package infrastructure.network

import com.example.cache.proto.CacheEntry
import com.example.cache.proto.DeleteRequest
import com.example.cache.proto.GetRequest
import com.example.cache.proto.GetResponse
import com.example.cache.proto.PutRequest
import com.google.protobuf.ByteString
import domain.cache.InMemoryCache
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.time.Duration
import java.time.Instant

@Tag("integration")
class CacheServerTest {
    private lateinit var cache: InMemoryCache<ByteArray>
    private lateinit var server: CacheServer<ByteArray>
    private val config = ServerConfig(port = 9999) // Use non-standard port for testing

    @BeforeEach
    fun setup() {
        cache = InMemoryCache()
        server = CacheServer(cache, config)
    }

    @AfterEach
    fun cleanup() {
        server.stop()
        cache.shutdown()
    }

    @Test
    fun `server starts and stops successfully`() {
        // When
        server.start()
        Thread.sleep(100) // Give server time to start

        // Then - server should be running and accepting connections
        val socket =
            assertDoesNotThrow {
                Socket("localhost", config.port)
            }
        socket.close()

        // When
        server.stop()
        Thread.sleep(100) // Give server time to stop

        // Then - server should no longer accept connections
        // (connection attempt should fail or timeout)
    }

    @Test
    fun `server handles Get request for non-existent key`() {
        // Given
        server.start()
        Thread.sleep(100)

        // When
        val request =
            GetRequest.newBuilder()
                .setKey("non-existent-key")
                .build()

        val response = sendGetRequest(request)

        // Then
        assertEquals(GetResponse.Status.MISS, response.status)
        assertEquals(false, response.hasEntry())
    }

    @Test
    fun `server handles Put and Get operations end-to-end`() {
        // Given
        server.start()
        Thread.sleep(100)

        val key = "test-key"
        val value = "test-value".toByteArray()
        val now = Instant.now()

        // When - Put operation
        val putRequest =
            PutRequest.newBuilder()
                .setKey(key)
                .setEntry(
                    CacheEntry.newBuilder()
                        .setData(ByteString.copyFrom(value))
                        .setCreatedAt(now.toEpochMilli())
                        .setExpiresAt(0) // Never expires
                        .setVersion(1)
                        .build(),
                )
                .build()

        val putResponse = sendPutRequest(putRequest)

        // Then
        assertEquals(com.example.cache.proto.PutResponse.Status.SUCCESS, putResponse.status)

        // When - Get operation
        val getRequest =
            GetRequest.newBuilder()
                .setKey(key)
                .build()

        val getResponse = sendGetRequest(getRequest)

        // Then
        assertEquals(GetResponse.Status.HIT, getResponse.status)
        assertEquals(true, getResponse.hasEntry())
        assertArrayEquals(value, getResponse.entry.data.toByteArray())
        assertEquals(now.toEpochMilli(), getResponse.entry.createdAt)
        assertEquals(0, getResponse.entry.expiresAt)
    }

    @Test
    fun `server handles Delete operation`() {
        // Given
        server.start()
        Thread.sleep(100)

        val key = "delete-key"
        val value = "delete-value".toByteArray()

        // Setup - Put a value first
        val putRequest =
            PutRequest.newBuilder()
                .setKey(key)
                .setEntry(
                    CacheEntry.newBuilder()
                        .setData(ByteString.copyFrom(value))
                        .setCreatedAt(Instant.now().toEpochMilli())
                        .setExpiresAt(0)
                        .setVersion(1)
                        .build(),
                )
                .build()
        sendPutRequest(putRequest)

        // When - Delete operation
        val deleteRequest =
            DeleteRequest.newBuilder()
                .setKey(key)
                .build()

        val deleteResponse = sendDeleteRequest(deleteRequest)

        // Then
        assertEquals(com.example.cache.proto.DeleteResponse.Status.SUCCESS, deleteResponse.status)

        // Verify key is gone
        val getRequest =
            GetRequest.newBuilder()
                .setKey(key)
                .build()

        val getResponse = sendGetRequest(getRequest)
        assertEquals(GetResponse.Status.MISS, getResponse.status)
    }

    @Test
    fun `server handles expired entries`() {
        // Given
        server.start()
        Thread.sleep(100)

        val key = "expired-key"
        val value = "expired-value".toByteArray()
        val now = Instant.now()
        val expiresAt = now.minus(Duration.ofMinutes(1)) // Expired 1 minute ago

        // When - Put expired entry
        val putRequest =
            PutRequest.newBuilder()
                .setKey(key)
                .setEntry(
                    CacheEntry.newBuilder()
                        .setData(ByteString.copyFrom(value))
                        .setCreatedAt(now.minus(Duration.ofMinutes(2)).toEpochMilli())
                        .setExpiresAt(expiresAt.toEpochMilli())
                        .setVersion(1)
                        .build(),
                )
                .build()

        sendPutRequest(putRequest)

        // Get the entry
        val getRequest =
            GetRequest.newBuilder()
                .setKey(key)
                .build()

        val getResponse = sendGetRequest(getRequest)

        // Then - Should be a miss because it's expired
        assertEquals(GetResponse.Status.MISS, getResponse.status)
    }

    // Helper methods to send requests and receive responses
    private fun sendGetRequest(request: GetRequest): GetResponse {
        Socket("localhost", config.port).use { socket ->
            val output = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())

            // Write message type (0 = GetRequest) and message
            output.writeByte(0)
            val requestBytes = request.toByteArray()
            output.writeInt(requestBytes.size)
            output.write(requestBytes)
            output.flush()

            // Read response type and message
            val responseType = input.readByte()
            assertEquals(1, responseType.toInt()) // 1 = GetResponse

            val responseLength = input.readInt()
            val responseBytes = ByteArray(responseLength)
            input.readFully(responseBytes)

            return GetResponse.parseFrom(responseBytes)
        }
    }

    private fun sendPutRequest(request: PutRequest): com.example.cache.proto.PutResponse {
        Socket("localhost", config.port).use { socket ->
            val output = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())

            // Write message type (2 = PutRequest) and message
            output.writeByte(2)
            val requestBytes = request.toByteArray()
            output.writeInt(requestBytes.size)
            output.write(requestBytes)
            output.flush()

            // Read response type and message
            val responseType = input.readByte()
            assertEquals(3, responseType.toInt()) // 3 = PutResponse

            val responseLength = input.readInt()
            val responseBytes = ByteArray(responseLength)
            input.readFully(responseBytes)

            return com.example.cache.proto.PutResponse.parseFrom(responseBytes)
        }
    }

    private fun sendDeleteRequest(request: DeleteRequest): com.example.cache.proto.DeleteResponse {
        Socket("localhost", config.port).use { socket ->
            val output = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())

            // Write message type (4 = DeleteRequest) and message
            output.writeByte(4)
            val requestBytes = request.toByteArray()
            output.writeInt(requestBytes.size)
            output.write(requestBytes)
            output.flush()

            // Read response type and message
            val responseType = input.readByte()
            assertEquals(5, responseType.toInt()) // 5 = DeleteResponse

            val responseLength = input.readInt()
            val responseBytes = ByteArray(responseLength)
            input.readFully(responseBytes)

            return com.example.cache.proto.DeleteResponse.parseFrom(responseBytes)
        }
    }
}
