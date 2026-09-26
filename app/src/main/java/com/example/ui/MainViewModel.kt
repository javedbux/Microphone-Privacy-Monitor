package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PrivacyGuardApplication
import com.example.data.model.AccessEvent
import com.example.data.model.AppPrivacyInfo
import com.example.data.model.ResourceType
import com.example.data.model.RiskLevel
import com.example.service.LiveMicState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TimelineFilter(val label: String) {
    ALL("All Events"),
    MICROPHONE("🎙️ Mic"),
    CAMERA("📷 Camera"),
    LOCATION("📍 Location"),
    FLAGGED("⚠️ Worth Checking")
}

enum class AppListFilter(val label: String) {
    ALL("All Apps"),
    MIC_ONLY("🎙️ Has Mic"),
    CAMERA_ONLY("📷 Has Camera"),
    FLAGGED_ONLY("⚠️ Background Access")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PrivacyGuardApplication
    private val repository = app.repository
    private val micManager = app.micMonitoringManager

    val allEvents: StateFlow<List<AccessEvent>> = repository.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTimelineFilter = MutableStateFlow(TimelineFilter.ALL)
    val selectedTimelineFilter: StateFlow<TimelineFilter> = _selectedTimelineFilter.asStateFlow()

    val filteredEvents: StateFlow<List<AccessEvent>> = combine(
        allEvents,
        _selectedTimelineFilter
    ) { events, filter ->
        when (filter) {
            TimelineFilter.ALL -> events
            TimelineFilter.MICROPHONE -> events.filter { it.resourceType == ResourceType.MICROPHONE }
            TimelineFilter.CAMERA -> events.filter { it.resourceType == ResourceType.CAMERA }
            TimelineFilter.LOCATION -> events.filter { it.resourceType == ResourceType.LOCATION }
            TimelineFilter.FLAGGED -> events.filter { it.riskLevel != RiskLevel.NORMAL }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val micState: StateFlow<LiveMicState> = micManager.micState
    val liveDecibels: StateFlow<Float> = micManager.liveDecibels
    val isRecordingLabActive: StateFlow<Boolean> = micManager.isRecordingLabActive

    private val _installedApps = MutableStateFlow<List<AppPrivacyInfo>>(emptyList())
    val installedApps: StateFlow<List<AppPrivacyInfo>> = _installedApps.asStateFlow()

    private val _isScanningApps = MutableStateFlow(false)
    val isScanningApps: StateFlow<Boolean> = _isScanningApps.asStateFlow()

    private val _appSearchQuery = MutableStateFlow("")
    val appSearchQuery: StateFlow<String> = _appSearchQuery.asStateFlow()

    private val _appListFilter = MutableStateFlow(AppListFilter.ALL)
    val appListFilter: StateFlow<AppListFilter> = _appListFilter.asStateFlow()

    val filteredApps: StateFlow<List<AppPrivacyInfo>> = combine(
        _installedApps,
        _appSearchQuery,
        _appListFilter
    ) { apps, query, filter ->
        apps.filter { appInfo ->
            val matchesQuery = query.isBlank() ||
                    appInfo.appName.contains(query, ignoreCase = true) ||
                    appInfo.packageName.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                AppListFilter.ALL -> true
                AppListFilter.MIC_ONLY -> appInfo.micAllowed
                AppListFilter.CAMERA_ONLY -> appInfo.cameraAllowed
                AppListFilter.FLAGGED_ONLY -> appInfo.backgroundEventsCount > 0
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedEventForDetail = MutableStateFlow<AccessEvent?>(null)
    val selectedEventForDetail: StateFlow<AccessEvent?> = _selectedEventForDetail.asStateFlow()

    private val _selectedAppForDetail = MutableStateFlow<AppPrivacyInfo?>(null)
    val selectedAppForDetail: StateFlow<AppPrivacyInfo?> = _selectedAppForDetail.asStateFlow()

    init {
        scanInstalledApps()
    }

    fun setTimelineFilter(filter: TimelineFilter) {
        _selectedTimelineFilter.value = filter
    }

    fun setAppSearchQuery(query: String) {
        _appSearchQuery.value = query
    }

    fun setAppListFilter(filter: AppListFilter) {
        _appListFilter.value = filter
    }

    fun selectEventForDetail(event: AccessEvent?) {
        _selectedEventForDetail.value = event
    }

    fun selectAppForDetail(appInfo: AppPrivacyInfo?) {
        _selectedAppForDetail.value = appInfo
    }

    fun scanInstalledApps() {
        viewModelScope.launch {
            _isScanningApps.value = true
            val apps = repository.scanInstalledApps()
            _installedApps.value = apps
            _isScanningApps.value = false
        }
    }

    fun deleteEvent(id: Long) {
        viewModelScope.launch {
            repository.deleteEvent(id)
            if (_selectedEventForDetail.value?.id == id) {
                _selectedEventForDetail.value = null
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _selectedEventForDetail.value = null
        }
    }

    fun triggerSimulation() {
        val sampleSuspiciousApps = listOf(
            Triple("Voice Assistant Mod", "com.voiceassistant.mod", 12),
            Triple("Social Reel Studio", "com.social.reel.studio", 24),
            Triple("Smart Audio Cleaner", "com.audio.cleaner.tools", 8),
            Triple("Call Recorder Lite", "com.callrecorder.service", 35)
        )
        val randomPick = sampleSuspiciousApps.random()
        micManager.triggerSimulatedEvent(
            appName = randomPick.first,
            packageName = randomPick.second,
            resourceType = ResourceType.MICROPHONE,
            isBackground = true,
            durationSeconds = randomPick.third,
            riskLevel = if (randomPick.third > 20) RiskLevel.INVESTIGATE else RiskLevel.WORTH_CHECKING
        )
    }

    fun startLabDecibelMeter() {
        micManager.startLiveDecibelMeter()
    }

    fun stopLabDecibelMeter() {
        micManager.stopLiveDecibelMeter()
    }

    fun openSystemAppSettings(packageName: String) {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
        }
    }

    fun openPrivacyDashboardSettings() {
        runCatching {
            val intent = Intent("android.settings.PRIVACY_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
        }.recoverCatching {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(fallback)
        }
    }
}
