package com.infinity.core

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Pure validation rules for INFINITY CONNECT profiles.
 * Kept independent from Android/Compose so GitHub Actions can test them locally.
 */
/** Formats host/port pairs unambiguously, including IPv6 literals. */
internal fun formatInfinityEndpointAddress(host: String, port: String): String {
    val normalizedPort = parseInfinityPort(port) ?: return ""
    val displayHost = host.trim()
    if (displayHost.isEmpty()) return ""
    val formattedHost = if (displayHost.contains(':')) "[$displayHost]" else displayHost
    return "$formattedHost:$normalizedPort"
}

internal fun parseInfinityPort(value: String): Int? {
    return value.trim().toIntOrNull()?.takeIf { it in 1..65535 }
}

/** Profile labels are user-visible metadata; reject blank, oversized and control-character values. */
internal fun isValidInfinityProfileName(value: String): Boolean {
    val name = value.trim()
    return name.isNotEmpty() && name.length <= 80 && name.none { it.isISOControl() }
}

/** Windows usernames may contain domain separators, but never control characters. */
internal fun isValidInfinityUsername(value: String): Boolean {
    val username = value.trim()
    return username.isNotEmpty() && username.length <= 128 && username.none { it.isISOControl() }
}

private fun normalizeInfinityEndpointHost(host: String): String {
    val trimmed = host.trim()
    if (trimmed.contains(':')) {
        val parts = trimmed.split('%', limit = 2)
        val address = parts[0]
        val normalizedAddress = try {
            (InetAddress.getByName(address) as? Inet6Address)
                ?.hostAddress
                ?.lowercase(java.util.Locale.ROOT)
                ?: address.lowercase(java.util.Locale.ROOT)
        } catch (_: Exception) {
            address.lowercase(java.util.Locale.ROOT)
        }
        val scope = parts.getOrNull(1)
        return if (scope == null) normalizedAddress else "$normalizedAddress%$scope"
    }
    return try {
        IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES).lowercase(java.util.Locale.ROOT)
    } catch (_: IllegalArgumentException) {
        trimmed.lowercase(java.util.Locale.ROOT)
    }
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
            normalizeInfinityEndpointHost(endpoint.first) == normalizeInfinityEndpointHost(normalizedHost) &&
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


/**
 * Collects valid persisted entries without letting malformed rows consume the valid-entry cap.
 * The mapper may return null for invalid or duplicate records.
 */
internal fun <T, R : Any> collectValidInfinityProfiles(
    records: Iterable<T>,
    maxProfiles: Int,
    mapValid: (T) -> R?
): List<R> {
    if (maxProfiles <= 0) return emptyList()

    val result = mutableListOf<R>()
    for (record in records) {
        if (result.size >= maxProfiles) break
        val mapped = mapValid(record) ?: continue
        result.add(mapped)
    }
    return result
}

/** Validate a complete persisted profile before it is exposed to the CONNECT UI. */
internal fun isValidInfinityProfileRecord(
    name: String,
    host: String,
    port: String,
    username: String
): Boolean {
    return isValidInfinityProfileName(name) &&
        isValidInfinityHost(host) &&
        parseInfinityPort(port) != null &&
        isValidInfinityUsername(username)
}
