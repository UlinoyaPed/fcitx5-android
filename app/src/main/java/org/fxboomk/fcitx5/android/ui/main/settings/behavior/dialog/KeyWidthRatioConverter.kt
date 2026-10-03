/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior.dialog

import java.math.BigDecimal

internal object KeyWidthRatioConverter {
    private val minRatio = BigDecimal.ZERO
    private val maxRatio = BigDecimal.TEN

    fun formatLegacyWeight(weight: Number?): String {
        val legacyWeight = weight?.toString()?.toBigDecimalOrNull() ?: return ""
        val ratio = legacyWeight.movePointRight(1).stripTrailingZeros()
        return if (ratio.scale() <= 0) {
            ratio.setScale(1).toPlainString()
        } else {
            ratio.toPlainString()
        }
    }

    fun resolveLegacyWeight(originalWeight: Number?, ratioText: String?): Number? {
        val text = ratioText?.trim().orEmpty()
        if (text == formatLegacyWeight(originalWeight)) return originalWeight
        if (text.isEmpty()) return null

        val ratio = text.toBigDecimalOrNull()
            ?.takeIf { it >= minRatio && it <= maxRatio }
            ?: return null
        return runCatching { ratio.movePointLeft(1).toFloat() }
            .getOrNull()
            ?.takeIf { it.isFinite() }
    }
}
