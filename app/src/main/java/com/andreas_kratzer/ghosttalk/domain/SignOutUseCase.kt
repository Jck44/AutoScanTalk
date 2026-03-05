package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val googleAuthManager: GoogleAuthManager
) {
    suspend fun execute() {
        googleAuthManager.signOut()
    }
}