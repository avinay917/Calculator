package com.example.authapp.network

import kotlin.math.max

/**
 * Rate limiter to prevent API abuse.
 * Uses token bucket algorithm.
 */
class RateLimiter(
    private val maxRequests: Int = 100,
    private val windowSizeMs: Long = 60000 // 1 minute
) {
    private var tokens = maxRequests.toDouble()
    private var lastRefillTime = System.currentTimeMillis()

    @Synchronized
    fun allowRequest(): Boolean {
        refillTokens()
        return if (tokens >= 1) {
            tokens -= 1
            true
        } else {
            false
        }
    }

    @Synchronized
    fun getAvailableTokens(): Int = tokens.toInt()

    @Synchronized
    fun getTimeUntilNextRequest(): Long {
        refillTokens()
        return if (tokens >= 1) {
            0
        } else {
            val timePerToken = windowSizeMs.toDouble() / maxRequests
            (timePerToken * (1 - tokens)).toLong()
        }
    }

    private fun refillTokens() {
        val now = System.currentTimeMillis()
        val timePassed = now - lastRefillTime
        
        if (timePassed > 0) {
            val tokensToAdd = (timePassed.toDouble() / windowSizeMs) * maxRequests
            tokens = (tokens + tokensToAdd).coerceAtMost(maxRequests.toDouble())
            lastRefillTime = now
        }
    }
}
