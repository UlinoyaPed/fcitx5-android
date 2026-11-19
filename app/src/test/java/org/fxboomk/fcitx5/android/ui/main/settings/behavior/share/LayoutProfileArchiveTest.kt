package org.fxboomk.fcitx5.android.ui.main.settings.behavior.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LayoutProfileArchiveTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun exportAndImportRoundTripMultipleUnicodeProfiles() {
        val first = temp.newFile("first.json").apply { writeText("{\"name\":\"中文\"}") }
        val second = temp.newFile("second.json").apply { writeText("{\"name\":\"emoji😀\"}") }
        val zip = ByteArrayOutputStream()
        LayoutProfileArchive.exportZip(listOf("布局" to first, "second" to second), zip)
        val entries = LayoutProfileArchive.readZip(ByteArrayInputStream(zip.toByteArray()))
        assertEquals(listOf("布局.json", "second.json"), entries.map { it.name })
        assertEquals(listOf(first.readText(), second.readText()), entries.map { it.json })
    }

    @Test fun importDoesNotOverwriteProfileCreatedAfterPreview() {
        val target = temp.newFile("existing.json").apply { writeText("user data") }
        assertTrue(runCatching { LayoutProfileArchive.writeNewProfile(target, "new data") }.isFailure)
        assertEquals("user data", target.readText())
        val fresh = temp.root.resolve("new.json")
        assertFalse(fresh.exists())
        LayoutProfileArchive.writeNewProfile(fresh, "new data")
        assertEquals("new data", fresh.readText())
    }
}
