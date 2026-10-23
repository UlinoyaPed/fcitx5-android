/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainLabelPlacementTest {
    @Test
    fun singleEdgeCentersXHeightOnFaceInsteadOfBalancingInk() {
        val geometry = computeMainLabelGeometry(
            bandTop = 210f,
            bandBottom = 360f,
            faceCenter = 275f,
            inkTop = -60f,       // b: ascender
            inkBottom = 0f,
            xHeightTop = -30f,   // z/x/c/v/n/m body
            xHeightBottom = 0f,
            minGap = 5f,
            centerOnFace = true,
        )

        val xHeightCenter = geometry.baseline + (-30f + 0f) * geometry.scale / 2f
        assertEquals(275f, xHeightCenter, 0.01f)
        assertTrue("letter must clear the top sublabel", geometry.baseline - 60f * geometry.scale >= 215f)
    }

    @Test
    fun singleEdgeClampsToSubLabelClearance() {
        val geometry = computeMainLabelGeometry(
            bandTop = 210f,
            bandBottom = 360f,
            faceCenter = 275f,
            inkTop = -200f,
            inkBottom = 0f,
            xHeightTop = -30f,
            xHeightBottom = 0f,
            minGap = 5f,
            centerOnFace = true,
        )

        assertEquals(215f, geometry.baseline + geometry.scale * -200f, 0.01f)
    }

    @Test
    fun twoEdgeBandsKeepBalancingInk() {
        val geometry = computeMainLabelGeometry(
            bandTop = 220f,
            bandBottom = 340f,
            faceCenter = 275f,
            inkTop = -40f,
            inkBottom = 0f,
            xHeightTop = -30f,
            xHeightBottom = 0f,
            minGap = 5f,
            centerOnFace = false,
        )

        val inkTop = geometry.baseline + geometry.scale * -40f
        val inkBottom = geometry.baseline
        assertEquals(280f, (inkTop + inkBottom) / 2f, 0.01f)
        assertEquals(260f, inkTop, 0.01f)
        assertTrue("ink must clear the top sublabel", inkTop >= 220f + 5f)
    }

    @Test
    fun sharedInputsProduceOneBaseline() {
        val first = computeMainLabelGeometry(210f, 360f, 275f, -60f, 0f, -30f, 0f, 5f, true)
        val second = computeMainLabelGeometry(210f, 360f, 275f, -60f, 0f, -30f, 0f, 5f, true)
        assertEquals(first, second)
    }
}
