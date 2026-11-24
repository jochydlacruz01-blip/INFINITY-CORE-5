package com.freerdp.freerdpcore.application

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.freerdp.freerdpcore.domain.BookmarkBase
import com.freerdp.freerdpcore.services.LibFreeRDP
import java.util.Collections
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal INFINITY-owned FreeRDP application bridge.
 * Deliberately omits the sample app's global theme, print monitor and exported UI behavior.
 */
class GlobalApp : Application(), LibFreeRDP.EventListener {
    interface SessionEventListener {
        fun onConnectionSuccess()
        fun onConnectionFailure()
        fun onDisconnected()
    }

    override fun onCreate() {
        super.onCreate()
        application = this
    }

    fun startDisconnectTimer() {
        // INFINITY owns foreground/background session policy.
    }

    private fun dispatch(instance: Long, action: (SessionEventListener) -> Unit) {
        val listener = sessionListeners[instance] ?: return
        mainHandler.post { action(listener) }
    }

    override fun OnPreConnect(instance: Long) = Unit

    override fun OnConnectionSuccess(instance: Long) =
        dispatch(instance) { it.onConnectionSuccess() }

    override fun OnConnectionFailure(instance: Long) =
        dispatch(instance) { it.onConnectionFailure() }

    override fun OnDisconnecting(instance: Long) = Unit

    override fun OnDisconnected(instance: Long) =
        dispatch(instance) { it.onDisconnected() }

    companion object {
        private var application: GlobalApp? = null
        private val sessions = Collections.synchronizedMap(HashMap<Long, SessionState>())
        private val sessionListeners = ConcurrentHashMap<Long, SessionEventListener>()
        private val mainHandler = Handler(Looper.getMainLooper())

        @JvmStatic
        fun registerSessionListener(instance: Long, listener: SessionEventListener) {
            sessionListeners[instance] = listener
        }

        @JvmStatic
        fun unregisterSessionListener(instance: Long) {
            sessionListeners.remove(instance)
        }

        @JvmStatic
        fun cancelDisconnectTimer() = Unit

        @JvmStatic
        fun createSession(bookmark: BookmarkBase, context: Context): SessionState {
            val app = application ?: error("INFINITY application has not been initialized.")
            LibFreeRDP.setEventListener(app)
            val session = SessionState(LibFreeRDP.newInstance(context), bookmark)
            sessions[session.instance] = session
            return session
        }

        @JvmStatic
        fun createSession(openUri: Uri, context: Context): SessionState {
            val app = application ?: error("INFINITY application has not been initialized.")
            LibFreeRDP.setEventListener(app)
            val session = SessionState(LibFreeRDP.newInstance(context), openUri)
            sessions[session.instance] = session
            return session
        }

        @JvmStatic
        fun getSession(instance: Long): SessionState? = sessions[instance]

        @JvmStatic
        fun getSessions(): Collection<SessionState> = synchronized(sessions) {
            ArrayList(sessions.values)
        }

        @JvmStatic
        fun freeSession(instance: Long) {
            val removed = sessions.remove(instance) ?: return
            sessionListeners.remove(instance)
            LibFreeRDP.freeInstance(removed.instance)
        }
    }
}
