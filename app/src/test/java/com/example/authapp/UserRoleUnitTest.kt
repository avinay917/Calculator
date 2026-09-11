package com.example.authapp

import com.example.authapp.data.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRoleUnitTest {

    @Test
    fun newUser_defaultRoleIsChild() {
        val user = User(
            uid = "test_uid_123",
            name = "Test Child User",
            email = "child@example.com"
        )
        assertEquals("child", user.role)
    }

    @Test
    fun userRole_canBeUpdatedToParent() {
        val user = User(
            uid = "test_uid_456",
            name = "Test Parent User",
            email = "parent@example.com",
            role = "parent"
        )
        assertEquals("parent", user.role)
    }

    @Test
    fun userOnlineStatus_defaultState() {
        val user = User()
        assertEquals(false, user.isOnline)
        assertTrue(user.lastSeen > 0)
    }
}
