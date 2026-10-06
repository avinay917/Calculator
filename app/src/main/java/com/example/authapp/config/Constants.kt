package com.example.authapp.config

/**
 * Centralized constants for logging, tags, and app configuration
 */
object AppConfigConstants {
    object Logging {
        const val TAG_FATAL_CRASH = "FATAL_CRASH"
        const val TAG_FIREBASE = "FIREBASE_ERROR"
        const val TAG_APP = "AuthApp"
    }

    object Timeouts {
        const val FIREBASE_TIMEOUT_MS = 30000L
        const val LOCATION_UPDATE_INTERVAL_MS = 60000L
    }

    object Limits {
        const val MAX_LOG_LINES = 1000
        const val MAX_PAGINATION_SIZE = 100
    }
}
