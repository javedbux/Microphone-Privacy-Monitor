package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.repository.PrivacyRepository
import com.example.service.MicMonitoringManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PrivacyGuardApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { PrivacyRepository(database.accessEventDao(), this) }
    val micMonitoringManager by lazy {
        MicMonitoringManager(this, repository, applicationScope)
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize lazy properties early so background audio callback is active
        micMonitoringManager.updateHardwareMuteState()
    }
}
