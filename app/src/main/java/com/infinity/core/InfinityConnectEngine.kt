package com.infinity.core

/**
 * Adapter contract for a native RDP implementation.
 * No implementation is considered operational until a real engine is wired and verified.
 */
data class InfinityConnectTarget(
    val host: String,
    val port: Int = 3389,
    val username: String,
    val password: CharArray? = null
)

sealed interface InfinityConnectEngineEvent {
    data object Connecting : InfinityConnectEngineEvent
    data object Authenticating : InfinityConnectEngineEvent
    data object Connected : InfinityConnectEngineEvent
    data class Failure(val message: String) : InfinityConnectEngineEvent
    data object Disconnected : InfinityConnectEngineEvent
}

/** Engine boundary keeps UI independent from FreeRDP/JNI implementation details. */
interface InfinityConnectEngine {
    suspend fun connect(target: InfinityConnectTarget, eventSink: (InfinityConnectEngineEvent) -> Unit)
    suspend fun disconnect()
    val isConnected: Boolean
}

/** UI-facing factory; production RDP engine must replace this placeholder factory. */
fun interface InfinityConnectEngineFactory {
    fun create(): InfinityConnectEngine
}
