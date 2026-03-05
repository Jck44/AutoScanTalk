package com.andreas_kratzer.ghosttalk.domain.genai

import android.content.Intent
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import javax.inject.Inject

class HandleGenAiExceptionUseCase @Inject constructor() {
    sealed class Effect {
        data class EmitAuthIntent(val intent: Intent) : Effect()
    }

    fun execute(e: Exception): Effect? {
        var cause: Throwable? = e
        while (cause != null) {
            when (cause) {
                is UserRecoverableAuthIOException -> {
                    val intent = cause.intent
                    if (intent != null) return Effect.EmitAuthIntent(intent)
                }
                is UserRecoverableAuthException -> {
                    val intent = cause.intent
                    if (intent != null) return Effect.EmitAuthIntent(intent)
                }
            }
            cause = cause.cause
        }
        return null
    }
}
