package infrastructure.network

import com.example.cache.proto.GossipMessage
import domain.gossip.GossipTransport
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Netty-based implementation of GossipTransport for sending gossip messages over TCP.
 *
 * Features:
 * - Connection pooling and reuse
 * - Async message sending with timeout support
 * - Automatic connection cleanup
 * - Thread-safe concurrent access
 */
class NettyGossipTransport(
    private val workerGroup: EventLoopGroup = NioEventLoopGroup()
) : GossipTransport {

    private val connections = ConcurrentHashMap<InetSocketAddress, Channel>()
    private val pendingResponses = ConcurrentHashMap<Long, CompletableFuture<GossipMessage>>()

    /**
     * Send a gossip message and wait for response.
     */
    override fun sendMessage(
        targetAddress: InetSocketAddress,
        message: GossipMessage,
        timeout: Duration
    ): GossipMessage? {
        return try {
            val future = CompletableFuture<GossipMessage>()
            pendingResponses[message.sequenceNumber] = future

            val channel = getOrCreateConnection(targetAddress)

            // Send message
            channel.writeAndFlush(message).addListener { sendFuture ->
                if (!sendFuture.isSuccess) {
                    future.completeExceptionally(sendFuture.cause())
                    pendingResponses.remove(message.sequenceNumber)
                }
            }

            // Wait for response with timeout
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            pendingResponses.remove(message.sequenceNumber)
            null // Timeout or error - return null as per interface contract
        }
    }

    /**
     * Handle incoming gossip response.
     */
    fun handleResponse(response: GossipMessage) {
        val future = pendingResponses.remove(response.sequenceNumber)
        future?.complete(response)
    }

    /**
     * Get or create a connection to the target address.
     */
    private fun getOrCreateConnection(address: InetSocketAddress): Channel {
        // Try to reuse existing connection
        connections[address]?.let { channel ->
            if (channel.isActive) {
                return channel
            } else {
                connections.remove(address)
            }
        }

        // Create new connection
        val bootstrap = Bootstrap()
            .group(workerGroup)
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().addLast(
                        GossipCodec(),
                        GossipResponseHandler(this@NettyGossipTransport)
                    )
                }
            })

        val channelFuture = bootstrap.connect(address).sync()
        val channel = channelFuture.channel()

        // Store connection for reuse
        connections[address] = channel

        // Remove from pool when closed
        channel.closeFuture().addListener {
            connections.remove(address)
        }

        return channel
    }

    /**
     * Close all connections and shutdown.
     */
    fun shutdown() {
        connections.values.forEach { it.close() }
        connections.clear()
        pendingResponses.clear()
        workerGroup.shutdownGracefully()
    }
}

/**
 * Handler for incoming gossip responses.
 */
private class GossipResponseHandler(
    private val transport: NettyGossipTransport
) : SimpleChannelInboundHandler<GossipMessage>() {

    override fun channelRead0(ctx: ChannelHandlerContext, msg: GossipMessage) {
        transport.handleResponse(msg)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close()
    }
}
