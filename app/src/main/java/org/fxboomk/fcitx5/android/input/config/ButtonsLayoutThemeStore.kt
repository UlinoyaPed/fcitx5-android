/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val DEFAULT_BUTTONS_THEME_NAME = "Default"

@Serializable
data class ButtonsLayoutTheme(
    val name: String,
    val config: ButtonsLayoutConfig
)

@Serializable
data class ButtonsLayoutThemes(
    val activeTheme: String = DEFAULT_BUTTONS_THEME_NAME,
    val themes: List<ButtonsLayoutTheme> = listOf(
        ButtonsLayoutTheme(DEFAULT_BUTTONS_THEME_NAME, ButtonsLayoutConfig.default())
    )
)

/** Named snapshots for toolbar button layouts. */
object ButtonsLayoutThemeStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Synchronized
    fun load(): ButtonsLayoutThemes {
        val file = UserConfigFiles.buttonsLayoutThemes()
        if (file == null) return withBuiltins(ButtonsLayoutThemes())
        val parsed = runCatching { json.decodeFromString<ButtonsLayoutThemes>(file.readText()) }.getOrNull()
        return withBuiltins(parsed?.takeIf { it.themes.isNotEmpty() } ?: ButtonsLayoutThemes())
    }

    fun activeTheme(): ButtonsLayoutTheme {
        val themes = load()
        return themes.themes.firstOrNull { it.name == themes.activeTheme } ?: themes.themes.first()
    }

    @Synchronized
    fun save(name: String, config: ButtonsLayoutConfig): ButtonsLayoutThemes {
        val normalized = name.trim().ifEmpty { DEFAULT_BUTTONS_THEME_NAME }
        val current = load()
        val updated = current.themes.filterNot { it.name == normalized } +
            ButtonsLayoutTheme(normalized, config)
        return write(current.copy(activeTheme = normalized, themes = updated))
    }

    @Synchronized
    fun activate(name: String): ButtonsLayoutConfig? {
        val current = load()
        val theme = current.themes.firstOrNull { it.name == name } ?: return null
        write(current.copy(activeTheme = theme.name))
        return theme.config
    }

    @Synchronized
    fun delete(name: String): ButtonsLayoutThemes {
        val current = load()
        if (name == DEFAULT_BUTTONS_THEME_NAME) return current
        val themes = current.themes.filterNot { it.name == name }.ifEmpty {
            listOf(ButtonsLayoutTheme(DEFAULT_BUTTONS_THEME_NAME, ButtonsLayoutConfig.default()))
        }
        val active = if (current.activeTheme == name) DEFAULT_BUTTONS_THEME_NAME else current.activeTheme
        return write(current.copy(activeTheme = active, themes = themes))
    }

    private fun write(value: ButtonsLayoutThemes): ButtonsLayoutThemes {
        val file = UserConfigFiles.buttonsLayoutThemes() ?: return value
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(value) + "\n")
        return value
    }

    private fun withBuiltins(value: ButtonsLayoutThemes): ButtonsLayoutThemes {
        val baseConfig = ConfigProviders.readButtonsLayoutConfig<ButtonsLayoutConfig>()?.value
            ?: ButtonsLayoutConfig.default()
        val builtins = BuiltinThemeCatalog.toolbarThemes(baseConfig)
        val builtinByName = builtins.associateBy { it.name }
        // Refresh bundled themes in place so installations upgraded from an
        // older build also receive newly added icon mappings. Preserve the
        // user's layout and labels while replacing only themed icon values.
        val themes = value.themes.map { existing ->
            val builtin = builtinByName[existing.name] ?: return@map existing
            existing.copy(config = mergeThemedIcons(existing.config, builtin.config))
        }.toMutableList().apply {
            val existingNames = mapTo(hashSetOf()) { it.name }
            addAll(builtins.filter { it.name !in existingNames })
        }
        val active = themes.firstOrNull { it.name == value.activeTheme }?.name ?: themes.first().name
        return value.copy(activeTheme = active, themes = themes)
    }

    private fun mergeThemedIcons(
        current: ButtonsLayoutConfig,
        themed: ButtonsLayoutConfig
    ): ButtonsLayoutConfig {
        val themedById = (themed.kawaiiBarButtons + themed.statusAreaButtons + themed.optionalButtons)
            .associateBy { it.id }
        fun merge(button: ConfigurableButton): ConfigurableButton =
            themedById[button.id]?.icon?.let { button.copy(icon = it) } ?: button
        return current.copy(
            kawaiiBarButtons = current.kawaiiBarButtons.map(::merge),
            statusAreaButtons = current.statusAreaButtons.map(::merge),
            optionalButtons = current.optionalButtons.map(::merge),
            hideKeyboardButton = themed.hideKeyboardButton.icon?.let {
                current.hideKeyboardButton.copy(icon = it)
            } ?: current.hideKeyboardButton
        )
    }
}
