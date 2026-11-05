package no.scamguardian.app.board

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns

/**
 * The board has no TLS (protocol section 7), so its API is plain HTTP. Android's network security
 * config can only allow cleartext per host name, not per address range, so the app never puts the
 * board's IP in a URL. Every request goes to the fixed name [BOARD_HOST] (cleartext is allowed only
 * for `*.local` in `res/xml/network_security_config.xml`), and this resolver maps that name to the
 * board's real address, but only if the address is on the local network. A public address is
 * refused, so the app cannot be tricked into sending the token or family words in clear text over
 * the internet.
 */
class LocalDns(
    private val target: String,
    private val system: Dns = Dns.SYSTEM,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (!hostname.equals(BOARD_HOST, ignoreCase = true)) {
            throw UnknownHostException("Only the board can be reached")
        }
        val all = if (isIpLiteral(target)) listOf(InetAddress.getByName(target)) else system.lookup(target)
        val local = all.filter(::isLocalAddress)
        if (local.isEmpty()) throw UnknownHostException("$target is not on the local network")
        return local
    }

    companion object {
        const val BOARD_HOST = "guardian-board.local"

        fun isIpLiteral(host: String): Boolean =
            host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) || (host.contains(':') && host.all { it.isLetterOrDigit() || it == ':' || it == '.' || it == '%' })

        /** Private (RFC 1918), link-local, loopback, or IPv6 unique-local. */
        fun isLocalAddress(a: InetAddress): Boolean = when {
            a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress -> true
            a is Inet6Address -> (a.address[0].toInt() and 0xFE) == 0xFC
            a is Inet4Address -> false
            else -> false
        }
    }
}
