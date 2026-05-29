package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.MediaProvider
import com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayMediaActionHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val actionLogger: ActionLogger
) : ActionHandler {

    override fun canHandle(action: ButtonAction): Boolean = action is PlayMediaButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val mediaAction = action as PlayMediaButtonAction
        val provider = mediaAction.provider
        val contentUri = mediaAction.contentUri
        
        Log.d("PlayMediaActionHandler", "Handling media play for $provider, URI: $contentUri")
        
        val intent = when (provider) {
            MediaProvider.SPOTIFY -> {
                val baseUri = if (contentUri == "liked_songs") "spotify:collection:tracks" else contentUri
                val uriString = if (baseUri.startsWith("spotify:")) baseUri else "spotify:playlist:$baseUri"
                val playUri = when {
                    uriString.endsWith(":play") -> uriString
                    uriString == "spotify:collection:tracks" -> uriString // Liked Songs: no :play suffix
                    else -> "$uriString:play"
                }
                Intent(Intent.ACTION_VIEW, Uri.parse(playUri)).apply {
                    `package` = "com.spotify.music"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            MediaProvider.YOUTUBE -> {
                val ytUri = when {
                    // Full playlist URL – pass through directly
                    contentUri.contains("list=") -> contentUri
                    // Full video URL with v= parameter
                    contentUri.contains("v=") -> {
                        val videoId = contentUri.substringAfter("v=").substringBefore("&")
                        "vnd.youtube:$videoId"
                    }
                    // Short share link
                    contentUri.contains("youtu.be/") -> {
                        val videoId = contentUri.substringAfter("youtu.be/").substringBefore("?")
                        "vnd.youtube:$videoId"
                    }
                    // Raw video ID (11 chars)
                    contentUri.length == 11 && !contentUri.startsWith("http") -> "vnd.youtube:$contentUri"
                    // Anything else (full URL etc.) – pass through
                    else -> contentUri
                }
                Intent(Intent.ACTION_VIEW, Uri.parse(ytUri)).apply {
                    `package` = "com.google.android.youtube"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            MediaProvider.YOUTUBE_MUSIC -> {
                Intent(Intent.ACTION_VIEW, Uri.parse(contentUri)).apply {
                    `package` = "com.google.android.apps.youtube.music"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            MediaProvider.AUDIBLE -> {
                val audibleUri = if (contentUri.startsWith("audible:") || contentUri.startsWith("http")) {
                    contentUri
                } else {
                    "audible://play?asin=$contentUri"
                }
                Intent(Intent.ACTION_VIEW, Uri.parse(audibleUri)).apply {
                    `package` = "com.audible.application"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
        
        try {
            context.startActivity(intent)
            actionLogger.log("Medien gestartet (${mediaAction.contentName.ifBlank { provider.name }})", action, buttonConfig.label)
        } catch (e: Exception) {
            Log.e("PlayMediaActionHandler", "Failed to launch media app for $provider", e)
            actionLogger.log("Fehler beim Starten von ${provider.name}", action, buttonConfig.label)
            
            // Fallback: Just launch the app package directly
            try {
                val fallbackPackage = when (provider) {
                    MediaProvider.SPOTIFY -> "com.spotify.music"
                    MediaProvider.YOUTUBE -> "com.google.android.youtube"
                    MediaProvider.YOUTUBE_MUSIC -> "com.google.android.apps.youtube.music"
                    MediaProvider.AUDIBLE -> "com.audible.application"
                }
                val launchIntent = context.packageManager.getLaunchIntentForPackage(fallbackPackage)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                }
            } catch (_: Exception) {}
        }
        
        // Return to GoSTalk after delay if configured
        if (mediaAction.returnToAppDelayMs > 0) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val returnIntent = Intent().apply {
                        setClassName(context.packageName, "com.andreas_kratzer.ghosttalk.MainActivity")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    }
                    context.startActivity(returnIntent)
                    Log.d("PlayMediaActionHandler", "Successfully returned to GoSTalk")
                } catch (e: Exception) {
                    Log.e("PlayMediaActionHandler", "Failed to return to GoSTalk", e)
                }
            }, mediaAction.returnToAppDelayMs)
        }
        
        onFinish(executionId)
    }
}
