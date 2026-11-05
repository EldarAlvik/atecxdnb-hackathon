package no.scamguardian.app.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** A board found on the LAN. */
data class FoundService(val deviceId: String, val host: String, val port: Int, val wsPort: Int)

/** Finds the paired board on the LAN (protocol section 5). Faked in tests. */
interface BoardDiscovery {
    /** Looks for `_guardian._tcp` with TXT `id` == [deviceId]. Null if not found within [timeoutMs]. */
    suspend fun find(deviceId: String, timeoutMs: Long = 8_000): FoundService?
}

/**
 * Android NSD. Two boards on one network both answer to `guardian.local`, so this goes by the TXT
 * `id` record, never by host name. Services are resolved one at a time: before API 34
 * `resolveService` fails with FAILURE_ALREADY_ACTIVE if another resolve is running.
 */
class NsdBoardDiscovery(context: Context) : BoardDiscovery {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    override suspend fun find(deviceId: String, timeoutMs: Long): FoundService? {
        val found = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { found.close() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) { found.trySend(info) }
            override fun onServiceLost(info: NsdServiceInfo) {}
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        try {
            return withTimeoutOrNull(timeoutMs) {
                for (info in found) {
                    val resolved = resolve(info) ?: continue
                    val id = resolved.attributes["id"]?.decodeToString()
                    if (id != deviceId) continue
                    val host = hostOf(resolved) ?: continue
                    val ws = resolved.attributes["ws"]?.decodeToString()?.toIntOrNull() ?: DEFAULT_WS_PORT
                    return@withTimeoutOrNull FoundService(id, host, resolved.port, ws)
                }
                null
            }
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    private suspend fun resolve(info: NsdServiceInfo): NsdServiceInfo? =
        withTimeoutOrNull(4_000) {
            if (Build.VERSION.SDK_INT >= 34) resolve34(info) else resolveLegacy(info)
        }

    @Suppress("DEPRECATION")
    private suspend fun resolveLegacy(info: NsdServiceInfo): NsdServiceInfo? = suspendCancellableCoroutine { cont ->
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                if (cont.isActive) cont.resume(serviceInfo)
            }
        })
    }

    @RequiresApi(34)
    private suspend fun resolve34(info: NsdServiceInfo): NsdServiceInfo? {
        var registered: NsdManager.ServiceInfoCallback? = null
        try {
            return suspendCancellableCoroutine { cont ->
                val cb = object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                        if (serviceInfo.hostAddresses.isNotEmpty() && cont.isActive) cont.resume(serviceInfo)
                    }

                    override fun onServiceLost() {}
                    override fun onServiceInfoCallbackUnregistered() {}
                }
                registered = cb
                nsd.registerServiceInfoCallback(info, executor, cb)
            }
        } finally {
            registered?.let { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        }
    }

    @Suppress("DEPRECATION")
    private fun hostOf(info: NsdServiceInfo): String? {
        val addrs: List<InetAddress> =
            if (Build.VERSION.SDK_INT >= 34) info.hostAddresses else listOfNotNull(info.host)
        return (addrs.firstOrNull { it is Inet4Address } ?: addrs.firstOrNull())?.hostAddress
    }

    companion object {
        const val SERVICE_TYPE = "_guardian._tcp"
        const val DEFAULT_WS_PORT = 81
    }
}
