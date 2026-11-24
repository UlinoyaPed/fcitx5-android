/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import org.fxboomk.fcitx5.android.core.FcitxAPI

/** Switch to or from the app's standalone English keyboard, not an engine sub-mode. */
internal suspend fun FcitxAPI.switchToEnglishInputMode(): Boolean {
    val english = availableIme().firstOrNull {
        it.addon == "androidkeyboard" &&
            it.languageCode.substringBefore('-').substringBefore('_').equals("en", ignoreCase = true)
    } ?: return false
    return switchInputMethodPair(english.uniqueName)
}
