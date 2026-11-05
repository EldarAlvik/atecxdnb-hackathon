package no.scamguardian.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import no.scamguardian.app.data.BoardStore
import no.scamguardian.app.monitor.MonitorService
import no.scamguardian.app.ui.GuardianRoot
import no.scamguardian.app.ui.theme.GuardianTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var store: BoardStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GuardianTheme { GuardianRoot() } }

        // While the app is visible, the monitor runs whenever a board is paired.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                store.paired.collect { board ->
                    if (board != null) MonitorService.start(this@MainActivity) else MonitorService.stop(this@MainActivity)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        lifecycleScope.launch {
            if (!store.backgroundMonitoring.first()) MonitorService.stop(applicationContext)
        }
    }
}
