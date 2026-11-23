/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior.data

import android.util.AtomicFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.fxboomk.fcitx5.android.input.keyboard.PROFILE_META_KEY
import org.fxboomk.fcitx5.android.input.keyboard.fileLevelKeyboardHeightPercentOverride
import org.fxboomk.fcitx5.android.input.keyboard.keyboardHeightPercentOverride
import org.fxboomk.fcitx5.android.input.keyboard.resolveTextKeyboardLayout
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.utils.KeyboardRowStyleUtils
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.utils.LayoutJsonUtils
import java.io.File
import java.io.FileNotFoundException

internal data class LayoutCopyAddress(
    val inputMethod: String,
    val displayName: String = inputMethod,
    val subModeLabel: String = "",
    val subModeName: String = "",
) {
    val modeKeys get() = listOf(subModeLabel.trim(), subModeName.trim())
        .filter { it.isNotEmpty() }.distinct()

    val isValid get() = inputMethod.isNotBlank() && inputMethod != PROFILE_META_KEY &&
        modeKeys.none { it in reservedLayoutKeys }

    fun parent() = copy(subModeLabel = "", subModeName = "")
}

internal data class LayoutCopyPlan(
    val profile: JsonObject,
    val overwritten: Set<LayoutCopyAddress>,
)

/** Copy the effective rows and height of one layout, preserving unrelated profile data. */
internal fun planLayoutCopy(
    profile: JsonObject,
    source: LayoutCopyAddress,
    targets: List<LayoutCopyAddress>,
    factoryRows: JsonArray,
    defaultHeights: LayoutHeightPercentOverrides,
): LayoutCopyPlan {
    val addresses = listOf(source) + targets
    require(targets.isNotEmpty() && targets.distinct().size == targets.size)
    require(addresses.all { it.isValid })
    val sourceLayout = profile.snapshotLayout(source, factoryRows, defaultHeights)
    val updated = profile.toMutableMap()
    val overwritten = mutableSetOf<LayoutCopyAddress>()
    val written = mutableSetOf<Pair<String, String?>>()

    for (target in targets) {
        // Legacy display-name nodes participate in resolution. Preserve their
        // children when materializing a canonical destination.
        val original = profile.ownLayout(target)
        val existing = updated[target.inputMethod] ?: original
        val current = when (existing) {
            is JsonArray -> mapOf("default" to existing)
            is JsonObject -> existing
            null -> emptyMap()
            else -> error("Invalid destination layout: ${target.inputMethod}")
        }
        val child = target.modeKeys.firstOrNull { current.containsKey(it) }
            ?: target.modeKeys.firstOrNull()
        require(written.add(target.inputMethod to child)) { "Duplicate copy destination" }
        val originalChild = (original as? JsonObject)?.let { node ->
            target.modeKeys.firstOrNull(node::containsKey)
        }
        val sourceChild = (profile.ownLayout(source) as? JsonObject)?.let { node ->
            source.modeKeys.firstOrNull(node::containsKey)
        } ?: source.modeKeys.firstOrNull()
        require(source.inputMethod != target.inputMethod || sourceChild != child) {
            "Source and destination are the same layout"
        }

        val destination = if (child == null) {
            if (original != null) overwritten += target
            // Replace just the parent's layout and metadata. Explicit children
            // keep their overrides and still inherit unspecified values.
            current.filterKeys { it !in reservedLayoutKeys } + sourceLayout
        } else {
            if (originalChild != null) overwritten += target
            val parent = if (existing == null && profile.resolve(target.parent()) == null) {
                // Before a local node exists this IM uses the factory layout.
                // Adding a child must not redirect unrelated modes to a shared layout.
                profile.snapshotLayout(target.parent(), factoryRows, defaultHeights)
            } else current
            parent + (child to sourceLayout)
        }
        updated[target.inputMethod] = JsonObject(destination)
    }
    return LayoutCopyPlan(JsonObject(updated), overwritten)
}

private val reservedLayoutKeys = setOf("rows", "default", "", "__meta__")

private fun JsonObject.ownLayout(address: LayoutCopyAddress): JsonElement? =
    this[address.inputMethod] ?: this[address.displayName]

private fun JsonObject.resolve(address: LayoutCopyAddress) = resolveTextKeyboardLayout(
    this, address.inputMethod, address.displayName, address.subModeLabel, address.subModeName
)

private fun JsonObject.snapshotLayout(
    address: LayoutCopyAddress,
    factoryRows: JsonArray,
    defaultHeights: LayoutHeightPercentOverrides
): JsonObject {
    val resolved = resolve(address)
    val rows = resolved?.rows ?: factoryRows
    require(rows.isNotEmpty()) { "Cannot copy an empty keyboard layout" }
    val meta = linkedMapOf<String, JsonElement>()
    for (node in listOf(resolved?.globalDefault, resolved?.root, resolved?.subMode)) {
        val metadata = (node as? JsonObject)?.get("__meta__") as? JsonObject
        if (metadata != null) meta.putAll(metadata)
    }
    for ((landscape, field, default) in listOf(
        Triple(false, "keyboard_height_percent", defaultHeights.portrait),
        Triple(true, "keyboard_height_percent_landscape", defaultHeights.landscape)
    )) {
        val height = resolved?.keyboardHeightPercentOverride(landscape)
            ?: fileLevelKeyboardHeightPercentOverride(landscape) ?: default
        if (height != null) meta[field] = JsonPrimitive(height)
    }
    return JsonObject(mapOf("default" to rows.forCopiedMode(address), "__meta__" to JsonObject(meta)))
}

/** A copied child keeps the label it displayed in that mode on a different engine. */
private fun JsonArray.forCopiedMode(address: LayoutCopyAddress): JsonArray {
    val modes = listOf(address.subModeLabel, address.subModeName).distinct()
    if (address.modeKeys.isEmpty()) return this

    fun copyKey(element: JsonElement): JsonElement {
        val key = element as? JsonObject ?: return element
        val result = key.toMutableMap()
        val display = key["displayText"] as? JsonObject
        if (display != null) {
            val label = modes.firstNotNullOfOrNull { mode ->
                (display[mode] as? JsonPrimitive)?.takeUnless { it is JsonNull }
            }
            if (label == null) result.remove("displayText") else result["displayText"] = label
        }
        key["composeOverride"]?.let { result["composeOverride"] = copyKey(it) }
        return JsonObject(result)
    }

    return JsonArray(map { row ->
        when (row) {
            is JsonArray -> JsonArray(row.map(::copyKey))
            is JsonObject -> {
                val field = KeyboardRowStyleUtils.ROW_KEYS_FIELD
                val keys = row[field] as? JsonArray
                if (keys == null) row else JsonObject(row + (field to JsonArray(keys.map(::copyKey))))
            }
            else -> row
        }
    })
}

internal class LayoutCopyFile(private val path: File) {
    private val file = AtomicFile(path)

    fun read(): String? = synchronized(profileWrites) {
        try {
            file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (error: FileNotFoundException) {
            if (file.baseFile.exists()) throw error
            null
        }
    }

    fun parse(source: String): JsonObject =
        Json { isLenient = true }.parseToJsonElement(LayoutJsonUtils.removeJsonComments(source))
            as? JsonObject ?: error("The layout profile must be a JSON object")

    // Compare the exact previewed bytes, including edits to the source or any
    // destination made while the confirmation dialog was open.
    fun write(expected: String?, updated: JsonObject) = synchronized(profileWrites) {
        check(read() == expected) { "Layout profile changed while confirming the copy" }
        val text = LayoutJsonUtils.formatJsonCompact(updated) + "\n"
        val output = file.startWrite()
        try {
            output.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
        check(read() == text) { "The copied layout could not be saved" }
    }

    private companion object {
        val profileWrites = Any()
    }
}
