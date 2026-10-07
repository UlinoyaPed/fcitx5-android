/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.candidates

import android.graphics.Rect
import android.view.View

/** A populated adapter or a VISIBLE child under a hidden window is not a visible choice. */
internal fun View.isCandidateVisibleToUser(): Boolean =
    isShown && windowVisibility == View.VISIBLE && !isLayoutRequested &&
        alpha > 0f && width > 0 && height > 0 && getGlobalVisibleRect(Rect())
