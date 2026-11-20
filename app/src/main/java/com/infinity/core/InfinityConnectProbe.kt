package com.infinity.core

import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class InfinityProbeStatus(val message: String) {
    CHECKING("Comprobando disponibilidad TCP…"),
    CONNECTED("Puerto TCP accesible. Esto no confirma autenticación ni una sesión RDP."),
    INVALID_ENDPOINT("No se pudo comprobar: dirección o puerto no válido."),
    HOST_NOT_FOUND("No se encontró el equipo. Revisa el nombre o la red."),
    TIMEOUT("Tiempo agotado. El equipo no respondió dentro del límite."),
    REFUSED("El equipo respondió, pero rechazó la conexión en ese puerto."),
    BLOCKED("Android o la política de red bloqueó la comprobación."),
    NETWORK_ERROR("No se pudo completar la comprobación por un error de red.")
}

internal fun infinityProbeFailureKind(error: Exception): InfinityProbeStatus = when (error) {
    is UnknownHostException -> InfinityProbeStatus.HOST_NOT_FOUND
    is SocketTimeoutException -> InfinityProbeStatus.TIMEOUT
    is ConnectException -> InfinityProbeStatus.REFUSED
    is SecurityException -> InfinityProbeStatus.BLOCKED
    is IllegalArgumentException -> InfinityProbeStatus.INVALID_ENDPOINT
    is IOException -> InfinityProbeStatus.NETWORK_ERROR
    else -> InfinityProbeStatus.NETWORK_ERROR
}

/**
 * Checks only the user-selected saved endpoint. This is a TCP reachability
 * diagnostic, not an RDP handshake, credential check, or remote session.
 */
internal suspend fun probeInfinityTcpEndpoint(
    host: String,
    port: String,
    timeoutMs: Int = 4_000
): InfinityProbeStatus = withContext(Dispatchers.IO) {
    val parsedPort = parseInfinityPort(port)
    if (!isValidInfinityHost(host) || parsedPort == null || timeoutMs <= 0) {
        return@withContext InfinityProbeStatus.INVALID_ENDPOINT
    }

    try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host.trim(), parsedPort), timeoutMs)
        }
        InfinityProbeStatus.CONNECTED
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        infinityProbeFailureKind(error)
    }
}
