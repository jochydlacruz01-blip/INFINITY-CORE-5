package com.infinity.core

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Pure validation rules for INFINITY CONNECT profiles.
 * Kept independent from Android/Compose so GitHub Actions can test them locally.
 */
internal fun parseInfinityPort(value: String): Int? {
    return value.trim().toIntOrNull()?.takeIf { it in 1..65535 }
}

internal fun hasDuplicateInfinityEndpoint(
    host: String,
    port: String,
    savedEndpoints: List<Pair<String, String>>,
    excludedIndex: Int = -1
): Boolean {
    val normalizedHost = host.trim()
    val normalizedPort = parseInfinityPort(port) ?: return false

    return savedEndpoints.withIndex().any { (index, endpoint) ->
        index != excludedIndex &&
            endpoint.first.trim().equals(normalizedHost, ignoreCase = true) &&
            parseInfinityPort(endpoint.second) == normalizedPort
    }
}

/**
 * Validates a host-only endpoint without performing DNS lookups.
 * Accepts DNS names (including IDN), strict IPv4, and IPv6 literals with
 * an optional interface/scope identifier. URLs, ports, paths and whitespace
 * are intentionally rejected; the port is stored separately.
 */
internal fun isValidInfinityHost(value: String): Boolean {
    val host = value.trim()
    if (host.isEmpty() || host.length > 253) return false
    if (host.any { it.isWhitespace() } || host.contains('/') || host.contains('@')) return false

    if (host.contains(':')) {
        val parts = host.split('%')
        if (parts.size > 2) return false
        val address = parts[0]
        if (!address.contains(':') || !address.matches(Regex("[0-9A-Fa-f:.]+"))) {
            return false
        }
        if (host.count { it == '%' } > 1) return false
        if (parts.size == 2 && !parts[1].matches(Regex("[A-Za-z0-9_.-]{1,32}"))) {
            return false
        }
        return try {
            InetAddress.getByName(address) is Inet6Address
        } catch (_: Exception) {
            false
        }
    }

    if (host.matches(Regex("[0-9.]+"))) {
        val octets = host.split('.')
        return octets.size == 4 && octets.all { octet ->
            octet.isNotEmpty() &&
                (octet.length == 1 || !octet.startsWith('0')) &&
                octet.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    val asciiHost = try {
        IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES)
    } catch (_: IllegalArgumentException) {
        return false
    }
    if (asciiHost.length > 253 || asciiHost.startsWith('.') || asciiHost.endsWith('.')) {
        return false
    }

    return asciiHost.split('.').all { label ->
        label.isNotEmpty() &&
            label.length <= 63 &&
            !label.startsWith('-') &&
            !label.endsWith('-') &&
            label.matches(Regex("[A-Za-z0-9-]+"))
    }
}
