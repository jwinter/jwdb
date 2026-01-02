package infrastructure.network

import com.example.cache.proto.GossipMessage
import com.example.cache.proto.GossipResponse
import domain.gossip.GossipService
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler

/**
 * Netty handler for processing incoming gossip protocol messages.
 *
 * Routes gossip messages to the GossipService for processing and
 * sends back responses as needed.
 */
class GossipProtocolHandler(
    private val gossipService: GossipService
) : SimpleChannelInboundHandler<GossipMessage>() {

    override fun channelRead0(ctx: ChannelHandlerContext, msg: GossipMessage) {
        try {
            // Process the gossip message
            val response = gossipService.handleMessage(msg)

            // Send response if there is one
            if (response != null) {
                ctx.writeAndFlush(response)
            }
        } catch (e: Exception) {
            // Log error and send error response
            println("Error handling gossip message: ${e.message}")

            val errorResponse = GossipResponse.newBuilder()
                .setStatus(GossipResponse.Status.ERROR)
                .setErrorMessage(e.message ?: "Unknown error")
                .build()

            ctx.writeAndFlush(errorResponse)
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        println("Exception in gossip protocol handler: ${cause.message}")
        ctx.close()
    }
}
