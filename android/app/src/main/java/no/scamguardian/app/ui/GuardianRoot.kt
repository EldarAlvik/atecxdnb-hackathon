package no.scamguardian.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import no.scamguardian.app.data.BoardStore
import no.scamguardian.app.data.PairedBoard
import no.scamguardian.app.ui.dashboard.DashboardScreen
import no.scamguardian.app.ui.events.EventsScreen
import no.scamguardian.app.ui.onboarding.OnboardingScreen
import no.scamguardian.app.ui.settings.SettingsScreen
import no.scamguardian.app.ui.words.WordsScreen

sealed interface PairedState {
    data object Loading : PairedState
    data class Loaded(val board: PairedBoard?) : PairedState
}

@HiltViewModel
class AppViewModel @Inject constructor(store: BoardStore) : ViewModel() {
    val paired = store.paired.map { PairedState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PairedState.Loading)
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Dashboard("Guard", Icons.Filled.Home),
    Words("Family words", Icons.Filled.Lock),
    Events("Events", Icons.AutoMirrored.Filled.List),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
fun GuardianRoot(vm: AppViewModel = hiltViewModel()) {
    val paired by vm.paired.collectAsStateWithLifecycle()
    var setupRequested by rememberSaveable { mutableStateOf(false) }
    when (val p = paired) {
        PairedState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is PairedState.Loaded -> {
            // Stay in onboarding until it says it is finished, even once the new pairing is saved.
            if (p.board == null && !setupRequested) SideEffect { setupRequested = true }
            if (p.board == null || setupRequested) {
                OnboardingScreen(
                    onFinished = { setupRequested = false },
                    onCancel = if (p.board != null) ({ setupRequested = false }) else null,
                )
            } else {
                MainScreen(onRerunSetup = { setupRequested = true })
            }
        }
    }
}

@Composable
private fun MainScreen(onRerunSetup: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Dashboard) }
    AskForNotifications()
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.Dashboard -> DashboardScreen()
                Tab.Words -> WordsScreen()
                Tab.Events -> EventsScreen()
                Tab.Settings -> SettingsScreen(onRerunSetup = onRerunSetup)
            }
        }
    }
}

/** Android 13+: alerts need POST_NOTIFICATIONS. Asked once when the main screen first shows. */
@Composable
private fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
