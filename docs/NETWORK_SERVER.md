# Network Server

The distributed cache includes a high-performance network server built with Netty that exposes cache operations over TCP using Protocol Buffers.

## Overview

The network server provides:
- **High Performance**: Non-blocking I/O with Netty for handling many concurrent connections
- **Type-Safe Protocol**: Protocol Buffers for efficient serialization and multi-language support
- **Thread Safety**: Concurrent request processing with configurable thread pools
- **Graceful Shutdown**: Waits for in-flight requests to complete before shutting down

## Architecture

```
Client Connection
    ↓
[ProtobufCodec] - Encodes/decodes protobuf messages
    ↓
[CacheProtocolHandler] - Processes cache operations
    ↓
[InMemoryCache] - Domain layer cache implementation
```

## Server Configuration

The server is configured using the `ServerConfig` class:

```kotlin
val config = ServerConfig(
    port = 8080,                        // Port to bind to (default: 8080)
    bossThreads = 1,                    // Threads for accepting connections (default: 1)
    workerThreads = 4,                  // Threads for processing requests (default: CPU cores)
    shutdownTimeoutSeconds = 30         // Graceful shutdown timeout (default: 30s)
)
```

### Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `port` | Int | 8080 | TCP port number (1-65535) |
| `bossThreads` | Int | 1 | Number of threads for accepting new connections |
| `workerThreads` | Int | CPU cores | Number of threads for processing requests |
| `shutdownTimeoutSeconds` | Long | 30 | Maximum time to wait for graceful shutdown |

## Starting and Stopping the Server

### Basic Usage

```kotlin
// Create cache instance
val cache = InMemoryCache<ByteArray>()

// Create and start server
val server = CacheServer(cache)
server.start()

// ... server is running ...

// Stop server gracefully
server.stop()
cache.shutdown()
```

### Custom Configuration

```kotlin
val config = ServerConfig(
    port = 9090,
    workerThreads = 8
)

val cache = InMemoryCache<ByteArray>(
    maxSize = 10000,
    evictionPolicy = EvictionPolicy.LRU
)

val server = CacheServer(cache, config)
server.start()
```

## Network Protocol

The protocol uses a simple framing format:

```
[1 byte: message type][4 bytes: length][N bytes: protobuf message]
```

### Message Types

| Type | Value | Direction | Description |
|------|-------|-----------|-------------|
| GetRequest | 0 | Client → Server | Request to retrieve a cache entry |
| GetResponse | 1 | Server → Client | Response with cache entry or miss |
| PutRequest | 2 | Client → Server | Request to store a cache entry |
| PutResponse | 3 | Server → Client | Response indicating success or failure |
| DeleteRequest | 4 | Client → Server | Request to delete a cache entry |
| DeleteResponse | 5 | Server → Client | Response indicating success or failure |

### Protocol Buffers Schema

See [cache.proto](../src/main/proto/cache.proto) for the complete schema.

#### Example: Get Operation

Request:
```protobuf
GetRequest {
  key: "user:123"
}
```

Response (Hit):
```protobuf
GetResponse {
  status: HIT
  entry: {
    data: [serialized bytes]
    created_at: 1704067200000
    expires_at: 1704070800000
    version: 1
  }
}
```

Response (Miss):
```protobuf
GetResponse {
  status: MISS
}
```

#### Example: Put Operation

Request:
```protobuf
PutRequest {
  key: "user:123"
  entry: {
    data: [serialized bytes]
    created_at: 1704067200000
    expires_at: 0  // 0 = never expires
    version: 1
  }
}
```

Response:
```protobuf
PutResponse {
  status: SUCCESS
}
```

#### Example: Delete Operation

Request:
```protobuf
DeleteRequest {
  key: "user:123"
}
```

Response:
```protobuf
DeleteResponse {
  status: SUCCESS
}
```

## Client Implementation

To implement a client, you need to:

1. **Connect** to the server via TCP socket
2. **Frame messages** using the protocol format (type + length + protobuf bytes)
3. **Send requests** and **receive responses**

### Example Client (Kotlin)

```kotlin
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

class CacheClient(host: String, port: Int) {
    private val socket = Socket(host, port)
    private val output = DataOutputStream(socket.getOutputStream())
    private val input = DataInputStream(socket.getInputStream())

    fun get(key: String): GetResponse {
        // Build request
        val request = GetRequest.newBuilder()
            .setKey(key)
            .build()

        // Send request (type 0 = GetRequest)
        output.writeByte(0)
        val requestBytes = request.toByteArray()
        output.writeInt(requestBytes.size)
        output.write(requestBytes)
        output.flush()

        // Read response (type 1 = GetResponse)
        val responseType = input.readByte()
        require(responseType.toInt() == 1)

        val responseLength = input.readInt()
        val responseBytes = ByteArray(responseLength)
        input.readFully(responseBytes)

        return GetResponse.parseFrom(responseBytes)
    }

    fun close() {
        socket.close()
    }
}

// Usage
val client = CacheClient("localhost", 8080)
val response = client.get("my-key")
if (response.status == GetResponse.Status.HIT) {
    println("Found: ${response.entry.data}")
} else {
    println("Not found")
}
client.close()
```

## Error Handling

The server handles errors gracefully:

- **Connection Errors**: Logs error, closes connection, doesn't affect other connections
- **Malformed Messages**: Returns error response, keeps connection open for valid requests
- **Protocol Errors**: Closes connection and logs error details

### Error Response Example

```protobuf
GetResponse {
  status: ERROR
  error_message: "Invalid key format"
}
```

## Performance Considerations

### Thread Pool Sizing

- **Boss Threads**: Usually 1 is sufficient (only accepts connections)
- **Worker Threads**: Set to number of CPU cores for CPU-bound workloads, or higher for I/O-bound workloads
- Example: For a 4-core machine handling mostly cache operations, use 4-8 worker threads

### Connection Limits

The server can handle thousands of concurrent connections. Actual limit depends on:
- Available file descriptors (OS limit)
- Memory available for connection buffers
- Thread pool size and workload

### Message Size Limits

- Maximum message size: 10 MB (configurable in ProtobufCodec)
- Larger messages are rejected with an error

## Testing

Integration tests demonstrate all server capabilities:

```bash
# Run all integration tests
./gradlew testIntegration

# Run only server tests
./gradlew testIntegration --tests "CacheServerTest"
```

See [CacheServerTest.kt](../src/test/kotlin/infrastructure/network/CacheServerTest.kt) for examples.

## Monitoring

The server writes operational logs to stdout/stderr:

```
Cache server started on port 8080
Error handling request: Connection reset by peer
Cache server stopped
```

For production deployments, integrate with your logging framework and monitor:
- Connection count
- Request/response throughput
- Error rates
- Cache hit/miss rates (via cache.getStats())

## Next Steps

With the network server in place, you can:

1. **Build clients** in other languages using the protobuf schema
2. **Add replication** for distributed caching (Phase 2)
3. **Add persistence** for durability (Phase 3)
4. **Add authentication** and encryption for production use
