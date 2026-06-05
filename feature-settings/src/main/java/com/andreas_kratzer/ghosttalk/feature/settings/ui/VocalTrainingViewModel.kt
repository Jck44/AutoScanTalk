package com.andreas_kratzer.ghosttalk.feature.settings.ui

import android.annotation.SuppressLint
import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.ai.domain.AudioEmbedderWrapper
import com.andreas_kratzer.ghosttalk.core.ai.domain.VocalPatternMatcher
import com.andreas_kratzer.ghosttalk.core.data.VocalProfileRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.VocalProfile
import com.andreas_kratzer.ghosttalk.core.model.Page
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class VocalTrainingViewModel @Inject constructor(
    private val application: Application,
    private val vocalProfileRepository: VocalProfileRepository,
    private val pageRepository: PageRepository,
    private val audioEmbedderWrapper: AudioEmbedderWrapper,
    private val vocalPatternMatcher: VocalPatternMatcher
) : AndroidViewModel(application) {

    private val TAG = "VocalTrainingViewModel"

    val allProfiles: StateFlow<List<VocalProfile>> = vocalProfileRepository.getAllProfilesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _allPages = MutableStateFlow<List<Page>>(emptyList())
    val allPages: StateFlow<List<Page>> = _allPages.asStateFlow()

    // Recording States
    private val _recordingSlotStatus = MutableStateFlow(List(5) { SlotStatus.EMPTY })
    val recordingSlotStatus: StateFlow<List<SlotStatus>> = _recordingSlotStatus.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _activeRecordingSlot = MutableStateFlow<Int?>(null)
    val activeRecordingSlot: StateFlow<Int?> = _activeRecordingSlot.asStateFlow()

    private val recordedEmbeddings = mutableListOf<List<Float>>()

    // Profile Configuration States
    var profileName = MutableStateFlow("")
    var actionType = MutableStateFlow("taster_click") // "taster_click", "speak_text", "navigate_page"
    var spokenText = MutableStateFlow("")
    var selectedPageId = MutableStateFlow("")

    enum class SlotStatus {
        EMPTY,
        RECORDING,
        PROCESSING,
        DONE,
        ERROR
    }

    init {
        loadPages()
    }

    private fun loadPages() {
        viewModelScope.launch {
            try {
                val pages = pageRepository.getAllPages()
                _allPages.value = pages
                if (pages.isNotEmpty()) {
                    selectedPageId.value = pages.first().id
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load pages", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecordingSlot(slotIndex: Int) {
        if (_isRecording.value) return
        _isRecording.value = true
        _activeRecordingSlot.value = slotIndex

        val updatedStatus = _recordingSlotStatus.value.toMutableList()
        updatedStatus[slotIndex] = SlotStatus.RECORDING
        _recordingSlotStatus.value = updatedStatus

        viewModelScope.launch(Dispatchers.IO) {
            val format = audioEmbedderWrapper.getRequiredAudioFormat()
            val sampleRate = format.sampleRate
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
            val windowSamples = 15600
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
            val bufferSize = maxOf(minBufferSize, windowSamples * 2)

            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioEncoding,
                    bufferSize
                )

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("AudioRecord not initialized")
                }

                recorder.startRecording()
                Log.d(TAG, "Recording started for slot $slotIndex")

                val shortBuffer = ShortArray(windowSamples)
                var totalRead = 0
                while (totalRead < windowSamples && _isRecording.value) {
                    val read = recorder.read(shortBuffer, totalRead, windowSamples - totalRead)
                    if (read > 0) {
                        totalRead += read
                    } else if (read < 0) {
                        throw IllegalStateException("AudioRecord read error: $read")
                    }
                }

                withContext(Dispatchers.Main) {
                    val statusProcessing = _recordingSlotStatus.value.toMutableList()
                    statusProcessing[slotIndex] = SlotStatus.PROCESSING
                    _recordingSlotStatus.value = statusProcessing
                }

                val floatAudioData = FloatArray(windowSamples)
                for (i in 0 until windowSamples) {
                    floatAudioData[i] = shortBuffer[i] / 32768.0f
                }

                val embedding = audioEmbedderWrapper.getEmbedding(floatAudioData)
                withContext(Dispatchers.Main) {
                    if (embedding != null) {
                        if (recordedEmbeddings.size > slotIndex) {
                            recordedEmbeddings[slotIndex] = embedding
                        } else {
                            recordedEmbeddings.add(embedding)
                        }
                        val statusDone = _recordingSlotStatus.value.toMutableList()
                        statusDone[slotIndex] = SlotStatus.DONE
                        _recordingSlotStatus.value = statusDone
                        Log.d(TAG, "Successfully recorded embedding for slot $slotIndex")
                    } else {
                        val statusError = _recordingSlotStatus.value.toMutableList()
                        statusError[slotIndex] = SlotStatus.ERROR
                        _recordingSlotStatus.value = statusError
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Recording failed for slot $slotIndex", e)
                withContext(Dispatchers.Main) {
                    val statusError = _recordingSlotStatus.value.toMutableList()
                    statusError[slotIndex] = SlotStatus.ERROR
                    _recordingSlotStatus.value = statusError
                }
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to release recorder", e)
                }
                _isRecording.value = false
                _activeRecordingSlot.value = null
            }
        }
    }

    fun saveProfile(): Boolean {
        val name = profileName.value.trim()
        if (name.isEmpty() || recordedEmbeddings.size < 5) return false

        val vectorSize = recordedEmbeddings.first().size
        val averageVector = FloatArray(vectorSize)
        for (embedding in recordedEmbeddings) {
            for (i in 0 until vectorSize) {
                averageVector[i] += embedding[i]
            }
        }
        for (i in 0 until vectorSize) {
            averageVector[i] /= recordedEmbeddings.size
        }

        val action = when (actionType.value) {
            "speak_text" -> SpeakTextButtonAction()
            "navigate_page" -> NavigateToPageButtonAction(pageId = selectedPageId.value)
            else -> null
        }

        val textToSpeak = if (actionType.value == "speak_text") spokenText.value.trim().takeIf { it.isNotEmpty() } else null

        val newProfile = VocalProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            positiveTemplates = recordedEmbeddings.toList(),
            negativeTemplates = emptyList(),
            buttonAction = action,
            spokenText = textToSpeak,
            isActive = true,
            referenceEmbedding = averageVector.toList()
        )

        viewModelScope.launch {
            vocalProfileRepository.saveProfile(newProfile)
        }

        resetTrainingState()
        return true
    }

    fun deleteProfile(profile: VocalProfile) {
        viewModelScope.launch {
            vocalProfileRepository.deleteProfile(profile)
        }
    }

    fun resetTrainingState() {
        recordedEmbeddings.clear()
        _recordingSlotStatus.value = List(5) { SlotStatus.EMPTY }
        profileName.value = ""
        actionType.value = "taster_click"
        spokenText.value = ""
    }

    fun resetModel() {
        viewModelScope.launch {
            vocalProfileRepository.clearAllProfiles()
        }
        resetTrainingState()
    }

    // --- Live Test & Calibration V2 ---
    sealed interface TestScreenState {
        object Idle : TestScreenState
        object Listening : TestScreenState
        data class Evaluated(
            val isMatch: Boolean,
            val positiveConfidence: Float,
            val negativeConfidence: Float,
            val matchedProfileName: String?,
            val rawFeatures: List<Float>
        ) : TestScreenState
    }

    private val _testState = MutableStateFlow<TestScreenState>(TestScreenState.Idle)
    val testState: StateFlow<TestScreenState> = _testState.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startLiveTest() {
        if (_isRecording.value) return
        _isRecording.value = true
        _testState.value = TestScreenState.Listening

        viewModelScope.launch(Dispatchers.IO) {
            val format = audioEmbedderWrapper.getRequiredAudioFormat()
            val sampleRate = format.sampleRate
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
            val windowSamples = 15600
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
            val bufferSize = maxOf(minBufferSize, windowSamples * 2)

            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioEncoding,
                    bufferSize
                )

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("AudioRecord not initialized")
                }

                recorder.startRecording()
                Log.d(TAG, "Live test recording started")

                val shortBuffer = ShortArray(windowSamples)
                var totalRead = 0
                while (totalRead < windowSamples && _isRecording.value) {
                    val read = recorder.read(shortBuffer, totalRead, windowSamples - totalRead)
                    if (read > 0) {
                        totalRead += read
                    } else if (read < 0) {
                        throw IllegalStateException("AudioRecord read error: $read")
                    }
                }

                val floatAudioData = FloatArray(windowSamples)
                for (i in 0 until windowSamples) {
                    floatAudioData[i] = shortBuffer[i] / 32768.0f
                }

                val embedding = audioEmbedderWrapper.getEmbedding(floatAudioData)
                withContext(Dispatchers.Main) {
                    if (embedding != null) {
                        // Evaluate against all active profiles
                        val profiles = allProfiles.value
                        var bestMatchResult: VocalPatternMatcher.MatchResult? = null
                        var bestMatchProfile: VocalProfile? = null

                        for (profile in profiles) {
                            val result = vocalPatternMatcher.evaluate(
                                inputVector = embedding,
                                positives = profile.positiveTemplates,
                                negatives = profile.negativeTemplates
                            )
                            if (bestMatchResult == null || result.positiveConfidence > bestMatchResult.positiveConfidence) {
                                bestMatchResult = result
                                bestMatchProfile = profile
                            }
                        }

                        if (bestMatchResult != null) {
                            _testState.value = TestScreenState.Evaluated(
                                isMatch = bestMatchResult.isMatch,
                                positiveConfidence = bestMatchResult.positiveConfidence,
                                negativeConfidence = bestMatchResult.negativeConfidence,
                                matchedProfileName = if (bestMatchResult.isMatch) bestMatchProfile?.name else null,
                                rawFeatures = embedding
                            )
                        } else {
                            _testState.value = TestScreenState.Evaluated(
                                isMatch = false,
                                positiveConfidence = 0f,
                                negativeConfidence = 0f,
                                matchedProfileName = null,
                                rawFeatures = embedding
                            )
                        }
                        Log.d(TAG, "Live test successfully evaluated")
                    } else {
                        _testState.value = TestScreenState.Idle
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Live test failed", e)
                withContext(Dispatchers.Main) {
                    _testState.value = TestScreenState.Idle
                }
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to release recorder during live test", e)
                }
                _isRecording.value = false
            }
        }
    }

    fun addLastSampleAsFalsePositive(profile: VocalProfile, features: List<Float>) {
        viewModelScope.launch {
            val updatedNegatives = profile.negativeTemplates.toMutableList()
            updatedNegatives.add(features)
            val updatedProfile = profile.copy(negativeTemplates = updatedNegatives)
            vocalProfileRepository.updateProfile(updatedProfile)
            Log.d(TAG, "Added sample as false-positive for profile: ${profile.name}")
        }
    }
}
