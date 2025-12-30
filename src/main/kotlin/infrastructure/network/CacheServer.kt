package infrastructure.network

import domain.cache.Cache
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import java.util.concurrent.TimeUnit

/**
 * High-performance cache server using Netty.
 *
 * Handles client connections and processes cache operations via protobuf protocol.
 * Supports concurrent connections with non-blocking I/O.
 *
 * @param cache The cache implementation to serve
 * @param config Server configuration
 */
class CacheServer<T>(
    private val cache: Cache<T>,
    private val config: ServerConfig = ServerConfig(),
) {
    private var bossGroup: NioEventLoopGroup? = null
    private var workerGroup: NioEventLoopGroup? = null

    /**
     * Starts the server and begins accepting client connections.
     * This method returns immediately; the server runs in background threads.
     */
    fun start() {
        bossGroup = NioEventLoopGroup(config.bossThreads)
        workerGroup = NioEventLoopGroup(config.workerThreads)

        val bootstrap = ServerBootstrap()
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(
                object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(
                            ProtobufCodec(),
                            CacheProtocolHandler(cache),
                        )
                    }
                },
            )
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.SO_KEEPALIVE, true)

        // Bind and start to accept incoming connections
        val future = bootstrap.bind(config.port).sync()

        println("Cache server started on port ${config.port}")
    }

    /**
     * Stops the server gracefully.
     * Waits for in-flight requests to complete before shutting down.
     */
    fun stop() {
        try {
            workerGroup?.shutdownGracefully(0, config.shutdownTimeoutSeconds, TimeUnit.SECONDS)?.sync()
            bossGroup?.shutdownGracefully(0, config.shutdownTimeoutSeconds, TimeUnit.SECONDS)?.sync()
            println("Cache server stopped")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            println("Server shutdown interrupted: ${e.message}")
        }
    }
}
