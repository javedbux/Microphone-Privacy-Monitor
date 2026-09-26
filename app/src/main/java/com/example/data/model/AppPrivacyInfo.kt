package com.example.data.model

import android.graphics.drawable.Drawable

data class AppPrivacyInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false,
    val icon: Drawable? = null,
    val micAllowed: Boolean = false,
    val cameraAllowed: Boolean = false,
    val locationAllowed: Boolean = false,
    val contactsAllowed: Boolean = false,
    val phoneAllowed: Boolean = false,
    val smsAllowed: Boolean = false,
    val backgroundEventsCount: Int = 0,
    val totalEventsCount: Int = 0,
    val riskScore: Int = 0,
    val permissionsGrantedCount: Int = 0
)
