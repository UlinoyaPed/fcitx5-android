/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.PunctuationPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectionalSwipeMigrationTest {
    private val types = listOf("CapsKey", "LayoutSwitchKey", "SymbolKey", "ReturnKey", "BackspaceKey")
    private val action = Json.parseToJsonElement("""{"macro":[{"type":"text","text":"legacy"}]}""")

    @Test
    fun allSpecialKeysMigrateOnceForEveryPunctuationPosition() {
        val keys = JsonArray(types.map { type ->
            buildJsonObject {
                put("type", JsonPrimitive(type))
                put("swipe", action)
                put("swipeLabel", JsonPrimitive("label"))
            }
        })
        // A layout contains rows, each of which contains key objects.
        val layout = JsonObject(mapOf("default" to JsonArray(listOf(keys))))
        for (position in PunctuationPosition.entries) {
            val direction = if (position == PunctuationPosition.Bottom) "swipeDown" else "swipeUp"
            val migrated = DirectionalSwipeMigration.migrateJson(layout, position)
            migrated["default"]!!.jsonArray[0].jsonArray.forEach { key ->
                assertEquals(action, key.jsonObject[direction])
                assertEquals(JsonPrimitive("label"), key.jsonObject["${direction}Label"])
                assertFalse(key.jsonObject.containsKey("swipe"))
                assertFalse(key.jsonObject.containsKey("swipeLabel"))
            }
            assertEquals(migrated, DirectionalSwipeMigration.migrateJson(migrated, PunctuationPosition.Bottom))
            assertEquals(migrated, DirectionalSwipeMigration.migrateJson(migrated, PunctuationPosition.Top))
        }
    }

    @Test
    fun nestedLayoutsAndRowsPreserveMetadataUnknownFieldsAndNumericPrecision() {
        val root = Json.parseToJsonElement("""
            {
              "__profile__": {"__meta__": {"huge": 9223372036854775807123}},
              "default": [[{"__rowMeta":true,"type":"CapsKey","swipeLabel":"metadata"}]],
              "rime": {
                "__meta__": {"custom":{"type":"CapsKey","swipeLabel":"metadata"}},
                "default": [{"heightMultiplier":1.234567890123456789,"futureRow":42,"keys":[
                  {"type":"ReturnKey","weight":0.1234567890123456789,"future":9223372036854775807123,
                   "unknown":{"type":"CapsKey","swipeLabel":"opaque"},"swipeLabel":"return",
                   "composeOverride":{"swipeLabel":"composing","swipe":{"macro":[]}}}
                ]}],
                "schema": {"__meta__":{"untouched":true},"futureSubmode":98765432109876543210,
                  "default":[[{"type":"BackspaceKey","swipeLabel":"backspace"}]]}
              }
            }
        """.trimIndent()).jsonObject
        val migrated = DirectionalSwipeMigration.migrateJson(root, PunctuationPosition.Bottom)
        assertEquals(root["__profile__"], migrated["__profile__"])
        assertEquals(root["default"], migrated["default"])
        val originalLayout = root["rime"]!!.jsonObject
        val newLayout = migrated["rime"]!!.jsonObject
        assertEquals(originalLayout["__meta__"], newLayout["__meta__"])
        val originalRow = originalLayout["default"]!!.jsonArray[0].jsonObject
        val newRow = newLayout["default"]!!.jsonArray[0].jsonObject
        assertEquals(originalRow - "keys", newRow - "keys")
        val originalKey = originalRow["keys"]!!.jsonArray[0].jsonObject
        val newKey = newRow["keys"]!!.jsonArray[0].jsonObject
        for (field in listOf("weight", "future", "unknown")) assertEquals(originalKey[field], newKey[field])
        assertEquals(JsonPrimitive("return"), newKey["swipeDownLabel"])
        val compose = newKey["composeOverride"]!!.jsonObject
        assertEquals(JsonPrimitive("composing"), compose["swipeDownLabel"])
        assertTrue(compose.containsKey("swipeDown"))
        assertFalse(compose.containsKey("type"))
        assertFalse(compose.containsKey("swipe"))
        val schema = newLayout["schema"]!!.jsonObject
        assertEquals(originalLayout["schema"]!!.jsonObject - "default", schema - "default")
        assertEquals(JsonPrimitive("backspace"), schema["default"]!!.jsonArray[0].jsonArray[0].jsonObject["swipeDownLabel"])
    }

    @Test
    fun explicitEmptyDirectionsWinIndependentlyAndOtherKeyTypesAreUnchanged() {
        val root = Json.parseToJsonElement("""
            {"default":[[
              {"type":"CapsKey","swipeUp":null,"swipe":{"macro":[]},"swipeLabel":"old"},
              {"type":"SymbolKey","swipeDownLabel":"","swipeLabel":"old","swipe":{"macro":[]}},
              {"type":"MacroKey","swipeLabel":"macro","swipe":{"macro":[]}},
              {"type":"ReturnKey","composeOverride":{"type":null,"swipeLabel":"keep"}}
            ]]}
        """).jsonObject
        val migrated = DirectionalSwipeMigration.migrateJson(root, PunctuationPosition.Bottom)
        val keys = migrated["default"]!!.jsonArray[0].jsonArray
        assertEquals(JsonNull, keys[0].jsonObject["swipeUp"])
        assertFalse(keys[0].jsonObject.containsKey("swipeDown"))
        assertEquals(JsonPrimitive("old"), keys[0].jsonObject["swipeDownLabel"])
        assertEquals(JsonPrimitive(""), keys[1].jsonObject["swipeDownLabel"])
        assertTrue(keys[1].jsonObject.containsKey("swipeDown"))
        assertEquals(root["default"]!!.jsonArray[0].jsonArray[2], keys[2])
        assertEquals(root["default"]!!.jsonArray[0].jsonArray[3], keys[3])
    }

    @Test
    fun noMigrationKeepsOriginalWhitespaceAndComments() {
        val original = """
            // User formatting should remain untouched.
            { "default" : [[ {"type":"CapsKey", "swipeUpLabel":"up"} ]] }
        """.trimIndent() + "\n\n"
        assertEquals(original, DirectionalSwipeMigration.migrateText(original, PunctuationPosition.Bottom))
    }
}
