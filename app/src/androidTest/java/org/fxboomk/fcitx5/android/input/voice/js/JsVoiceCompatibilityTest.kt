package org.fxboomk.fcitx5.android.input.voice.js

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsVoiceCompatibilityTest {
    @Test fun bundledAsrScriptsLoadInQuickJsAndDoNotGrantNetworkConsent() = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        for (name in listOf("doubao-asr", "qwen-asr", "tencent-asr")) {
            val script = assets.open("bundled/js-voice/$name.js").bufferedReader().use { it.readText() }
            val result = JsVoiceImporter.inspect(script, "zh-CN", emptySet())
            assertTrue("Failed to inspect $name: $result", result is JsVoiceImportResult.Success)
            val meta = (result as JsVoiceImportResult.Success).meta
            assertTrue("$name must declare a network host", meta.hosts.isNotEmpty())
        }
        assertFalse(JsVoiceHostPermissions.isApproved("maintenance-unapproved-voice-test", setOf("example.com")))
    }
}
