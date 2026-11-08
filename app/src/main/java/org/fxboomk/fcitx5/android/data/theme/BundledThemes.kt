/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.data.theme

import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream

internal object BundledThemes {
    fun loadBuiltins(
        listAssets: (String) -> List<String>,
        openAsset: (String) -> InputStream,
        legacyInstalledNames: Set<String> = emptySet()
    ): List<Theme.Builtin> = listAssets("theme")
        .filter { it.endsWith(".json") && it !in legacyInstalledNames }
        .map { name ->
            val custom = openAsset("theme/$name").bufferedReader().use {
                Json.decodeFromString(CustomThemeSerializer, it.readText())
            }
            val background = custom.backgroundImage
            Theme.Builtin(
                name = custom.name,
                isDark = custom.isDark,
                backgroundColor = custom.backgroundColor,
                barColor = custom.barColor,
                keyboardColor = custom.keyboardColor,
                keyBackgroundColor = custom.keyBackgroundColor,
                keyTextColor = custom.keyTextColor,
                candidateTextColor = custom.candidateTextColor,
                candidateLabelColor = custom.candidateLabelColor,
                candidateCommentColor = custom.candidateCommentColor,
                altKeyBackgroundColor = custom.altKeyBackgroundColor,
                altKeyTextColor = custom.altKeyTextColor,
                accentKeyBackgroundColor = custom.accentKeyBackgroundColor,
                accentKeyTextColor = custom.accentKeyTextColor,
                keyPressHighlightColor = custom.keyPressHighlightColor,
                keyShadowColor = custom.keyShadowColor,
                popupBackgroundColor = custom.popupBackgroundColor,
                popupTextColor = custom.popupTextColor,
                spaceBarColor = custom.spaceBarColor,
                dividerColor = custom.dividerColor,
                clipboardEntryColor = custom.clipboardEntryColor,
                genericActiveBackgroundColor = custom.genericActiveBackgroundColor,
                genericActiveForegroundColor = custom.genericActiveForegroundColor,
                backgroundAssetPath = background?.croppedFilePath?.let { path ->
                    require(path.startsWith("images/") && File(path).name == path.removePrefix("images/"))
                    "theme/$path"
                },
                backgroundSourceAssetPath = background?.srcFilePath?.let { path ->
                    require(path.startsWith("images/") && File(path).name == path.removePrefix("images/"))
                    "theme/$path"
                },
                backgroundCropRect = background?.cropRect,
                backgroundCropRotation = background?.cropRotation ?: 0,
                backgroundBrightness = background?.brightness ?: 100,
                backgroundBlurRadius = background?.blurRadius ?: 0f
            )
        }

}
