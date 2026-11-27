package com.infinity.core

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Allows at most one delayed remote-frame snapshot task at a time.
 * Native rectangle updates are coalesced until the current snapshot is released.
 */
internal class InfinityFrameSnapshotGate {
    private val pending = AtomicBoolean(false)

    fun tryAcquire(): Boolean = pending.compareAndSet(false, true)

    fun release() {
        pending.set(false)
    }
}
