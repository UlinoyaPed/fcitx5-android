/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import org.fxboomk.fcitx5.android.core.FcitxAPI

/** Fcitx's standalone English (US) keyboard, independent of engine modes. */
internal suspend fun FcitxAPI.switchToEnglishInputMode(): Boolean =
    switchInputMethodPair("keyboard-us")
