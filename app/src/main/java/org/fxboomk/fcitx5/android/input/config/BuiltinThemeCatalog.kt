/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.fxboomk.fcitx5.android.utils.appContext

/** Bundled toolbar and virtual-key icon theme packs. */
object BuiltinThemeCatalog {
    @Serializable
    private data class ThemeFile(
        val name: String,
        val version: Int = 1,
        val icons: Map<String, String> = emptyMap()
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val names = listOf("谷歌", "长日将烬")
    private val toolbarFiles: List<ThemeFile> by lazy {
        names.mapNotNull { read("builtin_themes/toolbar/$it.json") }
    }
    private val keyIconFiles: List<ThemeFile> by lazy {
        names.mapNotNull { read("builtin_themes/key_icons/$it.json") }
    }

    /**
     * Build toolbar themes for the current layout. Toolbar themes change icon
     * styling while retaining the user's button placement, including buttons
     * that are optional in the factory layout (for example language_switch).
     */
    fun toolbarThemes(base: ButtonsLayoutConfig = ButtonsLayoutConfig.default()): List<ButtonsLayoutTheme> =
        toolbarFiles.map { source ->
            fun iconFor(id: String): String? = when (id) {
                "theme_toggle" -> source.icons["icon_theme"]?.asSvgValue()
                "more" -> (source.icons["toolbar_toggle"] ?: source.icons["more"])?.asSvgValue()
                else -> source.icons[id]?.asSvgValue()
            }
            fun apply(button: ConfigurableButton): ConfigurableButton =
                button.copy(icon = iconFor(button.id) ?: button.icon)
            ButtonsLayoutTheme(
                name = source.name,
                config = base.copy(
                    kawaiiBarButtons = base.kawaiiBarButtons.map(::apply),
                    statusAreaButtons = base.statusAreaButtons.map(::apply),
                    optionalButtons = base.optionalButtons.map(::apply),
                    hideKeyboardButton = base.hideKeyboardButton.copy(
                        icon = source.icons["hide_keyboard"]?.asSvgValue() ?: base.hideKeyboardButton.icon
                    )
                )
            )
        }

    fun keyIconThemes(): List<KeyIconTheme> = keyIconFiles.map { source ->
            KeyIconTheme(source.name, source.icons.mapValues { it.value.asSvgValue() })
        }

    private fun read(path: String): ThemeFile? = runCatching {
        appContext.assets.open(path).bufferedReader().use { reader ->
            json.decodeFromString<ThemeFile>(reader.readText())
        }
    }.getOrNull()

    private fun String.asSvgValue(): String =
        if (trimStart().startsWith("svg:", ignoreCase = true)) trim() else "svg:${trim()}"
}
