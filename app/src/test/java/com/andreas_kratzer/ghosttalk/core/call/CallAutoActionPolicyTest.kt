package com.andreas_kratzer.ghosttalk.core.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallAutoActionPolicyTest {

    @Test
    fun testDetermineAutoAction() {
        assertEquals(CallAutoAction.ANSWER, CallAutoActionPolicy.determineAutoAction("ANSWER"))
        assertEquals(CallAutoAction.REJECT, CallAutoActionPolicy.determineAutoAction("REJECT"))
        assertEquals(CallAutoAction.NONE, CallAutoActionPolicy.determineAutoAction("NONE"))
        assertEquals(CallAutoAction.NONE, CallAutoActionPolicy.determineAutoAction("INVALID"))
        assertEquals(CallAutoAction.NONE, CallAutoActionPolicy.determineAutoAction(null))
    }

    @Test
    fun testShouldTriggerActiveScanLimit() {
        assertFalse(CallAutoActionPolicy.shouldTriggerActiveScanLimit(cyclesCompleted = 5, limit = 0))
        assertFalse(CallAutoActionPolicy.shouldTriggerActiveScanLimit(cyclesCompleted = 5, limit = -1))

        assertTrue(CallAutoActionPolicy.shouldTriggerActiveScanLimit(cyclesCompleted = 5, limit = 5))
        assertTrue(CallAutoActionPolicy.shouldTriggerActiveScanLimit(cyclesCompleted = 6, limit = 5))

        assertFalse(CallAutoActionPolicy.shouldTriggerActiveScanLimit(cyclesCompleted = 4, limit = 5))
    }
}
