/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.data.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class BundledThemesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun createsImagesDirectoryWithoutBundledAssets() {
        val destination = File(temporaryFolder.root, "theme")

        BundledThemes.install(destination, { emptyList() }, { error("No asset should be opened") })

        assertTrue(File(destination, "images").isDirectory)
    }

    @Test
    fun installsAllThreeSuppliedThemesWithImagesInTheirConfiguredLocations() {
        val destination = temporaryFolder.newFolder()

        installAssets(destination)

        val configurations = destination.listFiles().orEmpty().filter { it.extension == "json" }
        assertEquals(
            setOf("渐变·天蓝.json", "指尖生花日间.json", "指尖生花夜间.json"),
            configurations.map { it.name }.toSet()
        )
        configurations.forEach { configuration ->
            assertArrayEquals(File(assetsDirectory, configuration.name).readBytes(), configuration.readBytes())
            imagePaths(configuration).forEach { path ->
                assertEquals("images", File(path).parent)
                assertArrayEquals(File(assetsDirectory, path).readBytes(), File(destination, path).readBytes())
            }
        }
        assertEquals(4, File(destination, "images").listFiles().orEmpty().count { it.isFile })
    }

    @Test
    fun preservesExistingUserConfigurationWithTheSameFilename() {
        val destination = temporaryFolder.newFolder()
        val userTheme = File(destination, "指尖生花日间.json")
        val userContent = "User-edited theme configuration".toByteArray()
        userTheme.writeBytes(userContent)

        installAssets(destination)
        installAssets(destination)

        assertArrayEquals(userContent, userTheme.readBytes())
    }

    @Test
    fun doesNotRestoreAThemeDeletedAfterInstallation() {
        val destination = temporaryFolder.newFolder()
        installAssets(destination)
        val deleted = File(destination, "指尖生花夜间.json")
        assertTrue(deleted.delete())

        installAssets(destination)

        assertFalse(deleted.exists())
        assertTrue(File(destination, "指尖生花日间.json").isFile)
    }

    @Test
    fun retriesIncompleteInstallationAfterAnImageReadFailure() {
        val destination = temporaryFolder.newFolder()
        val name = "指尖生花日间.json"
        val path = imagePaths(File(assetsDirectory, name)).first()
        val failingAsset = "theme/$path"
        val failure = runCatching {
            BundledThemes.install(destination, { listOf(name) }) { asset ->
                if (asset == failingAsset) {
                    object : ByteArrayInputStream(File(assetsDirectory, path).readBytes()) {
                        private var started = false

                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            if (started) throw IOException("Interrupted image asset read")
                            started = true
                            return super.read(buffer, offset, minOf(length, 7))
                        }
                    }
                } else {
                    openAsset(asset)
                }
            }
        }
        assertTrue("The interrupted source must fail installation", failure.isFailure)
        assertFalse(File(destination, name).exists())

        BundledThemes.install(destination, { listOf(name) }, ::openAsset)

        val configuration = File(destination, name)
        assertTrue("The failed theme must remain eligible for installation", configuration.isFile)
        imagePaths(configuration).forEach { image ->
            assertArrayEquals(File(assetsDirectory, image).readBytes(), File(destination, image).readBytes())
        }
    }

    private val assetsDirectory: File
        get() = listOf(File("src/main/assets/theme"), File("app/src/main/assets/theme"))
            .firstOrNull { it.isDirectory }
            ?: error("Bundled theme assets are missing from the app module")

    private fun installAssets(destination: File) {
        BundledThemes.install(
            destination,
            { path -> File(assetsDirectory.parentFile, path).list().orEmpty().toList() },
            ::openAsset
        )
    }

    private fun openAsset(path: String): InputStream =
        File(assetsDirectory.parentFile, path).inputStream()

    private fun imagePaths(configuration: File): List<String> {
        val root = Json.parseToJsonElement(configuration.readText()).jsonObject
        val background = root["backgroundImage"] as? JsonObject ?: return emptyList()
        return listOf("croppedFilePath", "srcFilePath").map { key ->
            background.getValue(key).jsonPrimitive.content
        }
    }
}
