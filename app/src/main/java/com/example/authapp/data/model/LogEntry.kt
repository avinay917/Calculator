package com.example.authapp.data.model

/**
 * Common interface for child activity log entries.
 * Preserves 100% backward compatibility with all fields.
 */
interface LogEntry {
    val timestamp: Long
}
