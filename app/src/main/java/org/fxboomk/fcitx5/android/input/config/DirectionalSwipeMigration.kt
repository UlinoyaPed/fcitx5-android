/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.config

import android.util.AtomicFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.PunctuationPosition
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.utils.KeyboardRowStyleUtils
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.utils.LayoutJsonUtils
import java.io.File

/** Freezes legacy swipe directions in the layout file before preferences can change. */
object DirectionalSwipeMigration {
    private val json = Json {
        isLenient = true
        prettyPrint = true
    }

    /** Leaves the original bytes and modification time alone when no fields need migration. */
    @Synchronized
    fun migrateFile(file: File?, position: PunctuationPosition): Result<Boolean> = runCatching {
        if (file == null || (!file.exists() && !File(file.path + ".bak").exists())) {
            return@runCatching false
        }
        val atomicFile = AtomicFile(file)
        val original = atomicFile.openRead().bufferedReader().use { it.readText() }
        if (original.isBlank()) return@runCatching false
        val migrated = migrateText(original, position)
        if (migrated == original) return@runCatching false
        val output = atomicFile.startWrite()
        try {
            output.write(migrated.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
        check(atomicFile.openRead().bufferedReader().use { it.readText() } == migrated) {
            "Atomic swipe migration did not commit: ${file.name}"
        }
        true
    }

    fun migrateText(content: String, position: PunctuationPosition): String {
        val parsed = json.parseToJsonElement(LayoutJsonUtils.removeJsonComments(content))
        val root = parsed as? JsonObject ?: return content
        val migrated = migrateJson(root, position)
        return if (migrated == root) content else json.encodeToString(JsonObject.serializer(), migrated) + "\n"
    }

    /** Traverses only layout rows and compose overrides, preserving unrelated JSON verbatim. */
    fun migrateJson(root: JsonObject, position: PunctuationPosition): JsonObject =
        JsonObject(root.mapValues { (name, layout) ->
            if (name == LayoutJsonUtils.PROFILE_META_KEY) layout else migrateLayout(layout, position)
        })

    private fun migrateLayout(layout: JsonElement, position: PunctuationPosition): JsonElement = when (layout) {
        is JsonArray -> migrateRows(layout, position)
        is JsonObject -> JsonObject(layout.mapValues { (name, submode) ->
            when {
                name == "__meta__" -> submode
                submode is JsonArray -> migrateRows(submode, position)
                submode is JsonObject -> JsonObject(submode.mapValues { (field, rows) ->
                    if ((field == "default" || field == "") && rows is JsonArray) {
                        migrateRows(rows, position)
                    } else rows
                })
                else -> submode
            }
        })
        else -> layout
    }

    private fun migrateRows(rows: JsonArray, position: PunctuationPosition): JsonArray =
        JsonArray(rows.map { row ->
            when (row) {
                is JsonArray -> migrateKeys(row, position)
                is JsonObject -> JsonObject(row.mapValues { (name, keys) ->
                    if (name == KeyboardRowStyleUtils.ROW_KEYS_FIELD && keys is JsonArray) {
                        migrateKeys(keys, position)
                    } else keys
                })
                else -> row
            }
        })

    private fun migrateKeys(keys: JsonArray, position: PunctuationPosition): JsonArray =
        JsonArray(keys.map { key ->
            if (key is JsonObject &&
                (key[KeyboardRowStyleUtils.ROW_META_MARKER] as? JsonPrimitive)?.booleanOrNull != true
            ) {
                fieldsToJson(LayoutJsonUtils.migrateDirectionalSwipeFields(keyFields(key), position))
            } else key
        })

    // Keep values as JsonElement, especially numbers that cannot round-trip through Double.
    // Only type and composeOverride need the Map helper's semantic representation.
    private fun keyFields(key: JsonObject): Map<String, Any?> = key.mapValues { (name, value) ->
        when {
            name == "type" && value is JsonPrimitive && value.isString -> value.content
            name == "composeOverride" && value is JsonObject -> keyFields(value)
            else -> value
        }
    }

    private fun fieldsToJson(fields: Map<String, Any?>): JsonObject = JsonObject(fields.mapValues { (_, value) ->
        when (value) {
            is JsonElement -> value
            is Map<*, *> -> fieldsToJson(value.entries.associate { it.key.toString() to it.value })
            is String -> JsonPrimitive(value)
            null -> JsonNull
            else -> error("Unexpected migrated field: $value")
        }
    })
}
