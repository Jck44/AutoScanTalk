package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.app.Activity
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import javax.inject.Inject

class SignInUseCase @Inject constructor(
    private val googleAuthManager: GoogleAuthManager
) {
    suspend fun execute(activity: Activity): Boolean {
        return googleAuthManager.signIn(activity)
    }
}
