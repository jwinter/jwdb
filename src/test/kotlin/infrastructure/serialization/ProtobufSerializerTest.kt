package infrastructure.serialization

import com.example.cache.proto.CacheEntry
import com.example.cache.proto.DeleteRequest
import com.example.cache.proto.DeleteResponse
import com.example.cache.proto.GetRequest
import com.example.cache.proto.GetResponse
import com.example.cache.proto.PutRequest
import com.example.cache.proto.PutResponse
import com.example.cache.proto.User
import com.example.cache.proto.cacheEntry
import com.example.cache.proto.deleteRequest
import com.example.cache.proto.deleteResponse
import com.example.cache.proto.getRequest
import com.example.cache.proto.getResponse
import com.example.cache.proto.putRequest
import com.example.cache.proto.putResponse
import com.example.cache.proto.user
import com.google.protobuf.ByteString
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class ProtobufSerializerTest {
    @Test
    fun `should serialize and deserialize User message`() {
        val serializer = ProtobufSerializer(User.parser())

        val original =
            user {
                id = "user123"
                name = "John Doe"
                email = "john@example.com"
                age = 30
                active = true
                roles.add("admin")
                roles.add("user")
            }

        // Serialize
        val bytes = serializer.serialize(original)

        // Deserialize
        val deserialized = serializer.deserialize(bytes)

        // Verify
        assertEquals(original.id, deserialized.id)
        assertEquals(original.name, deserialized.name)
        assertEquals(original.email, deserialized.email)
        assertEquals(original.age, deserialized.age)
        assertEquals(original.active, deserialized.active)
        assertEquals(original.rolesList, deserialized.rolesList)
    }

    @Test
    fun `should serialize and deserialize CacheEntry message`() {
        val serializer = ProtobufSerializer(CacheEntry.parser())

        val original =
            cacheEntry {
                data = ByteString.copyFromUtf8("test data")
                createdAt = 1234567890L
                expiresAt = 1234567900L
                version = 1L
            }

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)

        assertEquals(original.data, deserialized.data)
        assertEquals(original.createdAt, deserialized.createdAt)
        assertEquals(original.expiresAt, deserialized.expiresAt)
        assertEquals(original.version, deserialized.version)
    }

    @Test
    fun `should serialize and deserialize GetRequest message`() {
        val serializer = ProtobufSerializer(GetRequest.parser())

        val original =
            getRequest {
                key = "test-key"
            }

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)

        assertEquals(original.key, deserialized.key)
    }

    @Test
    fun `should serialize and deserialize GetResponse with HIT status`() {
        val serializer = ProtobufSerializer(GetResponse.parser())

        val entry =
            cacheEntry {
                data = ByteString.copyFromUtf8("value")
                createdAt = 1000L
                expiresAt = 2000L
                version = 1L
            }

        val original =
            getResponse {
                status = GetResponse.Status.HIT
                this.entry = entry
            }

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)

        assertEquals(GetResponse.Status.HIT, deserialized.status)
        assertEquals(original.entry.data, deserialized.entry.data)
    }

    @Test
    fun `should serialize and deserialize GetResponse with MISS status`() {
        val serializer = ProtobufSerializer(GetResponse.parser())

        val original =
            getResponse {
                status = GetResponse.Status.MISS
            }

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)

        assertEquals(GetResponse.Status.MISS, deserialized.status)
    }

    @Test
    fun `should serialize and deserialize GetResponse with ERROR status`() {
        val serializer = ProtobufSerializer(GetResponse.parser())

        val original =
            getResponse {
                status = GetResponse.Status.ERROR
                errorMessage = "Key not found"
            }

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)

        assertEquals(GetResponse.Status.ERROR, deserialized.status)
        assertEquals("Key not found", deserialized.errorMessage)
    }

    @Test
    fun `should serialize and deserialize PutRequest message`() {
        val serializer = ProtobufSerializer(PutRequest.parser())

        val entry =
            cacheEntry {
                data = ByteString.copyFromUtf8("test value")
                createdAt = System.currentTimeMillis()
                expiresAt = 0L
                version = 1L
            }

        val original =
            putRequest {
                key = "mykey"
                this.entry = entry
            }

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)

        assertEquals(original.key, deserialized.key)
        assertEquals(original.entry.data, deserialized.entry.data)
    }

    @Test
    fun `should serialize and deserialize PutResponse with SUCCESS status`() {
        val serializer = ProtobufSerializer(PutResponse.parser())

        val original =
            putResponse {
                status = PutResponse.Status.SUCCESS
            }

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)

        assertEquals(PutResponse.Status.SUCCESS, deserialized.status)
    }

    @Test
    fun `should serialize and deserialize PutResponse with ERROR status`() {
        val serializer = ProtobufSerializer(PutResponse.parser())

        val original =
            putResponse {
                status = PutResponse.Status.ERROR
                errorMessage = "Cache full"
            }

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)

        assertEquals(PutResponse.Status.ERROR, deserialized.status)
        assertEquals("Cache full", deserialized.errorMessage)
    }

    @Test
    fun `should serialize and deserialize DeleteRequest message`() {
        val serializer = ProtobufSerializer(DeleteRequest.parser())

        val original =
            deleteRequest {
                key = "key-to-delete"
            }

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)

        assertEquals(original.key, deserialized.key)
    }

    @Test
    fun `should serialize and deserialize DeleteResponse with SUCCESS status`() {
        val serializer = ProtobufSerializer(DeleteResponse.parser())

        val original =
            deleteResponse {
                status = DeleteResponse.Status.SUCCESS
            }

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)

        assertEquals(DeleteResponse.Status.SUCCESS, deserialized.status)
    }

    @Test
    fun `should serialize and deserialize DeleteResponse with ERROR status`() {
        val serializer = ProtobufSerializer(DeleteResponse.parser())

        val original =
            deleteResponse {
                status = DeleteResponse.Status.ERROR
                errorMessage = "Key not found"
            }

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)

        assertEquals(DeleteResponse.Status.ERROR, deserialized.status)
        assertEquals("Key not found", deserialized.errorMessage)
    }

    @Test
    fun `should handle empty User message`() {
        val serializer = ProtobufSerializer(User.parser())

        val original = user {}

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)

        assertEquals("", deserialized.id)
        assertEquals("", deserialized.name)
        assertEquals("", deserialized.email)
        assertEquals(0, deserialized.age)
        assertEquals(false, deserialized.active)
        assertTrue(deserialized.rolesList.isEmpty())
    }

    @Test
    fun `should handle User with special characters`() {
        val serializer = ProtobufSerializer(User.parser())

        val original =
            user {
                id = "user-\u0001-\u0002-\u0003"
                name = "José García"
                email = "josé@例え.com"
            }

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)

        assertEquals(original.id, deserialized.id)
        assertEquals(original.name, deserialized.name)
        assertEquals(original.email, deserialized.email)
    }

    @Test
    fun `should handle CacheEntry with empty data`() {
        val serializer = ProtobufSerializer(CacheEntry.parser())

        val original =
            cacheEntry {
                data = ByteString.EMPTY
                createdAt = 0L
                expiresAt = 0L
                version = 0L
            }

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)

        assertEquals(ByteString.EMPTY, deserialized.data)
    }

    @Test
    fun `should handle CacheEntry with large data`() {
        val serializer = ProtobufSerializer(CacheEntry.parser())

        val largeData = ByteArray(1024 * 1024) { it.toByte() } // 1MB
        val original =
            cacheEntry {
                data = ByteString.copyFrom(largeData)
                createdAt = System.currentTimeMillis()
                expiresAt = 0L
                version = 1L
            }

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)

        assertArrayEquals(largeData, deserialized.data.toByteArray())
    }

    @Test
    fun `should throw SerializationException on invalid bytes for deserialization`() {
        val serializer = ProtobufSerializer(User.parser())

        val invalidBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte())

        val exception =
            assertThrows(SerializationException::class.java) {
                serializer.deserialize(invalidBytes)
            }

        assertTrue(exception.message!!.contains("Failed to deserialize"))
    }

    @Test
    fun `should handle User with maximum values`() {
        val serializer = ProtobufSerializer(User.parser())

        val original =
            user {
                id = "x".repeat(10000) // Long string
                name = "John Doe"
                email = "john@example.com"
                age = Int.MAX_VALUE
                active = true
                repeat(1000) {
                    roles.add("role-$it")
                }
            }

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)

        assertEquals(original.id, deserialized.id)
        assertEquals(original.age, deserialized.age)
        assertEquals(original.rolesList.size, deserialized.rolesList.size)
    }

    @Test
    fun `serialized bytes should be compact and efficient`() {
        val serializer = ProtobufSerializer(User.parser())

        val original =
            user {
                id = "123"
                name = "John"
                email = "j@e.com"
                age = 30
                active = true
            }

        val bytes = serializer.serialize(original)

        // Protobuf should be more compact than equivalent JSON
        // Basic sanity check - should be under 100 bytes for this simple message
        assertTrue(bytes.size < 100, "Protobuf size: ${bytes.size} bytes")
    }

    @Test
    fun `should maintain binary compatibility across roundtrips`() {
        val serializer = ProtobufSerializer(CacheEntry.parser())

        val original =
            cacheEntry {
                data = ByteString.copyFromUtf8("test")
                createdAt = 1000L
                expiresAt = 2000L
                version = 1L
            }

        // First roundtrip
        val bytes1 = serializer.serialize(original)
        val deserialized1 = serializer.deserialize(bytes1)

        // Second roundtrip
        val bytes2 = serializer.serialize(deserialized1)
        val deserialized2 = serializer.deserialize(bytes2)

        // Bytes should be identical
        assertArrayEquals(bytes1, bytes2)

        // Objects should be equal
        assertEquals(deserialized1, deserialized2)
    }
}
