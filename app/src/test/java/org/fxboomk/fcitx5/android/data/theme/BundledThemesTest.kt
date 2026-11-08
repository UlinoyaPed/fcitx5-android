/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.data.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream

class BundledThemesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun loadsSuppliedThemesAsReadOnlyBuiltinsWithAssetBackgrounds() {
        val themes = BundledThemes.loadBuiltins(
            { path -> File(assetsDirectory.parentFile, path).list().orEmpty().toList() },
            ::openAsset
        ).associateBy { it.name }

        assertEquals(setOf("渐变·天蓝", "指尖生花日间", "指尖生花夜间"), themes.keys)
        val skyBlue = themes.getValue("渐变·天蓝")
        assertEquals(-1707268, skyBlue.keyboardColor)
        assertEquals(-1707268, skyBlue.barColor)
        assertEquals(-1711276033, skyBlue.spaceBarColor)
        assertEquals(null, skyBlue.backgroundAssetPath)

        val daytime = themes.getValue("指尖生花日间")
        assertEquals("theme/images/指尖生花日间-cropped.png", daytime.backgroundAssetPath)
        assertEquals("theme/images/指尖生花日间-src.png", daytime.backgroundSourceAssetPath)
        assertTrue(daytime.backgroundCropRect != null)
        assertEquals(100, daytime.backgroundBrightness)
        assertEquals(2f, daytime.backgroundBlurRadius)
        assertTrue(themes.getValue("指尖生花夜间").isDark)
    }

    @Test
    fun markedLegacyThemesStayUserOwnedAndDeletedOnesDoNotReappear() {
        val themeDir = temporaryFolder.newFolder()
        val marker = File(themeDir, ".bundled-themes-v1")
        val day = File(themeDir, "指尖生花日间.json").apply { writeText("edited theme") }
        val backup = File(themeDir, "指尖生花日间.json.bak").apply { writeText("edited backup") }
        val image = File(themeDir, "images/指尖生花日间-cropped.png").apply {
            parentFile.mkdirs()
            writeText("user image")
        }
        marker.writeText("指尖生花日间.json\n指尖生花夜间.json\n")
        val themes = BundledThemes.loadBuiltins(
            { path -> File(assetsDirectory.parentFile, path).list().orEmpty().toList() },
            ::openAsset,
            marker.readLines().toSet()
        )
        assertEquals(setOf("渐变·天蓝"), themes.map { it.name }.toSet())
        assertEquals("edited theme", day.readText())
        assertEquals("edited backup", backup.readText())
        assertEquals("user image", image.readText())
        assertTrue(marker.isFile)
        assertFalse(File(themeDir, "指尖生花夜间.json").exists())
    }

    private val assetsDirectory: File
        get() = listOf(File("src/main/assets/theme"), File("app/src/main/assets/theme"))
            .firstOrNull { it.isDirectory }
            ?: error("Bundled theme assets are missing from the app module")

    private fun openAsset(path: String): InputStream =
        File(assetsDirectory.parentFile, path).inputStream()
}
