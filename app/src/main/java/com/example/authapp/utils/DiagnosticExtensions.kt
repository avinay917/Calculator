package com.example.authapp.utils

import com.example.authapp.analytics.AppLogger

/**
 * Executes block safely with logging to AppLogger upon exception.
 */
inline fun <T> runCatchingDiagnostic(tag: String, message: String = "Execution failed", block: () -> T): Result<T> {
    return runCatching {
        block()
    }.onFailure { error ->
        AppLogger.e(tag, "$message: ${error.localizedMessage}", error)
    }
}
