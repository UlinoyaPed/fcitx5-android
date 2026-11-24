/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.input.keyboard

import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.fxboomk.fcitx5.android.core.FcitxAPI
import org.fxboomk.fcitx5.android.core.InputMethodEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EnglishInputSwitcherTest {
    @Test fun dispatchesOnlyTheNativeStandalonePairAndPropagatesFailure() = runBlocking {
        val entries = arrayOf(
            entry("engine-with-ascii-mode", "en", "test-engine"),
            entry("renamed-standalone-keyboard", "en_GB", "androidkeyboard")
        )
        val calls = mutableListOf<String>()
        val api = api(entries, calls, false)
        assertFalse(api.switchToEnglishInputMode())
        assertEquals(listOf("renamed-standalone-keyboard"), calls)
    }

    @Test fun missingStandaloneKeyboardDoesNotSwitchToAnotherEngine() = runBlocking {
        val calls = mutableListOf<String>()
        val api = api(arrayOf(entry("engine-with-ascii-mode", "en", "test-engine")), calls, true)
        assertFalse(api.switchToEnglishInputMode())
        assertEquals(emptyList<String>(), calls)
    }

    private fun entry(id: String, language: String, addon: String) =
        InputMethodEntry(id, "Localized display name", "", "", "", language, addon, false)

    private fun api(entries: Array<InputMethodEntry>, calls: MutableList<String>, result: Boolean) =
        Proxy.newProxyInstance(
            FcitxAPI::class.java.classLoader, arrayOf(FcitxAPI::class.java)
        ) { _, method, args ->
            when (method.name) {
                "availableIme" -> entries
                "switchInputMethodPair" -> {
                    calls.add(args[0] as String)
                    result
                }
                else -> error("Unexpected Fcitx API: ${method.name}")
            }
        } as FcitxAPI
}
