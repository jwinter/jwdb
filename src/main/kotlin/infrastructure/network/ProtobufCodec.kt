package infrastructure.network

import com.example.cache.proto.DeleteRequest
import com.example.cache.proto.DeleteResponse
import com.example.cache.proto.GetRequest
import com.example.cache.proto.GetResponse
import com.example.cache.proto.GossipMessage as ProtoGossipMessage
import com.example.cache.proto.PutRequest
import com.example.cache.proto.PutResponse
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageCodec

/**
 * Netty codec for encoding and decoding protobuf cache messages.
 *
 * Message format:
 * - 1 byte: message type (0=GetRequest, 1=GetResponse, 2=PutRequest, 3=PutResponse, 4=DeleteRequest, 5=DeleteResponse)
 * - 4 bytes: message length (big-endian int)
 * - N bytes: protobuf serialized message
 */
class ProtobufCodec : ByteToMessageCodec<CacheMessage>() {
    companion object {
        private const val TYPE_GET_REQUEST: Byte = 0
        private const val TYPE_GET_RESPONSE: Byte = 1
        private const val TYPE_PUT_REQUEST: Byte = 2
        private const val TYPE_PUT_RESPONSE: Byte = 3
        private const val TYPE_DELETE_REQUEST: Byte = 4
        private const val TYPE_DELETE_RESPONSE: Byte = 5
        private const val TYPE_GOSSIP: Byte = 6

        private const val HEADER_SIZE = 5 // 1 byte type + 4 bytes length
    }

    override fun encode(
        ctx: ChannelHandlerContext,
        msg: CacheMessage,
        out: ByteBuf,
    ) {
        val (type, bytes) =
            when (msg) {
                is CacheMessage.Request.Get -> TYPE_GET_REQUEST to msg.request.toByteArray()
                is CacheMessage.Response.Get -> TYPE_GET_RESPONSE to msg.response.toByteArray()
                is CacheMessage.Request.Put -> TYPE_PUT_REQUEST to msg.request.toByteArray()
                is CacheMessage.Response.Put -> TYPE_PUT_RESPONSE to msg.response.toByteArray()
                is CacheMessage.Request.Delete -> TYPE_DELETE_REQUEST to msg.request.toByteArray()
                is CacheMessage.Response.Delete -> TYPE_DELETE_RESPONSE to msg.response.toByteArray()
                is CacheMessage.Gossip -> TYPE_GOSSIP to msg.message.toByteArray()
            }

        out.writeByte(type.toInt())
        out.writeInt(bytes.size)
        out.writeBytes(bytes)
    }

    override fun decode(
        ctx: ChannelHandlerContext,
        input: ByteBuf,
        out: MutableList<Any>,
    ) {
        // Wait until we have at least the header
        if (input.readableBytes() < HEADER_SIZE) {
            return
        }

        // Mark the current reader index
        input.markReaderIndex()

        // Read header
        val type = input.readByte()
        val length = input.readInt()

        // Validate length
        if (length < 0 || length > 10_000_000) { // 10MB max message size
            throw IllegalArgumentException("Invalid message length: $length")
        }

        // Wait until we have the full message
        if (input.readableBytes() < length) {
            input.resetReaderIndex()
            return
        }

        // Read message bytes
        val bytes = ByteArray(length)
        input.readBytes(bytes)

        // Decode based on type
        val message =
            when (type) {
                TYPE_GET_REQUEST -> CacheMessage.Request.Get(GetRequest.parseFrom(bytes))
                TYPE_GET_RESPONSE -> CacheMessage.Response.Get(GetResponse.parseFrom(bytes))
                TYPE_PUT_REQUEST -> CacheMessage.Request.Put(PutRequest.parseFrom(bytes))
                TYPE_PUT_RESPONSE -> CacheMessage.Response.Put(PutResponse.parseFrom(bytes))
                TYPE_DELETE_REQUEST -> CacheMessage.Request.Delete(DeleteRequest.parseFrom(bytes))
                TYPE_DELETE_RESPONSE -> CacheMessage.Response.Delete(DeleteResponse.parseFrom(bytes))
                TYPE_GOSSIP -> CacheMessage.Gossip(ProtoGossipMessage.parseFrom(bytes))
                else -> throw IllegalArgumentException("Unknown message type: $type")
            }

        out.add(message)
    }
}
