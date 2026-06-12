package com.andreas_kratzer.ghosttalk.core.cloud

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncWorkRequesterTest {

    @Test
    fun testBuildConstraints_withSaf_returnsNoNetworkConstraint() {
        val constraints = SyncWorkRequester.buildConstraints("LOCAL_FOLDER_SAF")
        assertEquals(NetworkType.NOT_REQUIRED, constraints.requiredNetworkType)
    }

    @Test
    fun testBuildConstraints_withCloud_requiresConnectedNetwork() {
        val constraints = SyncWorkRequester.buildConstraints("GOOGLE_DRIVE")
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
    }
}
