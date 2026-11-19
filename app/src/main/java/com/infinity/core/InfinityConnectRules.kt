package com.infinity.core

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
 * Accepts hostnames, IPv4, and IPv6 literals without URL syntax or whitespace.
 * This keeps the stored endpoint host-only before a real RDP transport is added.
 */
internal fun isValidInfinityHost(value: String): Boolean {
    val host = value.trim()
    if (host.isEmpty() || host.length > 253) return false
    if (host.contains("..") || host.startsWith(".") || host.endsWith(".")) return false
    return host.matches(Regex("[A-Za-z0-9.:%_-]+"))
}
