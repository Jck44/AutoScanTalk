package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.app.Activity
import com.andreas_kratzer.ghosttalk.core.cloud.AuthManager
import javax.inject.Inject

class SignInUseCase @Inject constructor(
    private val authManager: AuthManager
) {
    suspend fun execute(activity: Activity): Boolean {
        return authManager.signIn(activity)
    }
}
