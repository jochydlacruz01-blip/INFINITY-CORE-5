package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityConnectKeyboardRulesTest {
 @Test fun tapReleasesKey() {
  assertEquals(listOf(true,false), InfinityConnectKeyboardRules.tap(ConnectKey.ESC).events.map { it.pressed })
 }
 @Test fun chordPressesAndReleasesInReverseOrder() {
  val events=InfinityConnectKeyboardRules.chord(listOf(ConnectKey.CTRL,ConnectKey.SHIFT),ConnectKey.F1).events
  assertEquals(listOf(ConnectKey.CTRL,ConnectKey.SHIFT,ConnectKey.F1,ConnectKey.F1,ConnectKey.SHIFT,ConnectKey.CTRL),events.map { it.key })
  assertEquals(listOf(true,true,true,false,false,false),events.map { it.pressed })
 }
 @Test(expected=IllegalArgumentException::class) fun duplicateModifierRejected() {
  InfinityConnectKeyboardRules.chord(listOf(ConnectKey.CTRL,ConnectKey.CTRL),ConnectKey.F1)
 }
 @Test(expected=IllegalArgumentException::class) fun mainKeyCannotBeModifier() {
  InfinityConnectKeyboardRules.chord(listOf(ConnectKey.CTRL),ConnectKey.CTRL)
 }
}
