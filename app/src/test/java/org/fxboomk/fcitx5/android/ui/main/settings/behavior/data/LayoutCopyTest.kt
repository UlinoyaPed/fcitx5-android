/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.fxboomk.fcitx5.android.input.keyboard.keyboardHeightPercentOverride
import org.fxboomk.fcitx5.android.input.keyboard.resolveTextKeyboardLayout
import org.junit.Assert.*
import org.junit.Test

class LayoutCopyTest {
    private val factory = Json.parseToJsonElement("""[[{"type":"SpaceKey"}]]""") as JsonArray
    private val heights = LayoutHeightPercentOverrides(33, 35)
    private fun profile(text: String) = Json.parseToJsonElement(text) as JsonObject
    private fun resolved(json: JsonObject, key: LayoutCopyAddress) = checkNotNull(
        resolveTextKeyboardLayout(json, key.inputMethod, key.displayName, key.subModeLabel, key.subModeName)
    )

    @Test fun copiesEffectiveChildLabelsGeometryAndBehaviorToMultipleParents() {
        val document = profile("""
            {
              "__profile__":{"__meta__":{"keyboard_height_percent_landscape":51}},
              "rime":{
                "__meta__":{"keyboard_height_percent":44},
                "default":[{"heightMultiplier":1.2,"keys":[
                  {"type":"AlphabetKey","main":"A","displayText":{"schema":"訂"},
                   "swipeUp":[],"composeOverride":{"type":"AlphabetKey","displayText":{"schema":"合"}}}
                ]}],
                "schema":{"__meta__":{"keyboard_height_percent":49}}
              },
              "anthy":{"default":[[{"type":"ReturnKey"}]],"Kana":[[{"type":"CommaKey"}]]}
            }
        """.trimIndent())
        val source = LayoutCopyAddress("rime", subModeLabel = "schema")
        val japanese = LayoutCopyAddress("anthy")
        val english = LayoutCopyAddress("keyboard-us")
        val plan = planLayoutCopy(document, source, listOf(japanese, english), factory, heights)
        assertEquals(setOf(japanese), plan.overwritten)
        val saved = profile(plan.profile.toString())
        for (target in listOf(japanese, english)) {
            val result = resolved(saved, target)
            assertEquals(49, result.keyboardHeightPercentOverride(false))
            assertEquals(51, result.keyboardHeightPercentOverride(true))
            val row = result.rows!![0].toString()
            assertTrue(row.contains("訂") && row.contains("合") && row.contains("\"swipeUp\":[]"))
            assertTrue(row.contains("heightMultiplier"))
        }
        assertEquals(document["rime"], saved["rime"])
        assertEquals((document["anthy"] as JsonObject)["Kana"], (saved["anthy"] as JsonObject)["Kana"])

        val edited = planLayoutCopy(saved, LayoutCopyAddress("unconfigured"), listOf(japanese), factory, heights)
        assertEquals(saved["keyboard-us"], edited.profile["keyboard-us"])
        assertNotEquals(saved["anthy"], edited.profile["anthy"])
    }

    @Test fun legacyDestinationAndSiblingModeKeepTheirDataAfterCopyingToOneChild() {
        val document = profile("""{
            "pinyin":[[{"type":"ReturnKey"}]],
            "Japanese":{"__meta__":{"keyboard_height_percent":47},
                        "default":[[{"type":"SpaceKey"}]],
                        "Hiragana":[[{"type":"CommaKey"}]],
                        "Katakana":[[{"type":"SymbolKey"}]]}
        }""")
        val parent = LayoutCopyAddress("anthy", "Japanese")
        val selected = parent.copy(subModeLabel = "仮名", subModeName = "Hiragana")
        val plan = planLayoutCopy(document, LayoutCopyAddress("pinyin"), listOf(selected), factory, heights)
        assertEquals(setOf(selected), plan.overwritten)
        assertEquals(resolved(document, parent).rows, resolved(plan.profile, parent).rows)
        val sibling = parent.copy(subModeLabel = "Katakana")
        assertEquals(resolved(document, sibling).rows, resolved(plan.profile, sibling).rows)
        assertEquals(resolved(document, LayoutCopyAddress("pinyin")).rows, resolved(plan.profile, selected).rows)
        assertEquals(47, resolved(plan.profile, parent).keyboardHeightPercentOverride(false))
    }

    @Test fun newSubModeDoesNotRedirectOtherModesToTheCustomizedEnglishLayout() {
        val document = profile("""{"default":[[{"type":"ReturnKey"}]],"pinyin":[[{"type":"CommaKey"}]]}""")
        val target = LayoutCopyAddress("anthy", subModeLabel = "Kana")
        val plan = planLayoutCopy(document, LayoutCopyAddress("pinyin"), listOf(target), factory, heights)
        assertTrue(plan.overwritten.isEmpty())
        assertEquals(factory, resolved(plan.profile, target.parent()).rows)
        assertEquals(document["default"], plan.profile["default"])
    }

    @Test fun rejectsSelfCopyReservedModesAndDuplicateAliasesWithoutPartialWrites() {
        val document = profile("""{"pinyin":[[{"type":"SpaceKey"}]],"anthy":{"Kana":[[{"type":"CommaKey"}]]}}""")
        val source = LayoutCopyAddress("pinyin")
        val child = LayoutCopyAddress("anthy", subModeLabel = "Kana")
        for (targets in listOf(
            listOf(source),
            listOf(child.copy(subModeLabel = "__meta__")),
            listOf(child, child.copy(subModeLabel = "仮名", subModeName = "Kana")),
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                planLayoutCopy(document, source, targets, factory, heights)
            }
        }
        assertEquals(profile(document.toString()), document)
    }
}
