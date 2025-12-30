package infrastructure.serialization

import com.google.protobuf.MessageLite
import com.google.protobuf.Parser

/**
 * Protocol Buffers implementation of CacheSerializer.
 *
 * This serializer handles any protobuf message type and provides
 * efficient binary serialization with schema validation.
 *
 * @param T The protobuf message type to serialize
 * @param parser The protobuf parser for deserializing messages
 */
class ProtobufSerializer<T : MessageLite>(
    private val parser: Parser<T>,
) : CacheSerializer<T> {
    /**
     * Serializes a protobuf message to bytes using protobuf's binary format.
     *
     * @param value The protobuf message to serialize
     * @return ByteArray containing the serialized protobuf message
     * @throws SerializationException if serialization fails
     */
    override fun serialize(value: T): ByteArray =
        try {
            value.toByteArray()
        } catch (e: Exception) {
            throw SerializationException("Failed to serialize protobuf message: ${value::class.simpleName}", e)
        }

    /**
     * Deserializes bytes to a protobuf message using the provided parser.
     *
     * @param bytes The bytes to deserialize
     * @return The deserialized protobuf message
     * @throws SerializationException if bytes are invalid or deserialization fails
     */
    override fun deserialize(bytes: ByteArray): T =
        try {
            parser.parseFrom(bytes)
        } catch (e: Exception) {
            throw SerializationException("Failed to deserialize protobuf message", e)
        }
}
