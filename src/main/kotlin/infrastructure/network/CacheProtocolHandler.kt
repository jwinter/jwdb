package infrastructure.network

import com.example.cache.proto.DeleteResponse
import com.example.cache.proto.GetResponse
import com.example.cache.proto.PutResponse
import com.google.protobuf.ByteString
import domain.cache.Cache
import domain.cache.CacheKey
import domain.cache.CacheResult
import domain.cache.CacheValue
import domain.cache.WriteResult
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import java.time.Instant
import domain.replication.Version as DomainVersion

/**
 * Handles cache protocol requests and generates responses.
 * Converts between protobuf messages and domain cache operations.
 */
class CacheProtocolHandler<T>(
    private val cache: Cache<T>,
) : SimpleChannelInboundHandler<CacheMessage.Request>() {
    override fun channelRead0(
        ctx: ChannelHandlerContext,
        msg: CacheMessage.Request,
    ) {
        val response =
            when (msg) {
                is CacheMessage.Request.Get -> handleGet(msg)
                is CacheMessage.Request.Put -> handlePut(msg)
                is CacheMessage.Request.Delete -> handleDelete(msg)
            }

        ctx.writeAndFlush(response)
    }

    private fun handleGet(request: CacheMessage.Request.Get): CacheMessage.Response.Get {
        val key = CacheKey(request.request.key)

        @Suppress("UNCHECKED_CAST")
        val cacheForBytes = cache as Cache<ByteArray>
        val result = cacheForBytes.get(key)

        val response =
            when (result) {
                is CacheResult.Hit -> {
                    val entryBuilder =
                        com.example.cache.proto.CacheEntry.newBuilder()
                            .setData(ByteString.copyFrom(result.value.data))
                            .setCreatedAt(result.value.createdAt.toEpochMilli())
                            .setExpiresAt(result.value.expiresAt?.toEpochMilli() ?: 0)

                    // Add version if present
                    result.value.version?.let { domainVersion: DomainVersion ->
                        val protoVersion: com.example.cache.proto.Version =
                            com.example.cache.proto.Version.newBuilder()
                                .setTimestamp(domainVersion.timestamp)
                                .setNodeId(domainVersion.nodeId)
                                .build()
                        entryBuilder.setVersion(protoVersion)
                    }

                    val entry: com.example.cache.proto.CacheEntry = entryBuilder.build()

                    GetResponse.newBuilder()
                        .setStatus(GetResponse.Status.HIT)
                        .setEntry(entry)
                        .build()
                }
                is CacheResult.Miss -> {
                    GetResponse.newBuilder()
                        .setStatus(GetResponse.Status.MISS)
                        .build()
                }
                is CacheResult.Error -> {
                    GetResponse.newBuilder()
                        .setStatus(GetResponse.Status.ERROR)
                        .setErrorMessage(result.message)
                        .build()
                }
            }

        return CacheMessage.Response.Get(response)
    }

    private fun handlePut(request: CacheMessage.Request.Put): CacheMessage.Response.Put {
        val key = CacheKey(request.request.key)
        val entry = request.request.entry

        val createdAt = Instant.ofEpochMilli(entry.createdAt)
        val expiresAt =
            if (entry.expiresAt > 0) {
                Instant.ofEpochMilli(entry.expiresAt)
            } else {
                null
            }

        // Convert protobuf version to domain version if present
        val version: DomainVersion? =
            if (entry.hasVersion()) {
                DomainVersion(
                    timestamp = entry.version.timestamp,
                    nodeId = entry.version.nodeId,
                )
            } else {
                null
            }

        @Suppress("UNCHECKED_CAST")
        val cacheForBytes = cache as Cache<ByteArray>
        val value =
            CacheValue(
                data = entry.data.toByteArray(),
                createdAt = createdAt,
                expiresAt = expiresAt,
                version = version,
            )

        val result = cacheForBytes.put(key, value)

        val response =
            when (result) {
                is WriteResult.Success -> {
                    PutResponse.newBuilder()
                        .setStatus(PutResponse.Status.SUCCESS)
                        .build()
                }
                is WriteResult.Failure -> {
                    PutResponse.newBuilder()
                        .setStatus(PutResponse.Status.ERROR)
                        .setErrorMessage(result.message)
                        .build()
                }
            }

        return CacheMessage.Response.Put(response)
    }

    private fun handleDelete(request: CacheMessage.Request.Delete): CacheMessage.Response.Delete {
        val key = CacheKey(request.request.key)
        val result = cache.delete(key)

        val response =
            when (result) {
                is WriteResult.Success -> {
                    DeleteResponse.newBuilder()
                        .setStatus(DeleteResponse.Status.SUCCESS)
                        .build()
                }
                is WriteResult.Failure -> {
                    DeleteResponse.newBuilder()
                        .setStatus(DeleteResponse.Status.ERROR)
                        .setErrorMessage(result.message)
                        .build()
                }
            }

        return CacheMessage.Response.Delete(response)
    }

    override fun exceptionCaught(
        ctx: ChannelHandlerContext,
        cause: Throwable,
    ) {
        System.err.println("Error handling request: ${cause.message}")
        cause.printStackTrace()
        ctx.close()
    }
}
