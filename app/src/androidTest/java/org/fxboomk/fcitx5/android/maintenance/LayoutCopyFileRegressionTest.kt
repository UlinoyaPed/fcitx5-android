/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.maintenance

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.JsonArray
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.data.LayoutCopyAddress
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.data.LayoutCopyFile
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.data.LayoutHeightPercentOverrides
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.data.planLayoutCopy
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LayoutCopyFileRegressionTest {
    @Test fun confirmedCopyRejectsNewerSourceOrDestinationEditsAndRoundTripsOnRetry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val path = File.createTempFile("layout-copy", ".json", context.cacheDir)
        try {
            val file = LayoutCopyFile(path)
            val original = """{"pinyin":[[{"type":"SpaceKey"}]],"rime":[[{"type":"ReturnKey"}]]}"""
            path.writeText(original)
            val source = LayoutCopyAddress("pinyin")
            val destinations = listOf(LayoutCopyAddress("rime"), LayoutCopyAddress("anthy"))
            val defaults = LayoutHeightPercentOverrides(33, 33)
            fun plan(text: String) = planLayoutCopy(file.parse(text), source, destinations, JsonArray(emptyList()), defaults)
            val before = plan(original)
            assertEquals(setOf(destinations.first()), before.overwritten)
            val concurrent = original.replace("ReturnKey", "CommaKey")
            path.writeText(concurrent)
            assertThrows(IllegalStateException::class.java) { file.write(original, before.profile) }
            assertEquals(concurrent, path.readText())

            val confirmed = plan(concurrent)
            file.write(concurrent, confirmed.profile)
            assertEquals(confirmed.profile, file.parse(path.readText()))
        } finally {
            path.delete()
        }
    }
}
