package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.model.AccessEvent
import com.example.data.model.AccessState
import com.example.data.model.ResourceType
import com.example.data.model.RiskLevel
import com.example.data.repository.PrivacyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

data class LiveMicState(
    val isMicActive: Boolean = false,
    val activeApp: String = "No app using microphone",
    val activePackage: String = "",
    val activeSource: String = "Inactive",
    val durationSeconds: Int = 0,
    val isBackground: Boolean = false,
    val isHardwareMuted: Boolean = false,
    val lastActiveTimestamp: Long = 0L
)

class MicMonitoringManager(
    private val context: Context,
    private val repository: PrivacyRepository,
    private val scope: CoroutineScope
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val _micState = MutableStateFlow(LiveMicState())
    val micState: StateFlow<LiveMicState> = _micState.asStateFlow()

    private val _liveDecibels = MutableStateFlow(0f)
    val liveDecibels: StateFlow<Float> = _liveDecibels.asStateFlow()

    private val _isRecordingLabActive = MutableStateFlow(false)
    val isRecordingLabActive: StateFlow<Boolean> = _isRecordingLabActive.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var decibelJob: Job? = null
    private var activeSessionStartTime: Long = 0L
    private var sessionTimerJob: Job? = null

    private var recordingCallback: AudioManager.AudioRecordingCallback? = null

    init {
        createNotificationChannel()
        registerSystemRecordingCallback()
        updateHardwareMuteState()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Microphone Access Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when apps access your microphone in the background"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateHardwareMuteState() {
        val isMuted = runCatching { audioManager.isMicrophoneMute }.getOrDefault(false)
        _micState.value = _micState.value.copy(isHardwareMuted = isMuted)
    }

    private fun registerSystemRecordingCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val callback = object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>?) {
                    super.onRecordingConfigChanged(configs)
                    val activeConfigs = configs?.filterNotNull() ?: emptyList()
                    val isAnyActive = activeConfigs.isNotEmpty()

                    if (isAnyActive) {
                        val firstConfig = activeConfigs.first()
                        val sourceName = getAudioSourceName(firstConfig.clientAudioSource)

                        if (!_micState.value.isMicActive) {
                            // New recording session started!
                            activeSessionStartTime = System.currentTimeMillis()
                            _micState.value = LiveMicState(
                                isMicActive = true,
                                activeApp = "System Audio Client",
                                activePackage = "android.media",
                                activeSource = sourceName,
                                durationSeconds = 0,
                                isBackground = true,
                                isHardwareMuted = audioManager.isMicrophoneMute,
                                lastActiveTimestamp = activeSessionStartTime
                            )
                            startSessionTimer()
                        }
                    } else {
                        // Recording stopped
                        if (_micState.value.isMicActive) {
                            val duration = ((System.currentTimeMillis() - activeSessionStartTime) / 1000).toInt()
                                .coerceAtLeast(1)
                            sessionTimerJob?.cancel()
                            sessionTimerJob = null

                            val endedApp = _micState.value.activeApp
                            val endedPkg = _micState.value.activePackage
                            val isBg = _micState.value.isBackground

                            _micState.value = _micState.value.copy(
                                isMicActive = false,
                                durationSeconds = duration
                            )

                            // Log to Room Database
                            scope.launch {
                                repository.logEvent(
                                    AccessEvent(
                                        timestamp = System.currentTimeMillis(),
                                        appName = endedApp,
                                        packageName = endedPkg,
                                        resourceType = ResourceType.MICROPHONE,
                                        accessState = AccessState.STOPPED,
                                        durationSeconds = duration,
                                        isBackground = isBg,
                                        riskLevel = if (isBg) RiskLevel.WORTH_CHECKING else RiskLevel.NORMAL,
                                        notes = "System recording session completed ($duration seconds)."
                                    )
                                )
                            }
                        }
                    }
                }
            }
            recordingCallback = callback
            audioManager.registerAudioRecordingCallback(callback, Handler(Looper.getMainLooper()))
        }
    }

    private fun startSessionTimer() {
        sessionTimerJob?.cancel()
        sessionTimerJob = scope.launch {
            while (isActive && _micState.value.isMicActive) {
                delay(1000)
                val duration = ((System.currentTimeMillis() - activeSessionStartTime) / 1000).toInt()
                _micState.value = _micState.value.copy(durationSeconds = duration)
            }
        }
    }

    private fun getAudioSourceName(source: Int): String {
        return when (source) {
            MediaRecorder.AudioSource.MIC -> "Standard Microphone"
            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "Voice Call / VoIP"
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "Speech Recognition"
            MediaRecorder.AudioSource.CAMCORDER -> "Camera Video Recording"
            MediaRecorder.AudioSource.DEFAULT -> "Default Audio Stream"
            else -> "Audio Source ($source)"
        }
    }

    /**
     * Simulates an unexpected background microphone access event
     * so user can immediately test alerting and timeline response.
     */
    fun triggerSimulatedEvent(
        appName: String = "Suspicious Background App",
        packageName: String = "com.sample.analytics.voice",
        resourceType: ResourceType = ResourceType.MICROPHONE,
        isBackground: Boolean = true,
        durationSeconds: Int = 18,
        riskLevel: RiskLevel = RiskLevel.WORTH_CHECKING
    ) {
        val now = System.currentTimeMillis()
        activeSessionStartTime = now

        _micState.value = LiveMicState(
            isMicActive = true,
            activeApp = appName,
            activePackage = packageName,
            activeSource = "Background Listener",
            durationSeconds = durationSeconds,
            isBackground = isBackground,
            lastActiveTimestamp = now
        )

        sendAlertNotification(appName, resourceType, isBackground, durationSeconds)

        scope.launch {
            repository.logEvent(
                AccessEvent(
                    timestamp = now,
                    appName = appName,
                    packageName = packageName,
                    resourceType = resourceType,
                    accessState = AccessState.STOPPED,
                    durationSeconds = durationSeconds,
                    isBackground = isBackground,
                    riskLevel = riskLevel,
                    notes = if (isBackground) {
                        "Microphone was accessed while the app was running in the background. Device was offline/locked."
                    } else {
                        "Resource accessed while app was in the foreground."
                    }
                )
            )

            // Auto-reset live indicator after 4 seconds
            delay(4000)
            if (_micState.value.activeApp == appName) {
                _micState.value = _micState.value.copy(isMicActive = false)
            }
        }
    }

    private fun sendAlertNotification(
        appName: String,
        resourceType: ResourceType,
        isBackground: Boolean,
        durationSeconds: Int
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isBackground) {
            "⚠️ Attention: $appName accessed ${resourceType.displayName}"
        } else {
            "Privacy Alert: ${resourceType.displayName} Active"
        }

        val content = if (isBackground) {
            "$appName accessed your ${resourceType.displayName.lowercase()} for ${durationSeconds}s while running in the background."
        } else {
            "$appName is actively using the ${resourceType.displayName.lowercase()}."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Sensor Lab: Starts reading decibels from microphone hardware
     */
    fun startLiveDecibelMeter() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (_isRecordingLabActive.value) return

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .coerceAtLeast(2048)

        runCatching {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            audioRecord?.startRecording()
            _isRecordingLabActive.value = true

            decibelJob = scope.launch(Dispatchers.Default) {
                val buffer = ShortArray(bufferSize)
                while (isActive && _isRecordingLabActive.value) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = sqrt(sum / read)
                        // Approximate sound pressure decibel
                        val db = if (rms > 1.0) (20.0 * log10(rms)).toFloat() else 0f
                        _liveDecibels.value = (db - 15f).coerceIn(0f, 100f)
                    }
                    delay(100)
                }
            }
        }
    }

    fun stopLiveDecibelMeter() {
        _isRecordingLabActive.value = false
        decibelJob?.cancel()
        decibelJob = null
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
        _liveDecibels.value = 0f
    }

    companion object {
        private const val CHANNEL_ID = "privacy_guard_alerts"
        private const val NOTIFICATION_ID = 1001
    }
}
