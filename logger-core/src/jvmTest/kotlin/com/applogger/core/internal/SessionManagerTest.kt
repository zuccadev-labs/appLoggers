package com.applogger.core.internal

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SessionManagerTest {

    @Test
    fun `sessionId is generated on creation`() {
        val manager = SessionManager()
        assertNotNull(manager.sessionId)
        assertTrue(manager.sessionId.isNotBlank())
    }

    @Test
    fun `sessionId remains stable across accesses`() {
        val manager = SessionManager()
        val firstAccess = manager.sessionId
        val secondAccess = manager.sessionId
        assertEquals(firstAccess, secondAccess)
    }

    @Test
    fun `different SessionManager instances get different IDs`() {
        val manager1 = SessionManager()
        val manager2 = SessionManager()
        assertNotEquals(manager1.sessionId, manager2.sessionId)
    }

    @Test
    fun `sessionId has UUID format`() {
        val manager = SessionManager()
        // UUID format: 8-4-4-4-12 hex chars
        val uuidRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        assertTrue(uuidRegex.matches(manager.sessionId))
    }
}
