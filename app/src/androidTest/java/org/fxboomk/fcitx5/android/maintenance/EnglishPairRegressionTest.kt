/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.maintenance

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.fxboomk.fcitx5.android.core.CapabilityFlags
import org.fxboomk.fcitx5.android.daemon.FcitxDaemon
import org.fxboomk.fcitx5.android.input.keyboard.switchToEnglishInputMode
import org.junit.Assert.*
import org.junit.Test

class EnglishPairRegressionTest {
    @Test fun standaloneEnglishReturnsToTheNativeEngineInTheSameContext() = runBlocking {
        val name = "standalone-english-regression"
        val connection = FcitxDaemon.connect(name)
        try {
            withTimeout(120_000) {
                connection.runOnReady {
                    val original = enabledIme().map { it.uniqueName }.toTypedArray()
                    val engines = listOf("pinyin", "anthy", "rime")
                    val available = availableIme().map { it.uniqueName }
                    assertTrue("Install both the Anthy and Rime test plugins", available.containsAll(engines))
                    val contextId = 27130
                    try {
                        setEnabledIme((listOf("keyboard-us") + engines).toTypedArray())
                        activate(contextId, "org.ulinoyaped.fcitx5.android.debug")
                        setCapFlags(CapabilityFlags.DefaultFlags)
                        focus(true)
                        for (engine in engines) {
                            activateIme(engine)
                            val before = currentIme()
                            assertEquals(engine, before.uniqueName)
                            assertTrue(switchToEnglishInputMode())
                            assertEquals("keyboard-us", currentIme().uniqueName)
                            assertTrue(switchToEnglishInputMode())
                            assertEquals(engine, currentIme().uniqueName)
                            assertEquals("Retain the engine's native sub-mode", before.subMode, currentIme().subMode)
                        }
                        activateIme("pinyin")
                        assertTrue(switchToEnglishInputMode())
                        activateIme("anthy")
                        assertTrue(switchToEnglishInputMode())
                        assertTrue(switchToEnglishInputMode())
                        assertEquals("anthy", currentIme().uniqueName)
                        enumerateIme()
                        assertNotEquals("anthy", currentIme().uniqueName)
                        focus(false)
                        assertFalse(switchToEnglishInputMode())
                    } finally {
                        focus(false)
                        deactivate(contextId)
                        setEnabledIme(original)
                    }
                }
            }
        } finally {
            FcitxDaemon.disconnect(name)
        }
    }
}
