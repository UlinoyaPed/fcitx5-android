package org.fxboomk.fcitx5.android.data.prefs

import android.content.SharedPreferences
import java.io.File

/** Import the former native option once, without replacing an Android-side choice. */
internal fun migrateLegacyChineseEnglishSpacing(preferences: SharedPreferences, config: File) {
    val key = "insert_space_between_chinese_and_english"
    if (preferences.contains(key)) return
    val lines = if (config.exists()) runCatching { config.readLines() }.getOrNull() ?: return
        else emptyList()
    var inBehavior = false
    var enabled = false
    for (raw in lines) {
        val line = raw.trim()
        if (line.startsWith("[")) inBehavior = line == "[Behavior]"
        if (inBehavior && line.substringBefore('=').trim() == "InsertSpaceBetweenChineseAndEnglish") {
            enabled = line.substringAfter('=', "").trim().equals("true", ignoreCase = true)
        }
    }
    preferences.edit().putBoolean(key, enabled).apply()
}
