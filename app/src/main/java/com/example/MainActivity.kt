package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.components.AppDetailSheet
import com.example.ui.components.EventDetailSheet
import com.example.ui.screens.AppProfilesScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SensorLabScreen
import com.example.ui.screens.TimelineScreen
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSlateSurface
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlateCard
import kotlinx.coroutines.launch

enum class AppTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    DASHBOARD(
        title = "Dashboard",
        selectedIcon = Icons.Filled.Security,
        unselectedIcon = Icons.Outlined.Security,
        tag = "nav_dashboard"
    ),
    TIMELINE(
        title = "Timeline",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        tag = "nav_timeline"
    ),
    APPS(
        title = "App Profiles",
        selectedIcon = Icons.Filled.Apps,
        unselectedIcon = Icons.Outlined.Apps,
        tag = "nav_apps"
    ),
    SENSOR_LAB(
        title = "Sensor Lab",
        selectedIcon = Icons.Filled.GraphicEq,
        unselectedIcon = Icons.Outlined.GraphicEq,
        tag = "nav_sensor_lab"
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    viewModel: MainViewModel = viewModel()
) {
    var currentTab by remember { mutableStateOf(AppTab.DASHBOARD) }
    val scope = rememberCoroutineScope()

    val selectedEvent by viewModel.selectedEventForDetail.collectAsStateWithLifecycle()
    val selectedApp by viewModel.selectedAppForDetail.collectAsStateWithLifecycle()

    val eventSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Request notification permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Handle back button
    BackHandler(enabled = selectedEvent != null || selectedApp != null || currentTab != AppTab.DASHBOARD) {
        when {
            selectedEvent != null -> {
                scope.launch { eventSheetState.hide() }.invokeOnCompletion {
                    viewModel.selectEventForDetail(null)
                }
            }
            selectedApp != null -> {
                scope.launch { appSheetState.hide() }.invokeOnCompletion {
                    viewModel.selectAppForDetail(null)
                }
            }
            currentTab != AppTab.DASHBOARD -> {
                currentTab = AppTab.DASHBOARD
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightNavy),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("main_navigation_bar"),
                containerColor = DeepSlateSurface,
                contentColor = Color.White,
                tonalElevation = 8.dp
            ) {
                AppTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DeepSlateSurface,
                            selectedTextColor = CyberCyan,
                            indicatorColor = CyberCyan,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MidnightNavy)
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = currentTab,
                label = "tab_crossfade"
            ) { tab ->
                when (tab) {
                    AppTab.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToTimeline = { currentTab = AppTab.TIMELINE },
                        onNavigateToLab = { currentTab = AppTab.SENSOR_LAB },
                        onNavigateToApps = { currentTab = AppTab.APPS }
                    )
                    AppTab.TIMELINE -> TimelineScreen(
                        viewModel = viewModel
                    )
                    AppTab.APPS -> AppProfilesScreen(
                        viewModel = viewModel
                    )
                    AppTab.SENSOR_LAB -> SensorLabScreen(
                        viewModel = viewModel
                    )
                }
            }
        }

        // Event Detail BottomSheet
        selectedEvent?.let { event ->
            EventDetailSheet(
                event = event,
                sheetState = eventSheetState,
                onDismiss = {
                    scope.launch { eventSheetState.hide() }.invokeOnCompletion {
                        viewModel.selectEventForDetail(null)
                    }
                },
                onOpenAppSettings = { pkg ->
                    viewModel.openSystemAppSettings(pkg)
                },
                onDeleteEvent = { id ->
                    viewModel.deleteEvent(id)
                }
            )
        }

        // App Privacy Profile Detail BottomSheet
        selectedApp?.let { appInfo ->
            AppDetailSheet(
                appInfo = appInfo,
                sheetState = appSheetState,
                onDismiss = {
                    scope.launch { appSheetState.hide() }.invokeOnCompletion {
                        viewModel.selectAppForDetail(null)
                    }
                },
                onOpenAppSettings = { pkg ->
                    viewModel.openSystemAppSettings(pkg)
                }
            )
        }
    }
}
