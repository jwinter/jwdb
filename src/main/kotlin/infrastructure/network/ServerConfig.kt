package infrastructure.network

/**
 * Configuration for the cache server.
 *
 * @property port Port number to bind the server to (default: 8080)
 * @property bossThreads Number of threads for accepting connections (default: 1)
 * @property workerThreads Number of threads for processing requests (default: available processors)
 * @property shutdownTimeoutSeconds Timeout for graceful shutdown in seconds (default: 30)
 */
data class ServerConfig(
    val port: Int = 8080,
    val bossThreads: Int = 1,
    val workerThreads: Int = Runtime.getRuntime().availableProcessors(),
    val shutdownTimeoutSeconds: Long = 30,
) {
    init {
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        require(bossThreads > 0) { "Boss threads must be positive" }
        require(workerThreads > 0) { "Worker threads must be positive" }
        require(shutdownTimeoutSeconds > 0) { "Shutdown timeout must be positive" }
    }
}
