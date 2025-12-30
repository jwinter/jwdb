package infrastructure.network

import com.example.cache.proto.DeleteRequest
import com.example.cache.proto.DeleteResponse
import com.example.cache.proto.GetRequest
import com.example.cache.proto.GetResponse
import com.example.cache.proto.PutRequest
import com.example.cache.proto.PutResponse

/**
 * Sealed class representing all possible cache protocol messages.
 * This allows type-safe handling of requests and responses.
 */
sealed class CacheMessage {
    /**
     * Request messages sent from client to server.
     */
    sealed class Request : CacheMessage() {
        data class Get(val request: GetRequest) : Request()

        data class Put(val request: PutRequest) : Request()

        data class Delete(val request: DeleteRequest) : Request()
    }

    /**
     * Response messages sent from server to client.
     */
    sealed class Response : CacheMessage() {
        data class Get(val response: GetResponse) : Response()

        data class Put(val response: PutResponse) : Response()

        data class Delete(val response: DeleteResponse) : Response()
    }
}
