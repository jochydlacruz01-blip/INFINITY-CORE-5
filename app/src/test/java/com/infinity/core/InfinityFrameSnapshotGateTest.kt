package com.infinity.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityFrameSnapshotGateTest {
    @Test fun coalescesFrameUpdatesUntilSnapshotCompletes() {
        val gate = InfinityFrameSnapshotGate()
        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
        gate.release()
        assertTrue(gate.tryAcquire())
    }

    @Test fun releaseIsIdempotentAndAllowsTheNextSnapshot() {
        val gate = InfinityFrameSnapshotGate()
        gate.release()
        gate.release()
        assertTrue(gate.tryAcquire())
    }
}
