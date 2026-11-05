package no.scamguardian.app.board

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDnsTest {
    private fun addr(s: String) = InetAddress.getByName(s)

    @Test
    fun localRanges() {
        listOf("192.168.0.57", "10.0.2.2", "172.16.4.1", "127.0.0.1", "169.254.10.1", "fe80::1", "fd12:3456::1", "::1")
            .forEach { assertTrue(it, LocalDns.isLocalAddress(addr(it))) }
        listOf("8.8.8.8", "172.32.0.1", "100.64.0.1", "2001:4860:4860::8888")
            .forEach { assertFalse(it, LocalDns.isLocalAddress(addr(it))) }
    }

    @Test
    fun resolvesOnlyTheBoardName() {
        assertEquals(listOf(addr("192.168.0.57")), LocalDns("192.168.0.57").lookup(LocalDns.BOARD_HOST))
        assertUnknownHost { LocalDns("192.168.0.57").lookup("example.com") }
    }

    @Test
    fun refusesPublicAddresses() {
        assertUnknownHost { LocalDns("8.8.8.8").lookup(LocalDns.BOARD_HOST) }
        val fake = object : Dns {
            override fun lookup(hostname: String) = listOf(addr("93.184.216.34"), addr("192.168.1.9"))
        }
        assertEquals(listOf(addr("192.168.1.9")), LocalDns("guardian.local", fake).lookup(LocalDns.BOARD_HOST))
    }

    private fun assertUnknownHost(block: () -> Unit) {
        try {
            block()
        } catch (_: UnknownHostException) {
            return
        }
        throw AssertionError("expected UnknownHostException")
    }
}
