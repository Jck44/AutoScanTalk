package com.andreas_kratzer.ghosttalk.ui.pages.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.audio.AudioRecorder
import java.io.File

@Stable
class AudioRecordingController(
    private val context: Context,
    private val buttonConfigId: String,
    private val state: ButtonConfigDialogState,
    private val audioRecorder: AudioRecorder,
    private val micPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    private val onAutoSave: () -> Unit
) {
    var mediaPlayer by mutableStateOf<android.media.MediaPlayer?>(null)

    fun startVoiceRecording() {
        try {
            val dir = context.filesDir.resolve("audio_recordings")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val recordingFile = File(dir, "audio_${buttonConfigId}.ogg")
            audioRecorder.startRecording(recordingFile)
            state.isRecording = true
        } catch (e: Exception) {
            Toast.makeText(context, "Fehler bei der Aufnahme: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun stopVoiceRecording() {
        try {
            audioRecorder.stopRecording()
            state.isRecording = false
            state.audioFileName = "audio_${buttonConfigId}.ogg"
            onAutoSave()
            Toast.makeText(context, R.string.button_audio_saved, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Fehler beim Stoppen: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun playRecording(file: File) {
        if (state.isPlayingAudio) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            state.isPlayingAudio = false
            return
        }

        try {
            val player = android.media.MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    state.isPlayingAudio = false
                    it.release()
                    mediaPlayer = null
                }
                start()
            }
            mediaPlayer = player
            state.isPlayingAudio = true
        } catch (e: Exception) {
            android.util.Log.e("AudioRecordingController", "Error playing recording", e)
            Toast.makeText(context, "Fehler beim Abspielen: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleRecordingOrRequestPermission() {
        if (state.isRecording) {
            stopVoiceRecording()
        } else {
            val hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasMicPermission) {
                startVoiceRecording()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    fun release() {
        if (state.isRecording) {
            try {
                audioRecorder.stopRecording()
            } catch (e: Exception) {
                // ignore
            }
            state.isRecording = false
        }
        mediaPlayer?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                // ignore
            }
            it.release()
            mediaPlayer = null
            state.isPlayingAudio = false
        }
    }
}

@Composable
fun rememberAudioRecordingController(
    buttonConfigId: String,
    state: ButtonConfigDialogState,
    onAutoSave: () -> Unit
): AudioRecordingController {
    val context = LocalContext.current
    val audioRecorder = remember(context) { AudioRecorder(context) }
    
    // Define a holder for the start function so that the launcher's callback can access it
    var onPermissionGranted by remember { mutableStateOf<(() -> Unit)?>(null) }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted?.invoke()
        } else {
            Toast.makeText(context, R.string.error_microphone_permission_missing, Toast.LENGTH_LONG).show()
        }
    }

    val controller = remember(context, buttonConfigId, state, audioRecorder, micLauncher) {
        AudioRecordingController(
            context = context,
            buttonConfigId = buttonConfigId,
            state = state,
            audioRecorder = audioRecorder,
            micPermissionLauncher = micLauncher,
            onAutoSave = onAutoSave
        )
    }

    onPermissionGranted = { controller.startVoiceRecording() }

    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    DisposableEffect(state.isRecording) {
        if (state.isRecording) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            view.keepScreenOn = true
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            view.keepScreenOn = false
        }
    }

    DisposableEffect(controller) {
        onDispose {
            controller.release()
        }
    }

    return controller
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}
