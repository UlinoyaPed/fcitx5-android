/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.data.theme

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ThemeFilesManagerTest {

    @Test
    fun importsRepairsReloadsAndRoundTripsBackgroundTheme() {
        val names = listOf(uniqueThemeName(), uniqueThemeName())
        val importedThemes = mutableListOf<Theme.Custom>()
        val cropped = png(Color.RED)
        val source = png(Color.BLUE)
        val theme = oldPathTheme(names.first())
        val input = archive(
            "nested/theme.json" to Json.encodeToString(CustomThemeSerializer, theme).encodeToByteArray(),
            "nested/images/cropped.png" to cropped,
            "nested/images/src.png" to source
        )
        try {
            val (created, imported, migrated) = input.inputStream().use {
                ThemeFilesManager.importTheme(it, importedName = names.first()).getOrThrow()
            }
            importedThemes.add(imported)
            assertTrue(created)
            assertFalse(migrated)
            assertEquals(names.first(), imported.name)
            assertBackgroundFiles(imported, cropped, source)
            assertSavedRelativePaths(imported)

            // Export the returned theme before a list refresh, as the UI can do immediately.
            val exported = ByteArrayOutputStream()
            ThemeFilesManager.exportTheme(imported, exported).getOrThrow()
            val (roundTripCreated, roundTripped, roundTripMigrated) = exported.toByteArray().inputStream().use {
                ThemeFilesManager.importTheme(it, importedName = names.last()).getOrThrow()
            }
            importedThemes.add(roundTripped)
            assertTrue(roundTripCreated)
            assertFalse(roundTripMigrated)
            assertEquals(names.last(), roundTripped.name)
            assertBackgroundFiles(roundTripped, cropped, source)
            assertSavedRelativePaths(roundTripped)
            assertFalse(
                "Imported copies must own separate background files",
                imported.backgroundImage?.croppedFilePath == roundTripped.backgroundImage?.croppedFilePath
            )

            val reloaded = ThemeFilesManager.listThemes().filter { it.name in names }
            assertEquals(names.toSet(), reloaded.map { it.name }.toSet())
            reloaded.forEach { assertBackgroundFiles(it, cropped, source) }

            importedThemes.forEach { saved ->
                deleteTestTheme(saved, importedThemes.filter { it.name != saved.name })
                assertFalse(File(themeDirectory, "${saved.name}.json").exists())
                val background = requireNotNull(saved.backgroundImage)
                assertFalse(File(background.croppedFilePath).exists())
                assertFalse(File(background.srcFilePath).exists())
            }
            assertTrue("Deleting themes must retain theme/images", imagesDirectory.isDirectory)
        } finally {
            try {
                importedThemes.forEach { saved ->
                    deleteTestTheme(saved, importedThemes.filter { it.name != saved.name })
                }
            } finally {
                names.forEach(::removeTestOwnedFiles)
            }
        }
    }

    @Test
    fun rejectedMissingSourceLeavesNoConfigurationOrNewImages() {
        // Initialize the same singleton used by the app before taking the filesystem snapshot.
        ThemeManager.getAllThemes()
        val name = uniqueThemeName()
        val before = imagesDirectory.list().orEmpty().toSet()
        val theme = oldPathTheme(name)
        val input = archive(
            "nested/theme.json" to Json.encodeToString(CustomThemeSerializer, theme).encodeToByteArray(),
            "nested/images/cropped.png" to png(Color.RED)
        )
        try {
            val result = input.inputStream().use {
                ThemeFilesManager.importTheme(it, importedName = name)
            }

            assertTrue("A missing source image must reject the import", result.isFailure)
            assertFalse(File(themeDirectory, "$name.json").exists())
            assertEquals(before, imagesDirectory.list().orEmpty().toSet())
            assertTrue(imagesDirectory.isDirectory)
        } finally {
            removeTestOwnedFiles(name)
        }
    }

    private fun oldPathTheme(name: String): Theme.Custom {
        val oldDirectory = "/storage/emulated/0/Android/data/old.package/files/theme"
        return ThemePreset.PixelLight.deriveCustomBackground(
            name = name,
            croppedBackgroundImage = "$oldDirectory/cropped.png",
            originBackgroundImage = "$oldDirectory/src.png",
            cropBackgroundRect = null
        )
    }

    private fun assertBackgroundFiles(theme: Theme.Custom, cropped: ByteArray, source: ByteArray) {
        val background = requireNotNull(theme.backgroundImage)
        val croppedFile = File(background.croppedFilePath)
        val sourceFile = File(background.srcFilePath)
        listOf(croppedFile, sourceFile).forEach { image ->
            assertTrue(image.isAbsolute)
            assertEquals(imagesDirectory.canonicalFile, image.parentFile?.canonicalFile)
            assertTrue(image.isFile)
        }
        assertArrayEquals(cropped, croppedFile.readBytes())
        assertArrayEquals(source, sourceFile.readBytes())
    }

    private fun assertSavedRelativePaths(theme: Theme.Custom) {
        val file = File(themeDirectory, "${theme.name}.json")
        assertTrue(file.isFile)
        val json = Json.parseToJsonElement(file.readText()).jsonObject
        val background = json.getValue("backgroundImage").jsonObject
        listOf("croppedFilePath", "srcFilePath").forEach { key ->
            val path = background.getValue(key).jsonPrimitive.content
            assertFalse(File(path).isAbsolute)
            assertEquals("images", File(path).parent)
            assertTrue(File(themeDirectory, path).isFile)
        }
    }

    private fun deleteTestTheme(theme: Theme.Custom, otherTestThemes: List<Theme.Custom>) {
        val otherThemes = ThemeManager.getAllThemes().filterIsInstance<Theme.Custom>()
            .filter { it.name != theme.name } + otherTestThemes
        ThemeFilesManager.deleteThemeFiles(theme, otherThemes)
    }

    private fun removeTestOwnedFiles(name: String) {
        // Each name includes a fresh UUID; clean only this test's files, including failed writes.
        themeDirectory.listFiles().orEmpty().filter { file ->
            file.name == "$name.json" || file.name == "$name.json.new" || file.name == "$name.json.bak"
        }.forEach { it.delete() }
        imagesDirectory.listFiles().orEmpty().filter { it.name.startsWith("$name-") }
            .forEach { it.delete() }
    }

    private fun png(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun archive(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

    private fun uniqueThemeName() = "theme-files-test-${UUID.randomUUID()}"

    private val themeDirectory: File
        get() = File(
            requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)),
            "theme"
        )

    private val imagesDirectory: File
        get() = File(themeDirectory, "images")
}
