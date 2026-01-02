package infrastructure.network

import com.example.cache.proto.GossipMessage
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageCodec

/**
 * Netty codec for encoding/decoding GossipMessage protobuf messages.
 *
 * Message format:
 * - 4 bytes: message length (int)
 * - N bytes: protobuf-encoded GossipMessage
 */
class GossipCodec : ByteToMessageCodec<GossipMessage>() {

    companion object {
        private const val MAX_MESSAGE_SIZE = 10 * 1024 * 1024 // 10MB
    }

    override fun encode(ctx: ChannelHandlerContext, msg: GossipMessage, out: ByteBuf) {
        val bytes = msg.toByteArray()

        // Write length prefix
        out.writeInt(bytes.size)

        // Write protobuf message
        out.writeBytes(bytes)
    }

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        // Need at least 4 bytes for length
        if (input.readableBytes() < 4) {
            return
        }

        // Mark reader index to reset if we don't have full message
        input.markReaderIndex()

        // Read message length
        val length = input.readInt()

        // Validate message size
        if (length > MAX_MESSAGE_SIZE || length < 0) {
            throw IllegalArgumentException("Invalid gossip message size: $length")
        }

        // Check if we have the full message
        if (input.readableBytes() < length) {
            input.resetReaderIndex()
            return
        }

        // Read protobuf bytes
        val bytes = ByteArray(length)
        input.readBytes(bytes)

        // Decode protobuf message
        val message = GossipMessage.parseFrom(bytes)
        out.add(message)
    }
}
