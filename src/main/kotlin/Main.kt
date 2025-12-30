import domain.cache.InMemoryCache
import infrastructure.network.CacheServer
import infrastructure.network.ServerConfig

fun main() {
    println("Starting distributed cache server...")

    // Create cache instance
    val cache =
        InMemoryCache<ByteArray>(
            maxSize = 10000,
            enableAutoCleanup = true,
            cleanupIntervalSeconds = 60,
        )

    // Create server with default configuration (port 8080)
    val config =
        ServerConfig(
            port = 8080,
            workerThreads = Runtime.getRuntime().availableProcessors(),
        )
    val server = CacheServer(cache, config)

    // Add shutdown hook for graceful shutdown
    Runtime.getRuntime().addShutdownHook(
        Thread {
            println("\nShutting down server...")
            server.stop()
            cache.shutdown()
            println("Server stopped gracefully")
        },
    )

    // Start the server
    server.start()
    println("Server is running on port ${config.port}")
    println("Press Ctrl+C to stop")

    // Keep the application running
    Thread.currentThread().join()
}
