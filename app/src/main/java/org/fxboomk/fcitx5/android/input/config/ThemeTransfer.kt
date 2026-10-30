/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ButtonsLayoutThemeTransfer(
    val format: String = FORMAT,
    val theme: ButtonsLayoutTheme
) {
    companion object {
        const val FORMAT = "fcitx5-android.toolbar-button-theme.v1"
    }
}

@Serializable
data class KeyIconThemeTransfer(
    val format: String = FORMAT,
    val theme: KeyIconTheme
) {
    companion object {
        const val FORMAT = "fcitx5-android.key-icon-theme.v1"
    }
}

object ThemeTransferCodec {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    fun encodeToolbar(theme: ButtonsLayoutTheme): String =
        json.encodeToString(ButtonsLayoutThemeTransfer(theme = theme)) + "\n"

    fun decodeToolbar(value: String): ButtonsLayoutTheme {
        val transfer = json.decodeFromString<ButtonsLayoutThemeTransfer>(value)
        require(transfer.format == ButtonsLayoutThemeTransfer.FORMAT) { "Unsupported toolbar theme format" }
        return transfer.theme
    }

    fun encodeKeyIcons(theme: KeyIconTheme): String =
        json.encodeToString(KeyIconThemeTransfer(theme = theme)) + "\n"

    fun decodeKeyIcons(value: String): KeyIconTheme {
        val transfer = json.decodeFromString<KeyIconThemeTransfer>(value)
        require(transfer.format == KeyIconThemeTransfer.FORMAT) { "Unsupported key icon theme format" }
        return transfer.theme
    }
}
