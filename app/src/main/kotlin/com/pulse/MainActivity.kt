package com.pulse

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.pulse.core.designsystem.theme.FitbitTheme
import com.pulse.data.datastore.FeatureFlagRepository
import com.pulse.data.health.HealthConnectDataSource
import com.pulse.feature.dashboard.navigation.Dashboard
import com.pulse.feature.dashboard.navigation.dashboardScreen
import com.pulse.feature.debug.navigation.DebugMenu
import com.pulse.feature.debug.navigation.debugMenuScreen
import com.pulse.feature.detail.navigation.MetricDetail
import com.pulse.feature.detail.navigation.metricDetailScreen
import com.pulse.feature.exercise.navigation.ExerciseDetail
import com.pulse.feature.exercise.navigation.ExerciseLog
import com.pulse.feature.exercise.navigation.exerciseDetailScreen
import com.pulse.feature.exercise.navigation.exerciseLogScreen
import com.pulse.feature.you.navigation.YouProfile
import com.pulse.feature.you.navigation.youScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var featureFlags: FeatureFlagRepository
    @Inject lateinit var healthConnectDataSource: HealthConnectDataSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val flags by featureFlags.observe().collectAsState(
                initial = com.pulse.data.datastore.FeatureFlagSnapshot.Default
            )

            FitbitTheme(
                darkTheme = flags.forceDarkMode || isSystemInDarkTheme(),
                dynamicColor = flags.useDynamicColor,
            ) {
                Surface(modifier = Modifier) {
                    FitbitNavHost(healthConnectDataSource)
                }
            }
        }
    }

    @Composable
    private fun isSystemInDarkTheme() = androidx.compose.foundation.isSystemInDarkTheme()
}

@Composable
fun FitbitNavHost(hcDataSource: HealthConnectDataSource) {
    val nav = rememberNavController()
    val context = LocalContext.current

    // Health Connect permission launcher
    var permissionsChecked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        permissionsChecked = true
        if (granted.isEmpty()) {
            Toast.makeText(context, "Health Connect permissions required for data sync", Toast.LENGTH_LONG).show()
        }
    }

    // Check and request permissions on first composition
    LaunchedEffect(Unit) {
        if (!hcDataSource.isAvailable()) {
            // HC not installed — skip
            permissionsChecked = true
            return@LaunchedEffect
        }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val needed = hcDataSource.requiredPermissions - granted
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed)
        } else {
            permissionsChecked = true
        }
    }

    NavHost(navController = nav, startDestination = Dashboard) {
        dashboardScreen(
            onNavigateToMetric = { metric -> nav.navigate(MetricDetail(metric.name)) },
            onNavigateToExerciseLog = { nav.navigate(ExerciseLog) },
            onNavigateToExerciseDetail = { sessionId -> nav.navigate(ExerciseDetail(sessionId)) },
            onNavigateToChat = {
                Toast.makeText(context, "Coach coming soon", Toast.LENGTH_SHORT).show()
            },
            onNavigateToProfile = {
                nav.navigate(YouProfile)
            },
            onNavigateToDebug = {
                if (BuildConfig.DEBUG_MENU_ENABLED) nav.navigate(DebugMenu)
            },
        )
        metricDetailScreen(onBack = { nav.popBackStack() })
        exerciseLogScreen(
            onBack = { nav.popBackStack() },
            onNavigateToDetail = { sessionId -> nav.navigate(ExerciseDetail(sessionId)) },
        )
        exerciseDetailScreen(onBack = { nav.popBackStack() })
        youScreen(onBack = { nav.popBackStack() })
        debugMenuScreen(onDismiss = { nav.popBackStack() })
    }
}
