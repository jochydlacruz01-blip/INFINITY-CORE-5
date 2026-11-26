package com.infinity.core

/**
 * Single-slot mailbox for already-decoded remote frames.
 *
 * Only one main-thread drain is scheduled at a time. New frames replace pending
 * frames while the UI is busy, preventing an unbounded Handler queue and favoring
 * freshness over replaying stale desktop images.
 */
internal class InfinityLatestFrameMailbox<T : Any> {
    private var latest: T? = null
    private var drainScheduled = false

    /** Returns true only when the caller must schedule a main-thread drain. */
    @Synchronized
    fun offer(frame: T): Boolean {
        latest = frame
        if (drainScheduled) return false
        drainScheduled = true
        return true
    }

    /** Takes the freshest pending frame and allows the next offer to schedule a drain. */
    @Synchronized
    fun takeLatest(): T? {
        val frame = latest
        latest = null
        drainScheduled = false
        return frame
    }

    @Synchronized
    fun clear() {
        latest = null
        drainScheduled = false
    }
}
