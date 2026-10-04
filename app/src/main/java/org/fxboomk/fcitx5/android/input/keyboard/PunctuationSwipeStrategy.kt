/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.data.prefs.ManagedPreferenceEnum

enum class PunctuationSwipeStrategy(override val stringRes: Int) : ManagedPreferenceEnum {
    Default(R.string.default_),
    FollowInputMode(R.string.punctuation_swipe_follow_input_mode),
    Raw(R.string.punctuation_swipe_raw);
}
