/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.data.prefs

import timber.log.Timber

import android.content.Context
import org.fxboomk.fcitx5.android.utils.DeviceInfoCollector
import org.fxboomk.fcitx5.android.utils.DeviceType

/**
 * Smart default value initializer.
 *
 * Automatically sets split keyboard defaults based on device type during first installation.
 */
object SmartDefaultInitializer {

    private const val TAG = "SmartDefaultInitializer"

    /**
     * Initialize settings (only executed on first installation).
     */
    fun initialize(context: Context, prefs: AppPrefs) {
        if (prefs.internal.settingsInitialized.getValue()) {
            return
        }

        val deviceInfo = DeviceInfoCollector.collect(context)
        val (enableSplit, threshold) = getSmartDefaults(deviceInfo.deviceType)

        prefs.keyboard.splitKeyboardEnabled.setValue(enableSplit)
        prefs.keyboard.splitKeyboardThreshold.setValue(threshold)
        prefs.keyboard.splitKeyboardGapPercent.setValue(20)
        prefs.internal.settingsInitialized.setValue(true)

        Timber.tag(TAG).i(
            "Smart init: device=%s, model=%s, smallestWidth=%ddp, enable=%s, threshold=%d",
            deviceInfo.deviceType,
            deviceInfo.modelName,
            deviceInfo.smallestWidthDp,
            enableSplit,
            threshold
        )
    }

    /**
     * Get smart defaults based on device type.
     *
     * @return Pair<enable split, threshold dp>
     */
    fun getSmartDefaults(deviceType: DeviceType): Pair<Boolean, Int> {
        return when (deviceType) {
            // Tablet: enabled + low threshold for always split
            DeviceType.TABLET -> true to 420

            // Foldable: enabled + balanced threshold for inner/outer screens
            DeviceType.FOLDABLE -> true to 440

            // Large phone: enabled + high threshold, split in landscape only
            DeviceType.LARGE_PHONE -> true to 520

            // Small phone: enabled + higher threshold, rarely split
            DeviceType.SMALL_PHONE -> true to 500

            // Standard phone: enabled + standard threshold
            DeviceType.PHONE -> true to 470
        }
    }
}
