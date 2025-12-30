package infrastructure.serialization

/**
 * Interface for serializing and deserializing cache values.
 *
 * Implementations must be thread-safe and handle errors gracefully.
 *
 * @param T The type of object to serialize/deserialize
 */
interface CacheSerializer<T> {
    /**
     * Serializes an object to bytes.
     *
     * @param value The object to serialize
     * @return ByteArray containing the serialized data
     * @throws SerializationException if serialization fails
     */
    fun serialize(value: T): ByteArray

    /**
     * Deserializes bytes back to an object.
     *
     * @param bytes The bytes to deserialize
     * @return The deserialized object
     * @throws SerializationException if deserialization fails or bytes are invalid
     */
    fun deserialize(bytes: ByteArray): T
}

/**
 * Exception thrown when serialization or deserialization fails.
 */
class SerializationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
