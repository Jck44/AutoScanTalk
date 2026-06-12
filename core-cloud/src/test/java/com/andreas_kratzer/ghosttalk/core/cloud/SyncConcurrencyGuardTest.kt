package com.andreas_kratzer.ghosttalk.core.cloud

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncConcurrencyGuardTest {

    @Test
    fun testRunExclusiveLocksAndSkipsConcurrentCalls() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val firstCanFinish = CompletableDeferred<Unit>()

        val firstDeferred = async {
            SyncConcurrencyGuard.runExclusive {
                firstStarted.complete(Unit)
                firstCanFinish.await()
                "first_result"
            }
        }

        // Wait until the first block has actually started running and acquired the lock
        firstStarted.await()

        // Call runExclusive concurrently; it should immediately return null because the lock is held
        val secondResult = SyncConcurrencyGuard.runExclusive {
            "second_result"
        }
        assertNull(secondResult)

        // Let the first block finish
        firstCanFinish.complete(Unit)
        val firstResult = firstDeferred.await()
        assertEquals("first_result", firstResult)

        // Now that the lock is released, subsequent calls should succeed
        val thirdResult = SyncConcurrencyGuard.runExclusive {
            "third_result"
        }
        assertEquals("third_result", thirdResult)
    }
}
