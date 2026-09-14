package com.example.authapp.validation

import java.util.regex.Pattern

/**
 * Input validation utility for all user inputs.
 * Prevents injection attacks and invalid data.
 */
object InputValidator {
    
    private val EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    )
    
    private val PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$"
    )

    fun isValidEmail(email: String): Pair<Boolean, String?> {
        if (email.isBlank()) {
            return Pair(false, "Email cannot be empty")
        }
        if (email.length > 255) {
            return Pair(false, "Email is too long")
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return Pair(false, "Invalid email format")
        }
        return Pair(true, null)
    }

    fun isValidPassword(password: String): Pair<Boolean, String?> {
        if (password.length < 8) {
            return Pair(false, "Password must be at least 8 characters")
        }
        if (password.length > 128) {
            return Pair(false, "Password is too long")
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            return Pair(false, "Password must contain uppercase, lowercase, number, and special character")
        }
        return Pair(true, null)
    }

    fun isValidName(name: String): Pair<Boolean, String?> {
        if (name.isBlank()) {
            return Pair(false, "Name cannot be empty")
        }
        if (name.length < 2) {
            return Pair(false, "Name must be at least 2 characters")
        }
        if (name.length > 100) {
            return Pair(false, "Name is too long")
        }
        if (!name.matches(Regex("^[a-zA-Z\\s'-]+$"))) {
            return Pair(false, "Name contains invalid characters")
        }
        return Pair(true, null)
    }

    fun isValidPhoneNumber(phone: String): Pair<Boolean, String?> {
        if (phone.isBlank()) {
            return Pair(false, "Phone number cannot be empty")
        }
        val cleanedPhone = phone.replace(Regex("[^0-9]"), "")
        if (cleanedPhone.length < 10 || cleanedPhone.length > 15) {
            return Pair(false, "Phone number must be between 10-15 digits")
        }
        return Pair(true, null)
    }

    fun sanitizeString(input: String, maxLength: Int = 1000): String {
        return input
            .take(maxLength)
            .replace(Regex("[<>\"'`]"), "") // Remove potentially dangerous characters
            .trim()
    }

    fun isValidUrl(url: String): Pair<Boolean, String?> {
        if (url.isBlank()) {
            return Pair(false, "URL cannot be empty")
        }
        try {
            java.net.URL(url)
            if (!url.startsWith("https://")) {
                return Pair(false, "URL must use HTTPS")
            }
            return Pair(true, null)
        } catch (e: Exception) {
            return Pair(false, "Invalid URL format")
        }
    }
}
