package domain.cache

/**
 * Represents the result of a cache operation.
 * Using a sealed class for type-safe result handling.
 */
sealed class CacheResult<T> {
    data class Hit<T>(val value: CacheValue<T>) : CacheResult<T>()

    class Miss<T> : CacheResult<T>()

    data class Error<T>(val message: String, val cause: Throwable? = null) : CacheResult<T>()

    fun getOrNull(): CacheValue<T>? =
        when (this) {
            is Hit -> value
            is Miss -> null
            is Error -> null
        }

    fun isHit(): Boolean = this is Hit

    fun isMiss(): Boolean = this is Miss

    fun isError(): Boolean = this is Error
}

/**
 * Result of cache write operations.
 */
sealed class WriteResult {
    data object Success : WriteResult()

    data class Failure(val message: String, val cause: Throwable? = null) : WriteResult()

    fun isSuccess(): Boolean = this is Success
}
