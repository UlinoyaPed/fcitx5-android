/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.fxboomk.fcitx5.android.data.pinyin.ShuangpinLayoutKeys

/**
 * 英文键盘输入法的 uniqueName。只有英文键盘才回退到文件级共享的 `default` 布局：
 * 其余未定制专属布局的输入法一律使用出厂 26 键布局，避免复用可被用户定制的
 * "English"（`default`）布局造成布局污染。
 */
private const val ENGLISH_IME_UNIQUE_NAME = "keyboard-us"

internal data class TextKeyboardLayoutResolution(
    val sourceKey: String,
    val root: JsonElement,
    val subMode: JsonElement?,
    val globalDefault: JsonElement?,
    val rows: JsonArray?,
)

internal fun resolveTextKeyboardLayout(
    json: JsonObject,
    uniqueName: String,
    displayName: String,
    subModeLabel: String,
): TextKeyboardLayoutResolution? {
    val parentKey = ShuangpinLayoutKeys.parentBaseLayoutKey(uniqueName)
    val (matchedSourceKey, root) = when {
        json[uniqueName] != null -> uniqueName to json.getValue(uniqueName)
        json[displayName] != null -> displayName to json.getValue(displayName)
        // 双拼方案回退到"双拼"父级输入法布局（所有内置 / 自定义双拼的公共父布局）
        parentKey != null && json[parentKey] != null -> parentKey to json.getValue(parentKey)
        // 仅英文键盘回退到共享的 default 布局，避免非英文输入法复用可定制的 English 布局
        uniqueName == ENGLISH_IME_UNIQUE_NAME && json["default"] != null ->
            "default" to json.getValue("default")
        else -> return null
    }
    val layout = root as? JsonObject
    val subMode = layout?.get(subModeLabel)
    val isChildOnlyLayout = layout != null && layout["default"] == null && layout[""] == null
    // 兜底基准：双拼方案以"双拼"父级输入法布局为基准；其余输入法沿用全局 English 兜底
    // （英文键盘本身即 default，没有兜底）。
    val baselineKey = when {
        matchedSourceKey == "default" -> null
        parentKey != null -> parentKey.takeIf { it != matchedSourceKey }
        else -> "default"
    }
    val globalDefault = baselineKey?.let { json[it] }
    val rowFallbackKey = baselineKey ?: "default"
    val rowSources = when (root) {
        is JsonArray -> listOf(matchedSourceKey to root)
        is JsonObject -> listOfNotNull(
            subMode?.let { matchedSourceKey to it },
            (root["default"] ?: root[""])?.let { matchedSourceKey to it },
            globalDefault?.let { rowFallbackKey to it },
        )
        else -> listOfNotNull(globalDefault?.let { rowFallbackKey to it })
    }
    val (sourceKey, rows) = rowSources.firstNotNullOfOrNull { (key, source) ->
        source.rowsOrNull()?.let { key to it }
    } ?: (matchedSourceKey to null)
    // 兜底高度基准：双拼方案即便拥有自己的行，缺失的高度也应以"双拼"父级输入法布局
    // 为基准；其余输入法仅在子布局（无自己的行）时才继承兜底高度，避免显式
    // 父级输入法布局意外继承 English 的高度。
    val heightBaseline = globalDefault?.takeIf {
        isChildOnlyLayout || (parentKey != null && parentKey != matchedSourceKey)
    }
    return TextKeyboardLayoutResolution(
        sourceKey = sourceKey,
        root = root,
        subMode = subMode,
        globalDefault = heightBaseline,
        rows = rows,
    )
}

internal fun TextKeyboardLayoutResolution.keyboardHeightPercentOverride(
    landscape: Boolean,
): Int? {
    val key = if (landscape) {
        "keyboard_height_percent_landscape"
    } else {
        "keyboard_height_percent"
    }
    return layoutHeightPercentFromMeta(subMode, key)
        ?: layoutHeightPercentFromMeta(root, key)
        ?: layoutHeightPercentFromMeta(globalDefault, key)
}

/**
 * 文件级（profile 层）键盘高度覆写：文件顶层 {"__profile__": {"__meta__": {...}}}。
 * 字面量需与 LayoutJsonUtils.PROFILE_META_KEY 保持一致。
 */
internal const val PROFILE_META_KEY = "__profile__"

internal fun JsonObject?.fileLevelKeyboardHeightPercentOverride(landscape: Boolean): Int? {
    if (this == null) return null
    val profile = this[PROFILE_META_KEY] as? JsonObject ?: return null
    return layoutHeightPercentFromMeta(
        profile,
        if (landscape) "keyboard_height_percent_landscape" else "keyboard_height_percent"
    )
}

private fun layoutHeightPercentFromMeta(layout: JsonElement?, key: String): Int? {
    val meta = (layout as? JsonObject)?.get("__meta__") as? JsonObject ?: return null
    return (meta[key] as? JsonPrimitive)
        ?.intOrNull
        ?.takeIf { it in 10..90 }
}

private fun JsonElement?.rowsOrNull(): JsonArray? {
    return when (this) {
        is JsonArray -> this
        is JsonObject -> (this["rows"] ?: this["default"] ?: this[""]) as? JsonArray
        else -> null
    }
}
