/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.addon

import android.content.Context
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.core.AddonInfo

/**
 * Display-name overrides for addons whose fcitx-provided name is not clear
 * enough in the app UI.
 */
object AddonDisplayNames {

    fun of(context: Context, addon: AddonInfo): String = when (addon.uniqueName) {
        "unicode" -> context.getString(R.string.addon_unicode_name)
        else -> addon.displayName
    }
}
