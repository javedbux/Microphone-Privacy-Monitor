package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSlateSurface
import com.example.ui.theme.SecurityEmerald
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardStroke
import com.example.ui.theme.WarningAmber

@Composable
fun SensorLabScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val liveDb by viewModel.liveDecibels.collectAsStateWithLifecycle()
    val isRecordingActive by viewModel.isRecordingLabActive.collectAsStateWithLifecycle()
    val micState by viewModel.micState.collectAsStateWithLifecycle()
    val allEvents by viewModel.allEvents.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startLabDecibelMeter()
        } else {
            Toast.makeText(context, "Microphone permission required for sound tester", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopLabDecibelMeter()
        }
    }

    val animatedDb by animateFloatAsState(
        targetValue = liveDb,
        label = "db_animation"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("sensor_lab_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "Sensor Lab & Privacy Audit",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "Hardware diagnostics & voice security testing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Live Microphone dB Visualizer Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("decibel_meter_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (isRecordingActive) CyberCyan else SlateCardStroke
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Sound Wave",
                                tint = CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LIVE MICROPHONE DECIBEL METER",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isRecordingActive) SecurityEmerald.copy(alpha = 0.2f)
                                    else SlateCardStroke
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isRecordingActive) "LIVE SENSING" else "IDLE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isRecordingActive) SecurityEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Decibel readout
                    Text(
                        text = if (isRecordingActive) "${animatedDb.toInt()} dB" else "-- dB",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = when {
                            !isRecordingActive -> MaterialTheme.colorScheme.onSurfaceVariant
                            animatedDb > 75 -> AlertCrimson
                            animatedDb > 55 -> WarningAmber
                            else -> CyberCyan
                        }
                    )

                    Text(
                        text = when {
                            !isRecordingActive -> "Tap Start Test below to verify hardware capture"
                            animatedDb > 75 -> "Loud Environment / Nearby Speech"
                            animatedDb > 50 -> "Normal Conversation Level"
                            animatedDb > 25 -> "Quiet Ambient Noise"
                            else -> "Silent Background"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Level Bar
                    LinearProgressIndicator(
                        progress = { (animatedDb / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = when {
                            animatedDb > 75 -> AlertCrimson
                            animatedDb > 55 -> WarningAmber
                            else -> CyberCyan
                        },
                        trackColor = SlateCardStroke
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Controls
                    Button(
                        onClick = {
                            if (isRecordingActive) {
                                viewModel.stopLabDecibelMeter()
                            } else {
                                val hasAudioPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasAudioPermission) {
                                    viewModel.startLabDecibelMeter()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("toggle_decibel_test_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecordingActive) AlertCrimson else CyberCyan
                        )
                    ) {
                        Icon(
                            imageVector = if (isRecordingActive) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecordingActive) "Stop" else "Start",
                            tint = if (isRecordingActive) Color.White else DeepSlateSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRecordingActive) "Stop Decibel Test" else "Start Live Sound Test",
                            color = if (isRecordingActive) Color.White else DeepSlateSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Hardware Diagnostics Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Hardware & Audio Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    HardwareRow(
                        label = "Microphone Hardware Mute",
                        value = if (micState.isHardwareMuted) "Muted" else "Unmuted / Ready",
                        valueColor = if (micState.isHardwareMuted) WarningAmber else SecurityEmerald
                    )
                    HardwareRow(
                        label = "Audio Recording Callback",
                        value = "Active (Registered)",
                        valueColor = SecurityEmerald
                    )
                    HardwareRow(
                        label = "Global Audio Source",
                        value = micState.activeSource,
                        valueColor = Color.White
                    )
                    HardwareRow(
                        label = "Android Version",
                        value = "API ${android.os.Build.VERSION.SDK_INT}",
                        valueColor = Color.White
                    )
                }
            }
        }

        // Offline Voice Protection Guide
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Tips",
                            tint = CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "How to Stop Background Listening",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    SecurityTipItem(
                        number = "1",
                        title = "Use Quick Settings Sensor Toggle",
                        description = "On Android 12 and newer, pull down Quick Settings and toggle 'Mic Access' off to disconnect the microphone at the OS level."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SecurityTipItem(
                        number = "2",
                        title = "Set Microphone to 'Only While Using App'",
                        description = "In App Settings, set microphone permission to 'Allow only while using the app' so background audio recording is blocked."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SecurityTipItem(
                        number = "3",
                        title = "Review 'Worth Checking' Timeline Logs",
                        description = "Check Privacy Guard's timeline whenever you suspect an app accessed the microphone while your device was locked."
                    )
                }
            }
        }

        // Export / Share Audit Report
        item {
            OutlinedButton(
                onClick = {
                    val reportText = buildString {
                        appendLine("🛡️ PRIVACY GUARD AUDIT REPORT")
                        appendLine("Generated: ${java.util.Date()}")
                        appendLine("Total Events Logged: ${allEvents.size}")
                        appendLine("Background / Flagged Events: ${allEvents.count { it.isBackground }}")
                        appendLine("--------------------------------")
                        appendLine("Recent Events:")
                        allEvents.take(5).forEach {
                            appendLine("- ${it.appName} (${it.resourceType.displayName}): ${it.riskLevel.label} [${it.durationSeconds}s]")
                        }
                    }

                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Privacy Guard Report", reportText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Privacy Report copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_report_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Export Privacy Audit Report",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HardwareRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun SecurityTipItem(
    number: String,
    title: String,
    description: String
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(CyberCyan.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall,
                color = CyberCyan,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}
