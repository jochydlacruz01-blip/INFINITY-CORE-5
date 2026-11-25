package com.infinity.core

/** Structured, local preflight checks for a remote desktop endpoint. */
internal enum class InfinityPreflightSeverity { INFO, WARNING, BLOCKED }

internal data class InfinityPreflightItem(val code: String, val severity: InfinityPreflightSeverity, val message: String)

internal fun runInfinityConnectPreflight(host: String, port: String, username: String): List<InfinityPreflightItem> {
    val results = mutableListOf<InfinityPreflightItem>()
    val parsedPort = parseInfinityPort(port)
    if (!isValidInfinityHost(host.trim())) {
        results += InfinityPreflightItem("host.invalid", InfinityPreflightSeverity.BLOCKED, "La dirección del equipo no es válida.")
    } else {
        results += InfinityPreflightItem("host.valid", InfinityPreflightSeverity.INFO, "Dirección válida; aún no se ha comprobado su disponibilidad.")
    }
    if (parsedPort == null) {
        results += InfinityPreflightItem("port.invalid", InfinityPreflightSeverity.BLOCKED, "El puerto debe estar entre 1 y 65535.")
    } else if (parsedPort != 3389) {
        results += InfinityPreflightItem("port.custom", InfinityPreflightSeverity.WARNING, "Se está usando un puerto personalizado.")
    } else {
        results += InfinityPreflightItem("port.default", InfinityPreflightSeverity.INFO, "Puerto RDP predeterminado seleccionado (3389).")
    }
    if (!isValidInfinityUsername(username)) {
        results += InfinityPreflightItem("username.invalid", InfinityPreflightSeverity.BLOCKED, "El usuario está vacío o contiene caracteres no permitidos.")
    } else {
        results += InfinityPreflightItem("username.valid", InfinityPreflightSeverity.INFO, "Usuario configurado.")
    }
    results += InfinityPreflightItem("network.security", InfinityPreflightSeverity.WARNING, "Para conexiones fuera de una red confiable, utiliza VPN. No expongas RDP directamente a Internet.")
    results += InfinityPreflightItem("session.unverified", InfinityPreflightSeverity.INFO, "Estas comprobaciones locales no verifican TLS, autenticación ni una sesión RDP.")
    return results
}

internal fun isInfinityPreflightReady(items: List<InfinityPreflightItem>): Boolean =
    items.none { it.severity == InfinityPreflightSeverity.BLOCKED }
