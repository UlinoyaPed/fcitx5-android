/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.fxboomk.fcitx5.android.core.InputMethodEntry
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextKeyboardLayoutResolverTest {

    @Test
    fun previewInputMethodUpdateDoesNotChangeRealKeyboardLayoutState() {
        val realIme = InputMethodEntry("real")
        val previewIme = InputMethodEntry("preview")
        val realState = TextKeyboardLayoutState(realIme)
        val previewState = TextKeyboardLayoutState(realIme)

        previewState.ime = previewIme

        assertEquals(realIme, realState.ime)
        assertEquals(previewIme, previewState.ime)
    }

    @Test
    fun keyDefLayoutCacheKey_changesWithThemeColors() {
        val lightKey = TextKeyboard.KeyDefLayoutCacheKey(
            sourceKey = "default",
            subModeLabel = "",
            showLangSwitch = true,
            theme = ThemePreset.MaterialLight,
        )
        val darkKey = TextKeyboard.KeyDefLayoutCacheKey(
            sourceKey = "default",
            subModeLabel = "",
            showLangSwitch = true,
            theme = ThemePreset.MaterialDark,
        )

        assertNotEquals(lightKey, darkKey)
    }

    @Test
    fun metadataBearingDefaultLayoutProvidesRowsAndOrientationSpecificHeights() {
        val resolution = resolveTextKeyboardLayout(
            json = layoutJson(),
            uniqueName = "pinyin",
            displayName = "Pinyin",
            subModeLabel = "",
        )

        assertNotNull(resolution)
        assertEquals("default", resolution!!.sourceKey)
        assertTrue(resolution.rows is JsonArray)
        assertEquals(34, resolution.keyboardHeightPercentOverride(landscape = false))
        assertEquals(49, resolution.keyboardHeightPercentOverride(landscape = true))
    }

    @Test
    fun exactLayoutAndSubModeHeightOverrideDefaultLayoutHeight() {
        val resolution = resolveTextKeyboardLayout(
            json = Json.parseToJsonElement(
                """
                {
                  "default": {
                    "__meta__": {"keyboard_height_percent": 34},
                    "default": [[{"type": "AlphabetKey", "main": "q"}]]
                  },
                  "pinyin": {
                    "__meta__": {"keyboard_height_percent": 40},
                    "double_pinyin": {
                      "__meta__": {"keyboard_height_percent": 46},
                      "rows": [[{"type": "AlphabetKey", "main": "w"}]]
                    },
                    "default": [[{"type": "AlphabetKey", "main": "e"}]]
                  }
                }
                """.trimIndent()
            ) as JsonObject,
            uniqueName = "pinyin",
            displayName = "Pinyin",
            subModeLabel = "double_pinyin",
        )

        assertNotNull(resolution)
        assertEquals("pinyin", resolution!!.sourceKey)
        assertTrue(resolution.rows is JsonArray)
        assertEquals(46, resolution.keyboardHeightPercentOverride(landscape = false))
    }

    @Test
    fun malformedExplicitBaseFallsBackToGlobalRowsButNotGlobalHeight() {
        for (baseKey in listOf("default", "")) {
            val json = Json.parseToJsonElement(
                """
                {
                  "default": {
                    "__meta__": {"keyboard_height_percent": 34, "keyboard_height_percent_landscape": 49},
                    "default": [[{"type": "AlphabetKey", "main": "g"}]]
                  },
                  "rime": {"$baseKey": {"rows": "invalid"}}
                }
                """.trimIndent()
            ) as JsonObject
            val resolution = requireNotNull(resolveTextKeyboardLayout(json, "rime", "Rime", "A"))
            assertEquals("default", resolution.sourceKey)
            assertEquals("g", resolution.rows!!.firstKeyMain())
            assertNull(resolution.keyboardHeightPercentOverride(landscape = false))
            assertNull(resolution.keyboardHeightPercentOverride(landscape = true))
        }
    }

    @Test
    fun childOnlyLayoutUsesExactRowsAndInheritsGlobalDefaults() {
        val exactResolution = resolveTextKeyboardLayout(
            json = childOnlyLayoutJson(),
            uniqueName = "rime",
            displayName = "Rime",
            subModeLabel = "A",
        )
        val missingResolution = resolveTextKeyboardLayout(
            json = childOnlyLayoutJson(),
            uniqueName = "rime",
            displayName = "Rime",
            subModeLabel = "B",
        )

        assertNotNull(exactResolution)
        assertEquals("rime", exactResolution!!.sourceKey)
        assertEquals("a", exactResolution.rows!!.firstKeyMain())
        assertEquals(34, exactResolution.keyboardHeightPercentOverride(landscape = false))
        assertEquals(49, exactResolution.keyboardHeightPercentOverride(landscape = true))
        assertNotNull(missingResolution)
        assertEquals("default", missingResolution!!.sourceKey)
        assertEquals("g", missingResolution.rows!!.firstKeyMain())
        assertEquals(34, missingResolution.keyboardHeightPercentOverride(landscape = false))
        assertEquals(49, missingResolution.keyboardHeightPercentOverride(landscape = true))
    }

    @Test
    fun exactChildRowsInheritMissingOrientationMetadataByPriority() {
        val resolution = resolveTextKeyboardLayout(
            json = Json.parseToJsonElement(
                """
                {
                  "default": {
                    "__meta__": {
                      "keyboard_height_percent": 34,
                      "keyboard_height_percent_landscape": 49
                    },
                    "default": [[{"type": "AlphabetKey", "main": "g"}]]
                  },
                  "rime": {
                    "__meta__": {"keyboard_height_percent": 40},
                    "A": {
                      "__meta__": {"keyboard_height_percent_landscape": 55},
                      "default": [[{"type": "AlphabetKey", "main": "a"}]]
                    }
                  }
                }
                """.trimIndent()
            ) as JsonObject,
            uniqueName = "rime",
            displayName = "Rime",
            subModeLabel = "A",
        )

        assertNotNull(resolution)
        assertEquals("rime", resolution!!.sourceKey)
        assertEquals("a", resolution.rows!!.firstKeyMain())
        assertEquals(40, resolution.keyboardHeightPercentOverride(landscape = false))
        assertEquals(55, resolution.keyboardHeightPercentOverride(landscape = true))
    }

    @Test
    fun imeDefaultRowsTakePriorityOverGlobalDefault() {
        val resolution = resolveTextKeyboardLayout(
            json = Json.parseToJsonElement(
                """
                {
                  "default": [[{"type": "AlphabetKey", "main": "g"}]],
                  "rime": {
                    "A": [[{"type": "AlphabetKey", "main": "a"}]],
                    "default": [[{"type": "AlphabetKey", "main": "i"}]]
                  }
                }
                """.trimIndent()
            ) as JsonObject,
            uniqueName = "rime",
            displayName = "Rime",
            subModeLabel = "B",
        )

        assertNotNull(resolution)
        assertEquals("rime", resolution!!.sourceKey)
        assertEquals("i", resolution.rows!!.firstKeyMain())
    }

    @Test
    fun exactBaseWithoutHeightDoesNotInheritGlobalHeight() {
        val resolution = resolveTextKeyboardLayout(
            json = Json.parseToJsonElement(
                """
                {
                  "default": {
                    "__meta__": {
                      "keyboard_height_percent": 34,
                      "keyboard_height_percent_landscape": 49
                    },
                    "default": [[{"type": "AlphabetKey", "main": "g"}]]
                  },
                  "rime": {
                    "default": [[{"type": "AlphabetKey", "main": "i"}]]
                  }
                }
                """.trimIndent()
            ) as JsonObject,
            uniqueName = "rime",
            displayName = "Rime",
            subModeLabel = "B",
        )

        assertNotNull(resolution)
        assertEquals("i", resolution!!.rows!!.firstKeyMain())
        assertNull(resolution.keyboardHeightPercentOverride(landscape = false))
        assertNull(resolution.keyboardHeightPercentOverride(landscape = true))
    }

    @Test
    fun unmatchedSubModeWithoutAnyDefaultStillReturnsResolutionWithoutRows() {
        val resolution = resolveTextKeyboardLayout(
            json = Json.parseToJsonElement(
                """
                {
                  "rime": {
                    "A": [[{"type": "AlphabetKey", "main": "a"}]]
                  }
                }
                """.trimIndent()
            ) as JsonObject,
            uniqueName = "rime",
            displayName = "Rime",
            subModeLabel = "B",
        )

        assertNotNull(resolution)
        assertEquals("rime", resolution!!.sourceKey)
        assertNull(resolution.rows)
    }

    @Test
    fun legacyTopLevelArrayAndDefaultObjectRemainSupported() {
        val arrayResolution = resolveTextKeyboardLayout(
            json = Json.parseToJsonElement(
                """{"rime": [[{"type": "AlphabetKey", "main": "a"}]]}"""
            ) as JsonObject,
            uniqueName = "rime",
            displayName = "Rime",
            subModeLabel = "B",
        )
        val objectResolution = resolveTextKeyboardLayout(
            json = layoutJson(),
            uniqueName = "pinyin",
            displayName = "Pinyin",
            subModeLabel = "B",
        )

        assertEquals("a", arrayResolution!!.rows!!.firstKeyMain())
        assertEquals("q", objectResolution!!.rows!!.firstKeyMain())
        assertEquals(34, objectResolution.keyboardHeightPercentOverride(landscape = false))
        assertEquals(49, objectResolution.keyboardHeightPercentOverride(landscape = true))
    }

    private fun layoutJson(): JsonObject {
        return Json.parseToJsonElement(
            """
            {
              "default": {
                "__meta__": {
                  "keyboard_height_percent": 34,
                  "keyboard_height_percent_landscape": 49
                },
                "default": [[{"type": "AlphabetKey", "main": "q"}]]
              }
            }
            """.trimIndent()
        ) as JsonObject
    }

    private fun childOnlyLayoutJson(): JsonObject {
        return Json.parseToJsonElement(
            """
            {
              "default": {
                "__meta__": {
                  "keyboard_height_percent": 34,
                  "keyboard_height_percent_landscape": 49
                },
                "default": [[{"type": "AlphabetKey", "main": "g"}]]
              },
              "rime": {
                "A": [[{"type": "AlphabetKey", "main": "a"}]]
              }
            }
            """.trimIndent()
        ) as JsonObject
    }

    private fun JsonArray.firstKeyMain(): String {
        return this[0].jsonArray[0].jsonObject.getValue("main").jsonPrimitive.content
    }
}
