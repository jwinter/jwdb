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

## Implementation

### Domain Layer (Serialization-Agnostic)

The domain layer is **serialization-agnostic**:

- **Keys**: String-based (`CacheKey`) for simplicity and flexibility
- **Values**: Generic type `T` (`CacheValue<T>`) allows any data type

```kotlin
// Domain model - no serialization concerns
val cache = InMemoryCache<User>()
cache.put(CacheKey("user:123"), CacheValue(User(123, "Alice", "alice@example.com")))
```

### Infrastructure Layer (Serialization)

The infrastructure layer provides pluggable serialization:

```kotlin
// Serialization interface
package infrastructure.serialization

interface CacheSerializer<T> {
    fun serialize(value: T): ByteArray
    fun deserialize(bytes: ByteArray): T
}

// Protobuf implementation
class ProtobufSerializer<T : MessageLite>(
    private val parser: Parser<T>
) : CacheSerializer<T> {
    override fun serialize(value: T): ByteArray = value.toByteArray()
    override fun deserialize(bytes: ByteArray): T = parser.parseFrom(bytes)
}
```

### Using ProtobufSerializer

```kotlin
import infrastructure.serialization.ProtobufSerializer
import com.example.cache.proto.User
import com.example.cache.proto.user

// Create serializer
val userSerializer = ProtobufSerializer(User.parser())

// Create user message
val user = user {
    id = "user123"
    name = "Alice"
    email = "alice@example.com"
    age = 30
    active = true
    roles.add("admin")
}

// Serialize to bytes
val bytes: ByteArray = userSerializer.serialize(user)

// Deserialize from bytes
val deserializedUser: User = userSerializer.deserialize(bytes)
```

### Protobuf Schemas

The cache uses the following protobuf schemas (located in `src/main/proto/`):

#### cache.proto

```protobuf
syntax = "proto3";

package cache;

option java_package = "com.example.cache.proto";
option java_outer_classname = "CacheProto";
option java_multiple_files = true;

// CacheEntry represents a single cache entry with metadata
message CacheEntry {
  bytes data = 1;           // Serialized value data
  int64 created_at = 2;     // Timestamp when entry was created (milliseconds since epoch)
  int64 expires_at = 3;     // Timestamp when entry expires (0 = never expires)
  int64 version = 4;        // Version number for conflict resolution
}

// GetRequest represents a cache get operation
message GetRequest {
  string key = 1;           // Cache key to retrieve
}

// GetResponse represents the result of a get operation
message GetResponse {
  enum Status {
    HIT = 0;                // Key found in cache
    MISS = 1;               // Key not found or expired
    ERROR = 2;              // Error occurred
  }

  Status status = 1;        // Result status
  CacheEntry entry = 2;     // Cache entry (only present if status = HIT)
  string error_message = 3; // Error message (only present if status = ERROR)
}

// PutRequest represents a cache put operation
message PutRequest {
  string key = 1;           // Cache key
  CacheEntry entry = 2;     // Cache entry to store
}

// PutResponse represents the result of a put operation
message PutResponse {
  enum Status {
    SUCCESS = 0;            // Entry stored successfully
    ERROR = 1;              // Error occurred
  }

  Status status = 1;        // Result status
  string error_message = 2; // Error message (only present if status = ERROR)
}

// DeleteRequest represents a cache delete operation
message DeleteRequest {
  string key = 1;           // Cache key to delete
}

// DeleteResponse represents the result of a delete operation
message DeleteResponse {
  enum Status {
    SUCCESS = 0;            // Entry deleted successfully
    ERROR = 1;              // Error occurred
  }

  Status status = 1;        // Result status
  string error_message = 2; // Error message (only present if status = ERROR)
}
```

#### user.proto (Example for testing)

```protobuf
syntax = "proto3";

package user;

option java_package = "com.example.cache.proto";
option java_outer_classname = "UserProto";
option java_multiple_files = true;

// User message for testing protobuf serialization
message User {
  string id = 1;
  string name = 2;
  string email = 3;
  int32 age = 4;
  bool active = 5;
  repeated string roles = 6;
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

## How to Add New Message Types

### Step 1: Define the Protobuf Schema

Create a new `.proto` file in `src/main/proto/` or add to an existing one:

```protobuf
// src/main/proto/product.proto
syntax = "proto3";

package product;

option java_package = "com.example.cache.proto";
option java_outer_classname = "ProductProto";
option java_multiple_files = true;

message Product {
  string id = 1;
  string name = 2;
  double price = 3;
  int32 quantity = 4;
  repeated string categories = 5;
}
```

### Step 2: Build to Generate Kotlin Code

Run Gradle to generate the Kotlin code:

```bash
./gradlew generateProto
```

This generates Kotlin classes in `build/generated/source/proto/main/kotlin/`.

### Step 3: Create a Serializer

```kotlin
import infrastructure.serialization.ProtobufSerializer
import com.example.cache.proto.Product

val productSerializer = ProtobufSerializer(Product.parser())
```

### Step 4: Use the Serializer

```kotlin
import com.example.cache.proto.product

// Create a product
val product = product {
    id = "prod123"
    name = "Laptop"
    price = 999.99
    quantity = 10
    categories.add("Electronics")
    categories.add("Computers")
}

// Serialize
val bytes = productSerializer.serialize(product)

// Deserialize
val deserialized = productSerializer.deserialize(bytes)
```

### Best Practices

1. **Field Numbers**: Never reuse field numbers - they're permanent identifiers
2. **Optional vs Required**: Use optional fields for better schema evolution
3. **Naming**: Use snake_case for field names (protobuf convention)
4. **Packages**: Use separate packages for different domains
5. **Documentation**: Add comments to all messages and fields

## Migration Path

1. ✅ **Phase 1**: Implement domain layer with generic types
2. ✅ **Phase 2**: Add protobuf schemas and Gradle plugin
3. ✅ **Phase 3**: Implement serialization layer in infrastructure package
4. **Phase 4** (Next): Integrate with Netty network protocol
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
