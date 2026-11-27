package com.infinity.core

import java.net.Inet6Address
import java.net.InetAddress

/** Restricts the remote Companion endpoint to numeric LAN/VPN addresses; DNS and loopback are rejected. */
internal object InfinityConnectPrivateEndpointPolicy {
    fun isAllowedHost(host: String): Boolean {
        val value = host.trim().removePrefix("[").removeSuffix("]")
        if (value.isEmpty() || '%' in value) return false

        return if (':' in value) {
            val address = runCatching { InetAddress.getByName(value) }.getOrNull() as? Inet6Address ?: return false
            val bytes = address.address
            val uniqueLocal = (bytes[0].toInt() and 0xFE) == 0xFC
            uniqueLocal
        } else {
            val octets = value.split('.')
            if (octets.size != 4 || octets.any {
                it.isEmpty() || (it.length > 1 && it.startsWith('0')) || !it.all(Char::isDigit)
            }) return false
            val bytes = octets.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return false }
            val a = bytes[0]
            val b = bytes[1]
            a == 10 || (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) || (a == 100 && b in 64..127)
        }
    }
}
