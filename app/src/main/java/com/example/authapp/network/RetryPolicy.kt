package com.example.authapp.network

import kotlin.math.pow

/**
 * Retry policy with exponential backoff for failed operations.
 */
data class RetryConfig(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 30000,
    val backoffMultiplier: Double = 2.0
)

class RetryPolicy(private val config: RetryConfig = RetryConfig()) {
    
    suspend fun <T> executeWithRetry(
        operation: suspend () -> Result<T>,
        onRetry: ((attempt: Int, exception: Exception) -> Unit)? = null
    ): Result<T> {
        var lastException: Exception? = null
        
        repeat(config.maxRetries) { attempt ->
            try {
                val result = operation()
                if (result.isSuccess) {
                    return result
                } else {
                    val throwable = result.exceptionOrNull()
                    val exception = (throwable as? Exception) ?: Exception(throwable?.message ?: "Unknown error")
                    lastException = exception
                    onRetry?.invoke(attempt + 1, exception)
                    
                    if (attempt < config.maxRetries - 1) {
                        val delayMs = calculateBackoff(attempt)
                        kotlinx.coroutines.delay(delayMs)
                    }
                }
            } catch (e: Exception) {
                lastException = e
                onRetry?.invoke(attempt + 1, e)
                
                if (attempt < config.maxRetries - 1) {
                    val delayMs = calculateBackoff(attempt)
                    kotlinx.coroutines.delay(delayMs)
                }
            }
        }
        
        return Result.failure(lastException ?: Exception("Failed after ${config.maxRetries} retries"))
    }

    private fun calculateBackoff(attemptNumber: Int): Long {
        val exponentialDelay = config.initialDelayMs * (config.backoffMultiplier.pow(attemptNumber)).toLong()
        return exponentialDelay.coerceAtMost(config.maxDelayMs)
    }
}
