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
