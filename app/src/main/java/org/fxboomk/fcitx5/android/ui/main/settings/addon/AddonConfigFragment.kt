/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.addon

import android.content.Context
import org.fxboomk.fcitx5.android.core.FcitxAPI
import org.fxboomk.fcitx5.android.core.RawConfig
import org.fxboomk.fcitx5.android.ui.main.settings.ConfigLayout
import org.fxboomk.fcitx5.android.ui.main.settings.FcitxPreferenceFragment
import org.fxboomk.fcitx5.android.ui.main.settings.SettingsRoute
import org.fxboomk.fcitx5.android.utils.lazyRoute

class AddonConfigFragment : FcitxPreferenceFragment() {

    private val args by lazyRoute<SettingsRoute.AddonConfig>()

    override fun getPageTitle(): String = args.name

    override fun getConfigLayout(context: Context): ConfigLayout? =
        AddonConfigLayouts.get(context, args.uniqueName)

    override fun getConfigSectionRoute(
        context: Context,
        sectionIndex: Int
    ): SettingsRoute? = AddonConfigLayouts.get(context, args.uniqueName)
        ?.sectionTitle(context, sectionIndex)
        ?.let { sectionTitle ->
            val layout = AddonConfigLayouts.get(context, args.uniqueName)!!
            if (AddonConfigLayouts.isFuzzySection(layout, sectionIndex)) {
                SettingsRoute.FuzzyConfig(args.name, args.uniqueName, true, sectionTitle)
            } else SettingsRoute.ConfigSection(
                args.name, args.uniqueName, true, sectionIndex, sectionTitle
            )
        }

    override suspend fun obtainConfig(fcitx: FcitxAPI): RawConfig =
        fcitx.getAddonConfig(args.uniqueName)

    override suspend fun saveConfig(fcitx: FcitxAPI, newConfig: RawConfig) {
        fcitx.setAddonConfig(args.uniqueName, newConfig)
    }
}
