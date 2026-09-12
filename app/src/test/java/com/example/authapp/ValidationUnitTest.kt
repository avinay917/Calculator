package com.example.authapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationUnitTest {

    private fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && email.contains("@") && email.contains(".")
    }

    private fun doPasswordsMatch(pass: String, confirm: String): Boolean {
        return pass.isNotEmpty() && pass == confirm
    }

    @Test
    fun validEmail_returnsTrue() {
        assertTrue(isValidEmail("user@example.com"))
        assertTrue(isValidEmail("test.parent@domain.co.in"))
    }

    @Test
    fun invalidEmail_returnsFalse() {
        assertFalse(isValidEmail(""))
        assertFalse(isValidEmail("invalidemail"))
        assertFalse(isValidEmail("user@domain"))
    }

    @Test
    fun passwordMatch_returnsTrueWhenIdentical() {
        assertTrue(doPasswordsMatch("secret123", "secret123"))
    }

    @Test
    fun passwordMatch_returnsFalseWhenMismatched() {
        assertFalse(doPasswordsMatch("secret123", "secret456"))
    }

    @Test
    fun resetPasswordEmailValidation_validAndInvalidCases() {
        assertTrue(isValidEmail("parent.reset@example.com"))
        assertFalse(isValidEmail("   "))
        assertFalse(isValidEmail("not-an-email"))
    }
}
