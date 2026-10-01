/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.data.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ThemeArchiveTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun readsThemeWithoutBackground() {
        val theme = ThemePreset.PixelLight.deriveCustomNoBackground("Plain theme")

        val (decoded, migrated) = read(archive("theme.json" to theme.toJson()))

        assertEquals(theme, decoded)
        assertFalse(migrated)
    }

    @Test
    fun readsFlatBackgroundFiles() {
        val destination = temporaryFolder.newFolder()
        val theme = backgroundTheme("cropped.png", "src.png")

        val (decoded, migrated) = read(
            archive(
                "theme.json" to theme.toJson(),
                "cropped.png" to CROPPED,
                "src.png" to SOURCE
            ),
            destination
        )

        assertBackground(decoded, destination, "cropped.png", "src.png")
        assertFalse(migrated)
    }

    @Test
    fun resolvesImagesRelativeToNestedConfiguration() {
        val destination = temporaryFolder.newFolder()
        val theme = backgroundTheme("images/cropped.png", "images/src.png")

        val (decoded, migrated) = read(
            archive(
                "My theme/theme.json" to theme.toJson(),
                "My theme/images/cropped.png" to CROPPED,
                "My theme/images/src.png" to SOURCE
            ),
            destination
        )

        assertBackground(
            decoded, destination, "My theme/images/cropped.png", "My theme/images/src.png"
        )
        assertFalse(migrated)
    }

    @Test
    fun repairsAbsolutePathsFromAnotherAndroidPackage() {
        val destination = temporaryFolder.newFolder()
        val oldDirectory = "/storage/emulated/0/Android/data/other.package/files/theme"
        val theme = backgroundTheme("$oldDirectory/cropped.png", "$oldDirectory/src.png")

        val (decoded, migrated) = read(
            archive(
                "theme.json" to theme.toJson(),
                "images/cropped.png" to CROPPED,
                "images/src.png" to SOURCE
            ),
            destination
        )

        assertBackground(decoded, destination, "images/cropped.png", "images/src.png")
        assertFalse("Path repair alone is not a schema migration", migrated)
    }

    @Test
    fun repairsWindowsSeparatorsInConfigurationPaths() {
        val destination = temporaryFolder.newFolder()
        val theme = backgroundTheme("images\\cropped.png", "images\\src.png")

        val (decoded, _) = read(
            archive(
                "theme.json" to theme.toJson(),
                "images/cropped.png" to CROPPED,
                "images/src.png" to SOURCE
            ),
            destination
        )

        assertBackground(decoded, destination, "images/cropped.png", "images/src.png")
    }

    @Test
    fun preservesAnExactPathWhenOnlyTheOtherImageNeedsRepair() {
        listOf("one/cropped.png", "one\\cropped.png").forEach { croppedPath ->
            val destination = temporaryFolder.newFolder()
            val theme = backgroundTheme(croppedPath, "/old/installation/src.png")

            val (decoded, migrated) = read(
                archive(
                    "theme.json" to theme.toJson(),
                    "one/cropped.png" to CROPPED,
                    "two/cropped.png" to SOURCE,
                    "images/src.png" to SOURCE
                ),
                destination
            )

            assertBackground(decoded, destination, "one/cropped.png", "images/src.png")
            assertFalse(migrated)
        }
    }

    @Test
    fun repairsIncorrectFilenamesUsingUniqueImageRoles() {
        val destination = temporaryFolder.newFolder()
        val theme = backgroundTheme("missing-crop.png", "missing-original.png")

        val (decoded, _) = read(
            archive(
                "theme.json" to theme.toJson(),
                "images/background-cropped.png" to CROPPED,
                "images/background-src.png" to SOURCE
            ),
            destination
        )

        assertBackground(
            decoded, destination, "images/background-cropped.png", "images/background-src.png"
        )
    }

    @Test
    fun repairsMissingPathFieldsBeforeDecodingAgain() {
        val destination = temporaryFolder.newFolder()
        val original = backgroundTheme("unused.png", "unused.png").toJsonObject()
        val background = original.getValue("backgroundImage").jsonObject
        val json = JsonObject(
            original + ("backgroundImage" to JsonObject(background - "croppedFilePath" - "srcFilePath"))
        )

        val (decoded, migrated) = read(
            archive(
                "theme.json" to json.toString().toByteArray(),
                "images/background-cropped.png" to CROPPED,
                "images/background-src.png" to SOURCE
            ),
            destination
        )

        assertBackground(
            decoded, destination, "images/background-cropped.png", "images/background-src.png"
        )
        assertFalse(migrated)
    }

    @Test
    fun preservesSchemaMigrationStatusAfterRepair() {
        val original = backgroundTheme("missing-crop.png", "missing-source.png").toJsonObject()
        val json = JsonObject(original + ("version" to JsonPrimitive("2.0")))

        val (decoded, migrated) = read(
            archive(
                "theme.json" to json.toString().toByteArray(),
                "background-cropped.png" to CROPPED,
                "background-src.png" to SOURCE
            )
        )

        assertTrue(migrated)
        assertEquals(ThemePreset.PixelLight.keyTextColor, decoded.candidateTextColor)
    }

    @Test
    fun rejectsMissingBackgroundFiles() {
        val theme = backgroundTheme("cropped.png", "src.png")

        assertRejected(archive("theme.json" to theme.toJson()))
    }

    @Test
    fun doesNotUseCroppedImageAsAnAbsentSource() {
        val theme = backgroundTheme("wallpaper.png", "missing.png")

        assertRejected(
            archive("theme.json" to theme.toJson(), "wallpaper.png" to CROPPED)
        )
    }

    @Test
    fun rejectsAmbiguousBasenameRepair() {
        val theme = backgroundTheme("old/cropped.png", "src.png")

        assertRejected(
            archive(
                "theme.json" to theme.toJson(),
                "one/cropped.png" to CROPPED,
                "two/cropped.png" to SOURCE,
                "src.png" to SOURCE
            )
        )
    }

    @Test
    fun doesNotRepairUnrelatedInvalidConfigurationFields() {
        val json = JsonObject(backgroundTheme("missing.png", "missing.png").toJsonObject() - "isDark")

        assertRejected(
            archive(
                "theme.json" to json.toString().toByteArray(),
                "background-cropped.png" to CROPPED,
                "background-src.png" to SOURCE
            )
        )
    }

    @Test
    fun rejectsZipTraversalWithoutWritingOutsideExtractionDirectory() {
        val destination = temporaryFolder.newFolder("extracted")
        val outside = File(temporaryFolder.root, "outside.png")
        val theme = ThemePreset.PixelLight.deriveCustomNoBackground("Plain theme")

        assertRejected(
            archive("../outside.png" to CROPPED, "theme.json" to theme.toJson()),
            destination
        )

        assertFalse(outside.exists())
    }

    @Test
    fun rejectsAbsoluteZipEntries() {
        val destination = temporaryFolder.newFolder("extracted")
        val outside = File(temporaryFolder.root, "absolute.png")
        val theme = ThemePreset.PixelLight.deriveCustomNoBackground("Plain theme")

        assertRejected(
            archive(outside.absolutePath to CROPPED, "theme.json" to theme.toJson()),
            destination
        )

        assertFalse(outside.exists())
    }

    private fun backgroundTheme(cropped: String, source: String) =
        ThemePreset.PixelLight.deriveCustomBackground("Background theme", cropped, source)

    private fun Theme.Custom.toJson() =
        Json.encodeToString(CustomThemeSerializer, this).toByteArray()

    private fun Theme.Custom.toJsonObject() =
        Json.parseToJsonElement(toJson().toString(Charsets.UTF_8)).jsonObject

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

    private fun read(bytes: ByteArray, destination: File = temporaryFolder.newFolder()) =
        bytes.inputStream().use { ThemeArchive.read(it, destination) }

    private fun assertBackground(
        theme: Theme.Custom,
        destination: File,
        cropped: String,
        source: String
    ) {
        val background = requireNotNull(theme.backgroundImage)
        val croppedFile = File(background.croppedFilePath)
        val sourceFile = File(background.srcFilePath)
        assertTrue(croppedFile.isAbsolute)
        assertTrue(sourceFile.isAbsolute)
        assertEquals(File(destination, cropped).canonicalFile, croppedFile.canonicalFile)
        assertEquals(File(destination, source).canonicalFile, sourceFile.canonicalFile)
        assertArrayEquals(CROPPED, croppedFile.readBytes())
        assertArrayEquals(SOURCE, sourceFile.readBytes())
    }

    private fun assertRejected(bytes: ByteArray, destination: File = temporaryFolder.newFolder()) {
        val result = runCatching { read(bytes, destination) }
        assertTrue("Archive should be rejected", result.isFailure)
    }

    private companion object {
        val CROPPED = byteArrayOf(1, 2, 3)
        val SOURCE = byteArrayOf(4, 5, 6)
    }
}
