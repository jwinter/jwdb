# Change: Add Smart Client Library with Hybrid Approach

**Status: Proposed**

## Why
A distributed cache requires intelligent clients that understand cluster topology, perform client-side routing, and handle failures gracefully. However, teams adopting this cache may run in different environments - some with service mesh infrastructure (Istio, Linkerd), others without.

The **hybrid approach** provides a smart client library with built-in capabilities (topology awareness, consistent hashing, retries) while remaining compatible with service mesh enhancements. This gives teams:
- **Standalone mode**: Full functionality without additional infrastructure
- **Service mesh mode**: Enhanced observability, circuit breaking, and advanced traffic management
- **Flexibility**: Choose deployment model based on organizational maturity

This follows the pattern of mature distributed systems like Cassandra, which has smart clients that work standalone or integrate with service mesh infrastructure.

## What Changes
- Create client library (Kotlin/JVM initially, with multi-language support planned)
- Implement client-side topology awareness and consistent hashing
- Add connection pooling and request routing
- Implement retry logic with exponential backoff
- Add client-side load balancing across replicas
- Provide service mesh integration points
- Add client-side metrics and health checks

## Impact
- Affected specs: New `client-library` spec will be created
- Affected code: New client module/package separate from server
- Dependencies: Requires cluster membership and topology APIs from server
- Architecture: Shifts some intelligence to client for better performance
- Performance: Eliminates extra network hops from proxy/load-balancer
- Deployment: Clients can work standalone or with service mesh
- Testing: Requires client-server integration tests

## Design Decisions

### Client Responsibilities (Built-in)
1. **Topology Awareness**
   - Maintain cluster membership view via gossip subscription
   - Track node health (ALIVE, SUSPECTED, DOWN)
   - Refresh topology periodically or on failure

2. **Request Routing**
   - Calculate replica nodes using consistent hashing
   - Route requests directly to appropriate nodes
   - Select coordinator node (typically closest or random)

3. **Connection Management**
   - Connection pool per node (default: 2-5 connections)
   - Automatic reconnection on failure
   - Connection health checks (heartbeat)

4. **Basic Resilience**
   - Retry with exponential backoff (default: 3 attempts)
   - Failover to next replica on timeout
   - Circuit breaker per node (optional, simple implementation)

5. **Client-side Load Balancing**
   - Round-robin across healthy replicas
   - Least-connections balancing
   - Latency-aware routing (track node latency)

### Service Mesh Delegation (Optional)
When deployed with service mesh, delegate:
1. **Advanced Circuit Breaking**
   - Let service mesh handle circuit breaking
   - More sophisticated than client-side implementation
   - Cross-service coordination

2. **Observability**
   - Service mesh collects detailed metrics
   - Distributed tracing (spans for each hop)
   - Traffic analysis and visualization

3. **Service Discovery**
   - Use mesh service registry instead of gossip
   - Automatic DNS-based discovery
   - Health checks at infrastructure level

4. **Traffic Management**
   - Canary deployments
   - Traffic splitting for A/B testing
   - Rate limiting at mesh level

### Configuration Modes

#### Mode 1: Standalone (Full Client)
```kotlin
val client = CacheClient.builder()
    .seedNodes("node1:8080", "node2:8080", "node3:8080")
    .retryPolicy(ExponentialBackoff(maxAttempts = 3))
    .connectionPool(minConnections = 2, maxConnections = 5)
    .consistencyLevel(ConsistencyLevel.QUORUM)
    .build()
```

#### Mode 2: Service Mesh (Delegated)
```kotlin
val client = CacheClient.builder()
    .serviceUrl("http://cache-service") // Service mesh handles discovery
    .disableClientSideRouting() // Let mesh handle routing
    .disableCircuitBreaker() // Mesh provides circuit breaking
    .retryPolicy(SimpleRetry(maxAttempts = 2)) // Minimal retries
    .build()
```

#### Mode 3: Hybrid (Best of Both)
```kotlin
val client = CacheClient.builder()
    .seedNodes("node1:8080", "node2:8080", "node3:8080")
    .enableTopologyAwareness(true) // Client knows topology
    .enableClientSideRouting(true) // Client routes to replicas
    .disableCircuitBreaker() // Delegate to mesh
    .metricsExporter(PrometheusExporter()) // Export for mesh
    .build()
```

## Client API Design

### Core Client Interface
```kotlin
interface CacheClient<T> : AutoCloseable {
    /**
     * Get value from cache with default consistency level
     */
    suspend fun get(key: String): CacheResult<T>

    /**
     * Get value with explicit consistency level
     */
    suspend fun get(key: String, consistency: ConsistencyLevel): CacheResult<T>

    /**
     * Put value into cache
     */
    suspend fun put(key: String, value: T, ttl: Duration? = null): WriteResult

    /**
     * Compare-and-set operation
     */
    suspend fun compareAndSet(
        key: String,
        expectedVersion: Version?,
        newValue: T
    ): CasResult<T>

    /**
     * Delete value from cache
     */
    suspend fun delete(key: String): WriteResult

    /**
     * Get current cluster topology
     */
    fun getTopology(): ClusterTopology

    /**
     * Get client statistics and health
     */
    fun getStats(): ClientStats

    /**
     * Close client and release resources
     */
    override fun close()
}
```

### Builder Pattern for Configuration
```kotlin
class CacheClientBuilder<T> {
    fun seedNodes(vararg nodes: String): CacheClientBuilder<T>
    fun serviceUrl(url: String): CacheClientBuilder<T>
    fun consistencyLevel(level: ConsistencyLevel): CacheClientBuilder<T>
    fun retryPolicy(policy: RetryPolicy): CacheClientBuilder<T>
    fun connectionPool(min: Int, max: Int): CacheClientBuilder<T>
    fun enableClientSideRouting(enabled: Boolean): CacheClientBuilder<T>
    fun enableCircuitBreaker(enabled: Boolean): CacheClientBuilder<T>
    fun metricsExporter(exporter: MetricsExporter): CacheClientBuilder<T>
    fun serializer(serializer: Serializer<T>): CacheClientBuilder<T>
    fun build(): CacheClient<T>
}
```

### Topology and Routing
```kotlin
data class ClusterTopology(
    val nodes: Set<Node>,
    val replicationFactor: Int,
    val version: Long // Topology version for consistency
)

interface RoutingStrategy {
    /**
     * Determine which nodes to contact for a key
     */
    fun getReplicaNodes(key: String, topology: ClusterTopology): List<Node>

    /**
     * Select coordinator node for request
     */
    fun selectCoordinator(replicas: List<Node>): Node
}
```

### Connection Pooling
```kotlin
interface ConnectionPool {
    /**
     * Get connection to specified node
     */
    suspend fun getConnection(node: Node): Connection

    /**
     * Return connection to pool
     */
    fun returnConnection(connection: Connection)

    /**
     * Get pool statistics
     */
    fun getStats(): PoolStats
}

data class PoolStats(
    val activeConnections: Int,
    val idleConnections: Int,
    val pendingRequests: Int,
    val statsPerNode: Map<Node, NodePoolStats>
)
```

### Retry and Resilience
```kotlin
interface RetryPolicy {
    /**
     * Determine if operation should be retried
     */
    fun shouldRetry(attempt: Int, error: Throwable): Boolean

    /**
     * Calculate delay before next retry
     */
    fun getDelay(attempt: Int): Duration
}

class ExponentialBackoff(
    val maxAttempts: Int = 3,
    val initialDelay: Duration = Duration.ofMillis(100),
    val maxDelay: Duration = Duration.ofSeconds(2),
    val multiplier: Double = 2.0
) : RetryPolicy {
    override fun shouldRetry(attempt: Int, error: Throwable): Boolean =
        attempt < maxAttempts && error.isRetryable()

    override fun getDelay(attempt: Int): Duration {
        val delay = initialDelay.toMillis() * multiplier.pow(attempt - 1)
        return Duration.ofMillis(minOf(delay.toLong(), maxDelay.toMillis()))
    }
}

class CircuitBreaker(
    val failureThreshold: Int = 5,
    val timeout: Duration = Duration.ofSeconds(30),
    val halfOpenRequests: Int = 3
) {
    enum class State { CLOSED, OPEN, HALF_OPEN }

    fun recordSuccess()
    fun recordFailure()
    fun allowRequest(): Boolean
    fun getState(): State
}
```

## Feature Comparison Matrix

| Feature | Standalone Mode | Service Mesh Mode | Hybrid Mode |
|---------|----------------|-------------------|-------------|
| Topology awareness | Client | Mesh (service registry) | Client |
| Request routing | Client (consistent hash) | Mesh (L7 routing) | Client |
| Load balancing | Client (round-robin, least-conn) | Mesh (weighted, locality) | Client |
| Connection pooling | Client | Mesh (conn pooling) | Client |
| Retry logic | Client (3 retries + backoff) | Mesh (configurable) | Client (minimal) |
| Circuit breaking | Client (basic) | Mesh (advanced) | Mesh |
| Health checks | Client (TCP/heartbeat) | Mesh (L7 health) | Both |
| Metrics | Client (basic) | Mesh (comprehensive) | Client → Mesh |
| Distributed tracing | Limited | Mesh (automatic) | Mesh |
| TLS/mTLS | Manual | Mesh (automatic) | Mesh |
| Rate limiting | Application level | Mesh (global) | Mesh |
| Deployment complexity | Low | High (requires mesh) | Medium |

## Service Mesh Integration Points

### Istio/Envoy Integration
```yaml
# VirtualService for cache cluster
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: cache-service
spec:
  hosts:
  - cache-service
  http:
  - match:
    - uri:
        prefix: "/cache"
    route:
    - destination:
        host: cache-service
        subset: v1
      weight: 90
    - destination:
        host: cache-service
        subset: v2-canary
      weight: 10
    retries:
      attempts: 3
      perTryTimeout: 1s
    timeout: 5s

# DestinationRule for circuit breaking
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: cache-circuit-breaker
spec:
  host: cache-service
  trafficPolicy:
    connectionPool:
      tcp:
        maxConnections: 100
      http:
        http1MaxPendingRequests: 50
        http2MaxRequests: 100
    outlierDetection:
      consecutiveErrors: 5
      interval: 30s
      baseEjectionTime: 30s
      maxEjectionPercent: 50
```

### Client Metrics Export
```kotlin
// Export client metrics to Prometheus for mesh observability
interface MetricsExporter {
    fun recordRequest(node: Node, operation: String, latency: Duration, success: Boolean)
    fun recordTopologyUpdate(nodeCount: Int, version: Long)
    fun recordConnectionPoolStats(stats: PoolStats)
}

class PrometheusExporter : MetricsExporter {
    private val requestCounter = Counter.build()
        .name("cache_client_requests_total")
        .labelNames("node", "operation", "status")
        .register()

    private val latencyHistogram = Histogram.build()
        .name("cache_client_request_duration_seconds")
        .labelNames("node", "operation")
        .register()

    // Implementation...
}
```

## Multi-Language Support (Future)

### Language Priority
1. **Kotlin/JVM** - Primary (server language)
2. **Java** - High priority (JVM ecosystem)
3. **Go** - High priority (microservices, performance)
4. **Python** - Medium priority (data science, scripting)
5. **JavaScript/TypeScript** - Medium priority (Node.js backend)
6. **Rust** - Lower priority (performance-critical use cases)

### Shared Protocol
- All clients use same Protocol Buffers schema
- Consistent behavior across languages
- Shared test suite (protocol conformance tests)

## Testing Strategy

### Unit Tests
- Connection pool behavior
- Retry policy logic
- Circuit breaker state transitions
- Topology update handling

### Integration Tests
- Client-server operations (get, put, delete, CAS)
- Multi-node routing
- Failover scenarios
- Consistency level behavior

### E2E Tests
- Full cluster with client
- Network partitions
- Node failures during requests
- Topology changes during operations

### Performance Tests
- Throughput (requests/sec)
- Latency (p50, p95, p99)
- Connection pool efficiency
- Overhead vs. direct connection

## Out of Scope
- **Client-side caching** - Not implementing L1 cache (future consideration)
- **Offline mode** - No local persistence when disconnected
- **GraphQL API** - REST/gRPC only initially
- **Browser support** - Server-side clients only (no WASM/browser)
- **Reactive streams** - Callback-based async only (no reactive extensions initially)

## Dependencies
- ✅ Network protocol (Protocol Buffers) - completed
- ⏳ Cluster membership API - requires Phase 2A gossip protocol
- ⏳ Topology query endpoint - requires Phase 2A cluster topology
- ⏳ Health check endpoint - requires Phase 2A node health

## Success Metrics
- Client adds < 1ms latency overhead vs. direct connection
- Connection pool maintains 95%+ utilization
- Retry logic reduces error rate by 80%+ for transient failures
- Client correctly routes 99.99%+ of requests to appropriate replicas
- Client topology updates within 5 seconds of cluster changes
- Zero data loss during failover scenarios
- Works seamlessly in both standalone and service mesh deployments
