/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.config

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import kotlinx.serialization.Serializable
import org.fxboomk.fcitx5.android.input.font.ButtonIconFont
import org.fxboomk.fcitx5.android.input.font.TextIconDrawable

object KeyIconIds {
    const val CAPS_NONE = "capslock_none"
    const val CAPS_ONCE = "capslock_once"
    const val CAPS_LOCK = "capslock_lock"
    const val BACKSPACE = "backspace"
    const val RETURN = "return"
    const val RETURN_GO = "return_go"
    const val RETURN_SEARCH = "return_search"
    const val RETURN_SEND = "return_send"
    const val RETURN_NEXT = "return_next"
    const val RETURN_PREVIOUS = "return_previous"
    const val RETURN_DONE = "return_done"
    const val LANGUAGE = "language"
    const val SPACE = "space"
    const val NUMPAD = "numpad"
    const val EMOJI = "emoji"
    const val QUICKPHRASE = "quickphrase"
    const val SYMBOLS = "symbols"
    const val UNICODE = "unicode"
    const val PAGEUP = "pageup"
    const val PAGEDOWN = "pagedown"
    const val CURSOR_UP = "cursor_up"
    const val CURSOR_DOWN = "cursor_down"
    const val CURSOR_LEFT = "cursor_left"
    const val CURSOR_RIGHT = "cursor_right"
    const val HOME = "home"
    const val END = "end"
    const val VOICE_INPUT = "voice_input"
}

@Serializable
data class KeyIconTheme(
    val name: String,
    val icons: Map<String, String> = emptyMap(),
    val previousIcons: Map<String, String> = emptyMap()
)

@Serializable
data class KeyIconThemesConfig(
    val activeTheme: String = DEFAULT_THEME_NAME,
    val themes: List<KeyIconTheme> = listOf(KeyIconTheme(DEFAULT_THEME_NAME))
) {
    companion object { const val DEFAULT_THEME_NAME = "Default" }
}

data class KeyIconDefinition(val id: String, val label: String, @DrawableRes val defaultIcon: Int)

object KeyIconCatalog {
    fun definitions(): List<KeyIconDefinition> = listOf(
        KeyIconDefinition(KeyIconIds.CAPS_NONE, "Capslock / None", org.fxboomk.fcitx5.android.R.drawable.ic_capslock_none),
        KeyIconDefinition(KeyIconIds.CAPS_ONCE, "Capslock / Once", org.fxboomk.fcitx5.android.R.drawable.ic_capslock_once),
        KeyIconDefinition(KeyIconIds.CAPS_LOCK, "Capslock / Lock", org.fxboomk.fcitx5.android.R.drawable.ic_capslock_lock),
        KeyIconDefinition(KeyIconIds.BACKSPACE, "Backspace", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_backspace_24),
        KeyIconDefinition(KeyIconIds.RETURN, "Return / Default", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_keyboard_return_24),
        KeyIconDefinition(KeyIconIds.RETURN_GO, "Return / Go", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_arrow_forward_24),
        KeyIconDefinition(KeyIconIds.RETURN_SEARCH, "Return / Search", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_search_24),
        KeyIconDefinition(KeyIconIds.RETURN_SEND, "Return / Send", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_send_24),
        KeyIconDefinition(KeyIconIds.RETURN_NEXT, "Return / Next", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_keyboard_tab_24),
        KeyIconDefinition(KeyIconIds.RETURN_PREVIOUS, "Return / Previous", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_keyboard_tab_reverse_24),
        KeyIconDefinition(KeyIconIds.RETURN_DONE, "Return / Done", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_done_24),
        KeyIconDefinition(KeyIconIds.LANGUAGE, "Language", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_language_24),
        KeyIconDefinition(KeyIconIds.SPACE, "Space", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_space_bar_24),
        KeyIconDefinition(KeyIconIds.NUMPAD, "Numpad", org.fxboomk.fcitx5.android.R.drawable.ic_number_pad),
        KeyIconDefinition(KeyIconIds.EMOJI, "Emoji", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_tag_faces_24),
        KeyIconDefinition(KeyIconIds.QUICKPHRASE, "Quick phrase", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_format_quote_24),
        KeyIconDefinition(KeyIconIds.SYMBOLS, "Symbols", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_emoji_symbols_24),
        KeyIconDefinition(KeyIconIds.UNICODE, "Unicode", org.fxboomk.fcitx5.android.R.drawable.ic_logo_unicode),
        KeyIconDefinition(KeyIconIds.PAGEUP, "Pageup", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_arrow_upward_24),
        KeyIconDefinition(KeyIconIds.PAGEDOWN, "Pagedown", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_arrow_downward_24),
        KeyIconDefinition(KeyIconIds.CURSOR_UP, "Cursor Up", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_keyboard_arrow_up_24),
        KeyIconDefinition(KeyIconIds.CURSOR_DOWN, "Cursor Down", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_keyboard_arrow_down_24),
        KeyIconDefinition(KeyIconIds.CURSOR_LEFT, "Cursor Left", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_keyboard_arrow_left_24),
        KeyIconDefinition(KeyIconIds.CURSOR_RIGHT, "Cursor Right", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_keyboard_arrow_right_24),
        KeyIconDefinition(KeyIconIds.HOME, "Home", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_first_page_24),
        KeyIconDefinition(KeyIconIds.END, "End", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_last_page_24),
        KeyIconDefinition(KeyIconIds.VOICE_INPUT, "Voice input", org.fxboomk.fcitx5.android.R.drawable.ic_baseline_keyboard_voice_24)
    )
    fun definition(id: String): KeyIconDefinition? = definitions().firstOrNull { it.id == id }
}

object KeyIconThemeStore {
    private val json = kotlinx.serialization.json.Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val listeners = linkedSetOf<() -> Unit>()

    @Synchronized fun addOnChangedListener(listener: () -> Unit) { listeners += listener }

    @Synchronized fun removeOnChangedListener(listener: () -> Unit) { listeners -= listener }

    @Synchronized private fun notifyChanged() = listeners.toList().forEach { it() }

    @Synchronized fun read(): KeyIconThemesConfig {
        val file = UserConfigFiles.keyIconThemesConfig()
        val parsed = file?.let { runCatching { json.decodeFromString<KeyIconThemesConfig>(it.readText()) }.getOrNull() }
        return withBuiltins(parsed ?: KeyIconThemesConfig())
    }

    @Synchronized fun write(config: KeyIconThemesConfig): Result<Unit> = runCatching {
        val file = UserConfigFiles.keyIconThemesConfig() ?: error("Cannot resolve key icon themes path")
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(normalize(config)) + "\n")
        notifyChanged()
    }

    fun activeTheme(): KeyIconTheme {
        val config = read()
        return config.themes.firstOrNull { it.name == config.activeTheme } ?: config.themes.first()
    }

    fun listThemes(): List<KeyIconTheme> = read().themes

    fun updateIcon(themeName: String, id: String, value: String?, activate: Boolean = false): Result<Unit> {
        val config = read()
        val theme = config.themes.firstOrNull { it.name == themeName } ?: KeyIconTheme(themeName)
        val icons = theme.icons.toMutableMap().apply {
            if (value.isNullOrBlank()) remove(id) else put(id, value.trim())
        }
        return saveTheme(theme.copy(icons = icons), activate)
    }

    fun saveTheme(theme: KeyIconTheme, activate: Boolean = false): Result<Unit> {
        val config = read()
        val previous = config.themes.firstOrNull { it.name == theme.name }?.icons.orEmpty()
        val saved = theme.copy(previousIcons = previous)
        val themes = config.themes.filterNot { it.name == theme.name } + saved
        return write(config.copy(activeTheme = if (activate) theme.name else config.activeTheme, themes = themes))
    }

    fun activate(name: String): Result<Unit> {
        val config = read()
        if (config.themes.none { it.name == name }) return Result.failure(IllegalArgumentException("Unknown key icon theme: $name"))
        return write(config.copy(activeTheme = name))
    }

    fun iconValue(id: String): String? = activeTheme().icons[id]

    fun previousIcon(themeName: String, id: String): String? =
        read().themes.firstOrNull { it.name == themeName }?.previousIcons?.get(id)

    @DrawableRes fun resolveDrawable(context: Context, id: String, @DrawableRes fallback: Int): Int {
        val value = iconValue(id)?.trim().orEmpty()
        if (value.isEmpty()) return fallback
        return context.resources.getIdentifier(value.removePrefix("@drawable/"), "drawable", context.packageName)
            .takeIf { it != 0 } ?: fallback
    }

    fun resolveDrawableObject(context: Context, id: String, @DrawableRes fallback: Int): Drawable? {
        val value = iconValue(id)?.trim().orEmpty()
        if (value.isEmpty()) return fallback.takeIf { it != 0 }?.let { AppCompatResources.getDrawable(context, it) }
        val svg = ButtonIconSpec.svg(value)?.removePrefix("svg:")
            ?: ButtonIconSpec.svgMarkup(value)
        if (svg != null) return SvgIconDrawable.parse(svg, context.resources.displayMetrics.density)
        ButtonIconSpec.glyph(value)?.let { glyph ->
            return TextIconDrawable(
                glyph,
                ButtonIconFont.typeface(context),
                context.resources.displayMetrics.density
            )
        }
        val resource = context.resources.getIdentifier(value.removePrefix("@drawable/"), "drawable", context.packageName)
        return AppCompatResources.getDrawable(context, resource.takeIf { it != 0 } ?: fallback)
    }

    private fun normalize(config: KeyIconThemesConfig): KeyIconThemesConfig {
        val themes = config.themes.filter { it.name.isNotBlank() }.distinctBy { it.name }
            .ifEmpty { listOf(KeyIconTheme(KeyIconThemesConfig.DEFAULT_THEME_NAME)) }
        val active = themes.firstOrNull { it.name == config.activeTheme }?.name ?: themes.first().name
        return config.copy(activeTheme = active, themes = themes)
    }

    private fun withBuiltins(value: KeyIconThemesConfig): KeyIconThemesConfig {
        val userNames = value.themes.mapTo(hashSetOf()) { it.name }
        val themes = value.themes + BuiltinThemeCatalog.keyIconThemes().filter { it.name !in userNames }
        val active = themes.firstOrNull { it.name == value.activeTheme }?.name ?: themes.first().name
        return normalize(value.copy(activeTheme = active, themes = themes))
    }
}

typealias KeyIconConfig = KeyIconThemesConfig

/** Short facade used by settings UI and future import/export flows. */
object KeyIconStore {
    fun read(): KeyIconConfig = KeyIconThemeStore.read()
    fun activeTheme(): KeyIconTheme = KeyIconThemeStore.activeTheme()
    fun listThemes(): List<KeyIconTheme> = KeyIconThemeStore.listThemes()
    fun saveTheme(theme: KeyIconTheme, activate: Boolean = false): Result<Unit> =
        KeyIconThemeStore.saveTheme(theme, activate)
    fun activate(name: String): Result<Unit> = KeyIconThemeStore.activate(name)
    fun updateIcon(themeName: String, id: String, value: String?, activate: Boolean = false): Result<Unit> =
        KeyIconThemeStore.updateIcon(themeName, id, value, activate)
    fun previousIcon(themeName: String, id: String): String? = KeyIconThemeStore.previousIcon(themeName, id)
    fun addOnChangedListener(listener: () -> Unit) = KeyIconThemeStore.addOnChangedListener(listener)
    fun removeOnChangedListener(listener: () -> Unit) = KeyIconThemeStore.removeOnChangedListener(listener)
    fun definitions(): List<KeyIconDefinition> = KeyIconCatalog.definitions()
    fun resolveDrawable(context: Context, id: String, @DrawableRes fallback: Int): Int =
        KeyIconThemeStore.resolveDrawable(context, id, fallback)
    fun resolveDrawableObject(context: Context, id: String, @DrawableRes fallback: Int): Drawable? =
        KeyIconThemeStore.resolveDrawableObject(context, id, fallback)
}
