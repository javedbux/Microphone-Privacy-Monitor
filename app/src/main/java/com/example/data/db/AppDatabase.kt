package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AccessEvent
import com.example.data.model.AccessState
import com.example.data.model.ResourceType
import com.example.data.model.RiskLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Converters {
    @TypeConverter
    fun fromResourceType(value: ResourceType): String = value.name

    @TypeConverter
    fun toResourceType(value: String): ResourceType = runCatching {
        ResourceType.valueOf(value)
    }.getOrDefault(ResourceType.MICROPHONE)

    @TypeConverter
    fun fromAccessState(value: AccessState): String = value.name

    @TypeConverter
    fun toAccessState(value: String): AccessState = runCatching {
        AccessState.valueOf(value)
    }.getOrDefault(AccessState.SINGLE_EVENT)

    @TypeConverter
    fun fromRiskLevel(value: RiskLevel): String = value.name

    @TypeConverter
    fun toRiskLevel(value: String): RiskLevel = runCatching {
        RiskLevel.valueOf(value)
    }.getOrDefault(RiskLevel.NORMAL)
}

@Database(entities = [AccessEvent::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accessEventDao(): AccessEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "privacy_guard_db"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialEvents(database.accessEventDao())
                    }
                }
            }

            private suspend fun populateInitialEvents(dao: AccessEventDao) {
                val now = System.currentTimeMillis()
                val oneHour = 3600000L
                val oneMinute = 60000L

                val initialEvents = listOf(
                    AccessEvent(
                        timestamp = now - 12 * oneMinute,
                        appName = "Instagram",
                        packageName = "com.instagram.android",
                        resourceType = ResourceType.MICROPHONE,
                        accessState = AccessState.STOPPED,
                        durationSeconds = 14,
                        isBackground = true,
                        riskLevel = RiskLevel.WORTH_CHECKING,
                        notes = "Microphone accessed while application was in the background. Device screen was locked."
                    ),
                    AccessEvent(
                        timestamp = now - 35 * oneMinute,
                        appName = "WhatsApp",
                        packageName = "com.whatsapp",
                        resourceType = ResourceType.MICROPHONE,
                        accessState = AccessState.STOPPED,
                        durationSeconds = 8,
                        isBackground = false,
                        riskLevel = RiskLevel.NORMAL,
                        notes = "Microphone recorded voice note while user had the chat screen open."
                    ),
                    AccessEvent(
                        timestamp = now - 1 * oneHour - 15 * oneMinute,
                        appName = "Social Audio Lounge",
                        packageName = "com.stealth.audioapp",
                        resourceType = ResourceType.MICROPHONE,
                        accessState = AccessState.STOPPED,
                        durationSeconds = 48,
                        isBackground = true,
                        riskLevel = RiskLevel.INVESTIGATE,
                        notes = "Repeated background microphone recording detected for 48s without active voice call indicator."
                    ),
                    AccessEvent(
                        timestamp = now - 2 * oneHour,
                        appName = "Google Maps",
                        packageName = "com.google.android.apps.maps",
                        resourceType = ResourceType.LOCATION,
                        accessState = AccessState.SINGLE_EVENT,
                        durationSeconds = 42,
                        isBackground = false,
                        riskLevel = RiskLevel.NORMAL,
                        notes = "Precise GPS location requested during active route navigation."
                    ),
                    AccessEvent(
                        timestamp = now - 3 * oneHour,
                        appName = "WhatsApp",
                        packageName = "com.whatsapp",
                        resourceType = ResourceType.CONTACTS,
                        accessState = AccessState.SINGLE_EVENT,
                        durationSeconds = 0,
                        isBackground = false,
                        riskLevel = RiskLevel.NORMAL,
                        notes = "Contact sync refresh requested during active app usage."
                    ),
                    AccessEvent(
                        timestamp = now - 4 * oneHour - 30 * oneMinute,
                        appName = "Chrome",
                        packageName = "com.android.chrome",
                        resourceType = ResourceType.CLIPBOARD,
                        accessState = AccessState.SINGLE_EVENT,
                        durationSeconds = 0,
                        isBackground = false,
                        riskLevel = RiskLevel.NORMAL,
                        notes = "Clipboard read on address bar tap."
                    ),
                    AccessEvent(
                        timestamp = now - 6 * oneHour,
                        appName = "Camera",
                        packageName = "com.google.android.GoogleCamera",
                        resourceType = ResourceType.CAMERA,
                        accessState = AccessState.STOPPED,
                        durationSeconds = 12,
                        isBackground = false,
                        riskLevel = RiskLevel.NORMAL,
                        notes = "Camera sensor activated in viewfinder."
                    )
                )
                dao.insertEvents(initialEvents)
            }
        }
    }
}
