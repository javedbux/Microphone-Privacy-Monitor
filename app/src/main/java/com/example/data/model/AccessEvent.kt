package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ResourceType(val displayName: String, val iconSymbol: String) {
    MICROPHONE("Microphone", "🎙️"),
    CAMERA("Camera", "📷"),
    LOCATION("Location", "📍"),
    CONTACTS("Contacts", "👥"),
    CLIPBOARD("Clipboard", "📋"),
    PHONE("Phone", "📞"),
    SMS("SMS", "💬")
}

enum class AccessState {
    STARTED,
    STOPPED,
    SINGLE_EVENT
}

enum class RiskLevel(val label: String, val description: String) {
    NORMAL(
        label = "Normal",
        description = "App was actively in use on screen during access."
    ),
    WORTH_CHECKING(
        label = "Worth Checking",
        description = "App accessed resource while running in the background or screen was inactive."
    ),
    INVESTIGATE(
        label = "Investigate",
        description = "Repeated or unauthorized background access detected while device was idle."
    )
}

@Entity(tableName = "access_events")
data class AccessEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val appName: String,
    val packageName: String,
    val resourceType: ResourceType,
    val accessState: AccessState = AccessState.SINGLE_EVENT,
    val durationSeconds: Int = 0,
    val isBackground: Boolean = false,
    val riskLevel: RiskLevel = RiskLevel.NORMAL,
    val notes: String = ""
)
