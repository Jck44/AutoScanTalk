package com.andreas_kratzer.ghosttalk.core.call

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GhostTalkInCallService : InCallService() {

    @Inject
    lateinit var systemCallManager: SystemCallManager

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.i("GhostTalkInCallService", "onCallAdded: call=$call")
        systemCallManager.setInCallService(this)
        systemCallManager.onCallAdded(call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.i("GhostTalkInCallService", "onCallRemoved: call=$call")
        systemCallManager.onCallRemoved(call)
    }

    override fun onDestroy() {
        Log.i("GhostTalkInCallService", "onDestroy")
        systemCallManager.setInCallService(null)
        super.onDestroy()
    }
}
