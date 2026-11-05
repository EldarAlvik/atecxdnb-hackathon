package no.scamguardian.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import no.scamguardian.app.monitor.Notifier

@HiltAndroidApp
class GuardianApp : Application() {
    @Inject lateinit var notifier: Notifier

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
    }
}
