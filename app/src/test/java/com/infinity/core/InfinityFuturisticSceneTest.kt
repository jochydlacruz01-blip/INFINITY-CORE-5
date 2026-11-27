package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class InfinityFuturisticSceneTest {
    @Test
    fun selectedBackgroundPresetsProduceDistinctSceneTints() {
        val nebula = infinityFuturisticSceneTint("nebula")
        val interstellar = infinityFuturisticSceneTint("interstellar")
        val punk = infinityFuturisticSceneTint("punk")
        val aurora = infinityFuturisticSceneTint("aurora")

        assertNotEquals(nebula, interstellar)
        assertNotEquals(nebula, punk)
        assertNotEquals(nebula, aurora)
        assertNotEquals(punk, aurora)
    }

    @Test
    fun unknownPresetFallsBackToNebulaTint() {
        assertEquals(infinityFuturisticSceneTint("nebula"), infinityFuturisticSceneTint("unknown"))
    }
}
