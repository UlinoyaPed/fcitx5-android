/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.data.prefs.ManagedPreferenceEnum

enum class LangSwitchLongPressBehavior(override val stringRes: Int) : ManagedPreferenceEnum {
    Default(R.string.lang_switch_long_press_default),
    BuiltInAndRime(R.string.lang_switch_long_press_builtin_and_rime),
    BuiltInOnly(R.string.lang_switch_long_press_builtin_only),
    RimeOnly(R.string.lang_switch_long_press_rime_only);

    val includesRimeSchemas: Boolean
        get() = this == BuiltInAndRime || this == RimeOnly
}
