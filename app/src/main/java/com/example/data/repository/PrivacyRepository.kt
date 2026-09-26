package com.example.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.data.db.AccessEventDao
import com.example.data.model.AccessEvent
import com.example.data.model.AppPrivacyInfo
import com.example.data.model.ResourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class PrivacyRepository(
    private val accessEventDao: AccessEventDao,
    private val context: Context
) {
    val allEvents: Flow<List<AccessEvent>> = accessEventDao.getAllEvents()
    val micEventsCount: Flow<Int> = accessEventDao.getMicEventsCount()
    val backgroundEventsCount: Flow<Int> = accessEventDao.getBackgroundEventsCount()
    val totalEventsCount: Flow<Int> = accessEventDao.getTotalEventsCount()

    fun getEventsByResource(resourceType: ResourceType): Flow<List<AccessEvent>> =
        accessEventDao.getEventsByResource(resourceType)

    fun getFlaggedEvents(): Flow<List<AccessEvent>> =
        accessEventDao.getFlaggedEvents()

    fun getRecentEvents(limit: Int): Flow<List<AccessEvent>> =
        accessEventDao.getRecentEvents(limit)

    suspend fun logEvent(event: AccessEvent): Long = withContext(Dispatchers.IO) {
        accessEventDao.insertEvent(event)
    }

    suspend fun deleteEvent(id: Long) = withContext(Dispatchers.IO) {
        accessEventDao.deleteEventById(id)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        accessEventDao.clearAll()
    }

    suspend fun scanInstalledApps(): List<AppPrivacyInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installedPackages = runCatching {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }.getOrDefault(emptyList())

        val currentEvents = runCatching { accessEventDao.getAllEvents().first() }.getOrDefault(emptyList())
        val eventsByPackage = currentEvents.groupBy { it.packageName }

        val resultList = mutableListOf<AppPrivacyInfo>()

        for (pkg in installedPackages) {
            val appInfo = pkg.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val appLabel = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg.packageName)
            val icon = runCatching { pm.getApplicationIcon(appInfo) }.getOrNull()

            val permissions = pkg.requestedPermissions ?: emptyArray()

            val hasMic = permissions.contains("android.permission.RECORD_AUDIO")
            val hasCamera = permissions.contains("android.permission.CAMERA")
            val hasLocation = permissions.contains("android.permission.ACCESS_FINE_LOCATION") ||
                    permissions.contains("android.permission.ACCESS_COARSE_LOCATION")
            val hasContacts = permissions.contains("android.permission.READ_CONTACTS") ||
                    permissions.contains("android.permission.WRITE_CONTACTS")
            val hasPhone = permissions.contains("android.permission.READ_PHONE_STATE") ||
                    permissions.contains("android.permission.CALL_PHONE")
            val hasSms = permissions.contains("android.permission.READ_SMS") ||
                    permissions.contains("android.permission.RECEIVE_SMS")

            // Only surface apps that have at least one sensitive permission or are non-system apps
            if (!hasMic && !hasCamera && !hasLocation && !hasContacts && !hasPhone && !hasSms && isSystem) {
                continue
            }

            var grantedCount = 0
            if (hasMic) grantedCount++
            if (hasCamera) grantedCount++
            if (hasLocation) grantedCount++
            if (hasContacts) grantedCount++
            if (hasPhone) grantedCount++
            if (hasSms) grantedCount++

            val appEvents = eventsByPackage[pkg.packageName] ?: emptyList()
            val bgCount = appEvents.count { it.isBackground }
            val totalCount = appEvents.size

            // Calculate privacy risk score (0 to 100)
            var score = 15 // base
            if (hasMic) score += 30
            if (hasCamera) score += 20
            if (hasLocation) score += 15
            if (hasContacts) score += 10
            if (hasSms || hasPhone) score += 10
            if (bgCount > 0) score += (bgCount * 15).coerceAtMost(30)
            if (score > 100) score = 100

            resultList.add(
                AppPrivacyInfo(
                    packageName = pkg.packageName,
                    appName = appLabel,
                    isSystemApp = isSystem,
                    icon = icon,
                    micAllowed = hasMic,
                    cameraAllowed = hasCamera,
                    locationAllowed = hasLocation,
                    contactsAllowed = hasContacts,
                    phoneAllowed = hasPhone,
                    smsAllowed = hasSms,
                    backgroundEventsCount = bgCount,
                    totalEventsCount = totalCount,
                    riskScore = score,
                    permissionsGrantedCount = grantedCount
                )
            )
        }

        // Return sorted by risk score descending
        resultList.sortedWith(
            compareByDescending<AppPrivacyInfo> { it.backgroundEventsCount }
                .thenByDescending { it.micAllowed }
                .thenByDescending { it.riskScore }
        )
    }
}
