/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior.utils

import kotlinx.serialization.json.jsonObject
import org.fxboomk.fcitx5.android.data.theme.Theme
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.PunctuationPosition
import org.fxboomk.fcitx5.android.input.keyboard.*
import org.junit.Assert.*
import org.junit.Test

class DirectionalSwipeMigrationTest {
    private val types = listOf("CapsKey", "LayoutSwitchKey", "SymbolKey", "ReturnKey", "BackspaceKey")
    private val up = MacroAction(listOf(MacroStep.Text("up")))
    private val down = MacroAction(listOf(MacroStep.Text("down")))

    private val testTheme = Theme.Builtin(
        name = "test",
        isDark = false,
        backgroundColor = 0xFFE7E7E7,
        barColor = 0xFFDDDDDD,
        keyboardColor = 0xFFF5F5F5,
        keyBackgroundColor = 0xFFFFFFFF,
        keyTextColor = 0xFF222222,
        candidateTextColor = 0xFF222222,
        candidateLabelColor = 0xFF888888,
        candidateCommentColor = 0xFF888888,
        altKeyBackgroundColor = 0xFFCCCCCC,
        altKeyTextColor = 0xFF222222,
        accentKeyBackgroundColor = 0xFF3D9AB0,
        accentKeyTextColor = 0xFFFFFFFF,
        keyPressHighlightColor = 0xFFB3E5FC,
        keyShadowColor = 0x00000000,
        popupBackgroundColor = 0xFFFFFFFF,
        popupTextColor = 0xFF222222,
        spaceBarColor = 0xFFDDDDDD,
        dividerColor = 0xFFBBBBBB,
        clipboardEntryColor = 0xFFEEEEEE,
        genericActiveBackgroundColor = 0xFFB3E5FC,
        genericActiveForegroundColor = 0xFF222222
    )

    private fun create(fields: Map<String, Any?>, position: PunctuationPosition = PunctuationPosition.Top): KeyDef {
        val parsed = LayoutJsonUtils.parseKeyJson(LayoutJsonUtils.convertToJsonProperty(fields).jsonObject)!!
        return LayoutJsonUtils.createKeyDef(parsed, theme = testTheme, punctuationPosition = position)
    }

    private fun labels(key: KeyDef): Pair<String, String?> = when (val appearance = key.appearance) {
        is KeyDef.Appearance.AltText -> appearance.altText to appearance.altText1
        is KeyDef.Appearance.ImageAltText -> appearance.altText to appearance.altText1
        else -> "" to null
    }

    @Test
    fun legacyEventsAndLabelsMigrateForEveryKeyAndPosition() {
        for (type in types) for (position in PunctuationPosition.entries) {
            val legacy = mapOf("type" to type, "label" to "K", "swipe" to LayoutJsonUtils.macroActionToJson(up), "swipeLabel" to "legacy")
            val key = create(legacy, position)
            val swipe = key.behaviors.filterIsInstance<KeyDef.Behavior.Swipe>().single()
            val toDown = position == PunctuationPosition.Bottom
            assertEquals("$type $position up", if (toDown) null else up, swipe.upMacro)
            assertEquals("$type $position down", if (toDown) up else null, swipe.downMacro)
            assertNull(swipe.action)
            assertNull(swipe.downAction)
            assertNull(swipe.legacyMacro)
            assertEquals(if (toDown) "" to "legacy" else "legacy" to null, labels(key))
            assertTrue(key.appearance.directionalSwipeLabels)
            val saved = LayoutJsonUtils.keyDefToJson(key)
            assertFalse(saved.containsKey("swipe"))
            assertFalse(saved.containsKey("swipeLabel"))
            val reloaded = create(saved, if (toDown) PunctuationPosition.Top else PunctuationPosition.Bottom)
            assertEquals(labels(key), labels(reloaded))
            val reloadedSwipe = reloaded.behaviors.filterIsInstance<KeyDef.Behavior.Swipe>().single()
            assertEquals(swipe.upMacro, reloadedSwipe.upMacro)
            assertEquals(swipe.downMacro, reloadedSwipe.downMacro)
        }
    }

    @Test
    fun newDirectionsRoundTripIndependentlyAndDoNotUseLabelsAsActions() {
        for (type in types) for ((upAction, downAction) in listOf(up to down, up to null, null to down)) {
            val fields = mutableMapOf<String, Any?>("type" to type, "label" to "K", "swipeUpLabel" to "U", "swipeDownLabel" to "D")
            upAction?.let { fields["swipeUp"] = LayoutJsonUtils.macroActionToJson(it) }
            downAction?.let { fields["swipeDown"] = LayoutJsonUtils.macroActionToJson(it) }
            val key = create(fields)
            val restored = create(LayoutJsonUtils.keyDefToJson(key), PunctuationPosition.Bottom)
            val swipe = restored.behaviors.filterIsInstance<KeyDef.Behavior.Swipe>().single()
            assertEquals(upAction, swipe.upMacro)
            assertEquals(downAction, swipe.downMacro)
            assertNull(swipe.action)
            assertNull(swipe.downAction)
            assertEquals("U" to "D", labels(restored))
        }
    }

    @Test
    fun labelsAloneDoNotAddSwipeOrChangePressRepeatActions() {
        for (type in types) {
            val plain = create(mapOf("type" to type, "label" to "K"))
            val labeled = create(mapOf("type" to type, "label" to "K", "swipeDownLabel" to "D"))
            assertEquals(plain.behaviors.map { it.javaClass }, labeled.behaviors.map { it.javaClass })
            assertTrue(labeled.behaviors.none { it is KeyDef.Behavior.Swipe })
            assertEquals(plain.behaviors.filterIsInstance<KeyDef.Behavior.Press>().single().action,
                labeled.behaviors.filterIsInstance<KeyDef.Behavior.Press>().single().action)
            assertEquals("" to "D", labels(labeled))
            if (type == "BackspaceKey") assertEquals(1, labeled.behaviors.filterIsInstance<KeyDef.Behavior.Repeat>().size)
        }
    }

    @Test
    fun migrationIsIdempotentAndPreservesNewFieldFamiliesIncludingClearedValues() {
        for (type in types) {
            val legacy = mapOf<String, Any?>("type" to type, "swipe" to "old", "swipeLabel" to "old label", "swipeUp" to null, "swipeDownLabel" to "", "weight" to 0.2f)
            val migrated = LayoutJsonUtils.migrateDirectionalSwipeFields(legacy, PunctuationPosition.Bottom)
            assertEquals(mapOf("type" to type, "swipeUp" to null, "swipeDownLabel" to "", "weight" to 0.2f), migrated)
            assertEquals(migrated, LayoutJsonUtils.migrateDirectionalSwipeFields(migrated, PunctuationPosition.Top))
            assertTrue(legacy.containsKey("swipe"))
            val parsed = create(legacy - "swipe" + ("swipe" to LayoutJsonUtils.macroActionToJson(up)))
            assertTrue(parsed.behaviors.none { it is KeyDef.Behavior.Swipe })
        }
    }

    @Test
    fun eventAndLabelMigrationAreIndependent() {
        for (type in types) {
            val fields = mapOf("type" to type, "swipe" to "old", "swipeUpLabel" to "new")
            assertEquals(mapOf("type" to type, "swipeDown" to "old", "swipeUpLabel" to "new"),
                LayoutJsonUtils.migrateDirectionalSwipeFields(fields, PunctuationPosition.Bottom))
            val reverse = mapOf("type" to type, "swipeDown" to "new", "swipeLabel" to "old")
            assertEquals(mapOf("type" to type, "swipeDown" to "new", "swipeUpLabel" to "old"),
                LayoutJsonUtils.migrateDirectionalSwipeFields(reverse, PunctuationPosition.TopRight))
        }
    }

    @Test
    fun composingOverridesMigrateAndRoundTripWithoutLosingTheirIndependentActions() {
        for (type in types) {
            val fields = mapOf("type" to type, "label" to "base", "swipeUp" to LayoutJsonUtils.macroActionToJson(up),
                "composeOverride" to mapOf("label" to "composing", "swipe" to LayoutJsonUtils.macroActionToJson(down), "swipeLabel" to "D"))
            val migrated = LayoutJsonUtils.migrateDirectionalSwipeFields(fields, PunctuationPosition.Bottom)
            val override = migrated["composeOverride"] as Map<*, *>
            assertFalse(override.containsKey("type"))
            assertEquals("D", override["swipeDownLabel"])
            val key = create(fields, PunctuationPosition.Bottom)
            val restored = create(LayoutJsonUtils.keyDefToJson(key))
            val swipe = restored.composeOverride!!.behaviors.filterIsInstance<KeyDef.Behavior.Swipe>().single()
            assertNull(swipe.upMacro)
            assertEquals(down, swipe.downMacro)
            assertEquals("" to "D", labels(restored.composeOverride!!))
        }
    }

    @Test
    fun unrelatedKeyTypesKeepTheirLegacyFields() {
        for (type in listOf("MacroKey", "AlphabetKey", "SpaceKey")) {
            val fields = mapOf("type" to type, "swipe" to "legacy", "swipeLabel" to "label")
            assertEquals(fields, LayoutJsonUtils.migrateDirectionalSwipeFields(fields, PunctuationPosition.Bottom))
        }
    }
}
