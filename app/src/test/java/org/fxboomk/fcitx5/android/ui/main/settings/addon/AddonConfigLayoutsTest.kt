/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.addon

import org.junit.Assert.assertEquals
import org.junit.Test

class AddonConfigLayoutsTest {

    @Test
    fun fuzzyMappingOptionsAreSortedByCombinedLengthAndGroupedFamilies() {
        assertEquals(
            listOf(
                "F_H",
                "L_N",
                "L_R",
                "V_U",
                "C_CH",
                "S_SH",
                "Z_ZH",
                "U_OU",
                "VE_UE",
                "AN_ANG",
                "EN_ENG",
                "IN_ING",
                "IAN_IANG",
                "UAN_UANG"
            ),
            AddonConfigLayouts.fuzzyMappingOptionOrder()
        )
        assertEquals(
            AddonConfigLayouts.FUZZY_MAPPING_OPTIONS,
            AddonConfigLayouts.fuzzyMappingOptionOrder().toSet()
        )
    }
}
