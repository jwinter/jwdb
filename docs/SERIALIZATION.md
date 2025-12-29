# Serialization Strategy

## Overview

This distributed cache uses **Protocol Buffers (protobuf)** for serializing cache values across the network. This enables multi-language client support while maintaining high performance and type safety.

## Design Decisions

### Why Protocol Buffers?

1. **Multi-language Support**: Official protobuf implementations exist for Java, Kotlin, Python, Go, C++, C#, Ruby, JavaScript, PHP, and more
2. **Performance**: Binary format is compact and fast to serialize/deserialize
3. **Schema Evolution**: Built-in versioning supports backward/forward compatibility
4. **Type Safety**: Strongly-typed schemas prevent serialization errors across language boundaries
5. **Industry Standard**: Used by Google, Netflix, Square, and gRPC for distributed systems

### Alternative Options Considered

- **JSON**: Human-readable but larger message size, slower, no schema enforcement
- **MessagePack**: Good performance but less type safety and schema evolution support
- **Apache Avro**: Similar features but less language support than protobuf

## Implementation Plan

### Current State (Domain Layer)

The domain layer is **serialization-agnostic**:

- **Keys**: String-based (`CacheKey`) for simplicity and flexibility
- **Values**: Generic type `T` (`CacheValue<T>`) allows any data type

```kotlin
// Domain model - no serialization concerns
val cache = InMemoryCache<User>()
cache.put(CacheKey("user:123"), CacheValue(User(123, "Alice", "alice@example.com")))
```

### Future State (Infrastructure Layer)

The infrastructure layer will add serialization:

```kotlin
// Future: Serialization interface
interface CacheSerializer<T> {
    fun serialize(value: T): ByteArray
    fun deserialize(bytes: ByteArray): T
}

// Future: Protobuf implementation
class ProtobufSerializer<T : Message> : CacheSerializer<T> {
    override fun serialize(value: T): ByteArray = value.toByteArray()
    override fun deserialize(bytes: ByteArray): T = parseFrom(bytes)
}
```

### Protobuf Schema Example

```protobuf
syntax = "proto3";

package cache.protocol;

// Wire format for cache entries
message CacheEntry {
  bytes data = 1;              // Serialized user data (protobuf message)
  int64 created_at = 2;        // Unix timestamp (milliseconds)
  optional int64 expires_at = 3; // Optional expiration
  int64 version = 4;           // Version for conflict resolution
}

// Example: User data type
message User {
  int64 id = 1;
  string name = 2;
  string email = 3;
}

// Cache protocol messages
message GetRequest {
  string key = 1;
}

message GetResponse {
  oneof result {
    CacheEntry entry = 1;
    string error = 2;
  }
}

message PutRequest {
  string key = 1;
  CacheEntry entry = 2;
}

message PutResponse {
  bool success = 1;
  optional string error = 2;
}
```

## Data Flow

### Write Path
```
Client (any language)
  → Serialize user data to protobuf
  → Send PutRequest over Netty
  → Server deserializes protobuf
  → Store in InMemoryCache<T>
```

### Read Path
```
Client (any language)
  → Send GetRequest over Netty
  → Server retrieves from InMemoryCache<T>
  → Serialize to protobuf
  → Send GetResponse
  → Client deserializes protobuf
```

## Key Design Principles

1. **Domain Purity**: Domain layer knows nothing about protobuf or serialization
2. **Infrastructure Boundary**: Serialization happens only at network boundaries
3. **Type Safety**: Protobuf schemas enforce types across all clients
4. **Performance**: Binary format minimizes bandwidth and latency
5. **Evolution**: Schema versioning allows gradual rollouts and migrations

## Migration Path

1. ✅ **Phase 1** (Current): Implement domain layer with generic types
2. **Phase 2**: Add protobuf schemas and Gradle plugin
3. **Phase 3**: Implement serialization layer in infrastructure package
4. **Phase 4**: Integrate with Netty network protocol
5. **Phase 5**: Create client libraries for different languages

## Client Examples

### Kotlin Client
```kotlin
val user = User.newBuilder()
    .setId(123)
    .setName("Alice")
    .setEmail("alice@example.com")
    .build()

val entry = CacheEntry.newBuilder()
    .setData(user.toByteString())
    .setCreatedAt(System.currentTimeMillis())
    .setVersion(1)
    .build()

cacheClient.put("user:123", entry)
```

### Python Client
```python
user = User(
    id=123,
    name="Alice",
    email="alice@example.com"
)

entry = CacheEntry(
    data=user.SerializeToString(),
    created_at=int(time.time() * 1000),
    version=1
)

cache_client.put("user:123", entry)
```

### Go Client
```go
user := &User{
    Id:    123,
    Name:  "Alice",
    Email: "alice@example.com",
}

data, _ := proto.Marshal(user)
entry := &CacheEntry{
    Data:      data,
    CreatedAt: time.Now().UnixMilli(),
    Version:   1,
}

cacheClient.Put("user:123", entry)
```

## References

- [Protocol Buffers Documentation](https://protobuf.dev/)
- [Protobuf Language Guide](https://protobuf.dev/programming-guides/proto3/)
- [Kotlin Protobuf Plugin](https://github.com/google/protobuf-gradle-plugin)
