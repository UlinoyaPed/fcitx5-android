/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.dialog

import kotlinx.coroutines.runBlocking
import org.fxboomk.fcitx5.android.core.Action
import org.fxboomk.fcitx5.android.core.FcitxAPI
import org.fxboomk.fcitx5.android.core.InputMethodEntry
import org.fxboomk.fcitx5.android.input.keyboard.LangSwitchLongPressBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class InputMethodAvailabilityTest {
    private val enabled = arrayOf(
        InputMethodEntry("keyboard-us", "English", "", "", "", "en", "androidkeyboard", false),
        InputMethodEntry("pinyin", "Pinyin", "", "", "", "zh", "pinyin", false)
    )

    @Test
    fun mixedPickerWithoutRimeReturnsBuiltInsWithoutCallingSchemaApi() = runBlocking {
        val fcitx = api { name ->
            when (name) {
                "enabledIme", "availableIme" -> enabled
                else -> error("Unexpected API call: $name")
            }
        }
        val entries = InputMethodData.resolveInternal(fcitx, LangSwitchLongPressBehavior.BuiltInAndRime)
        assertEquals(enabled.map { it.uniqueName }, entries.map { it.uniqueName })
        assertTrue(entries.none { it.ime || it.rimeSchemaActionId != null })
    }

    @Test
    fun rimeOnlyWithoutPluginReturnsEmptyWithoutCallingSchemaApi() = runBlocking {
        val fcitx = api { name ->
            when (name) {
                "enabledIme", "availableIme" -> enabled
                else -> error("Unexpected API call: $name")
            }
        }
        assertTrue(InputMethodData.resolveInternal(fcitx, LangSwitchLongPressBehavior.RimeOnly).isEmpty())
    }

    @Test
    fun modesWithoutSchemasDoNotQueryOptionalPluginAvailability() = runBlocking {
        val fcitx = api { name ->
            check(name == "enabledIme") { "Unexpected API call: $name" }
            enabled
        }
        for (mode in listOf(LangSwitchLongPressBehavior.Default, LangSwitchLongPressBehavior.BuiltInOnly)) {
            assertEquals(enabled.map { it.uniqueName },
                InputMethodData.resolveInternal(fcitx, mode).map { it.uniqueName })
        }
    }

    @Test
    fun installedRimeCanProvideSchemasBeforeItIsAddedToEnabledList() = runBlocking {
        val rime = InputMethodEntry("rime", "Rime", "", "", "", "zh", "rime", false)
        val schema = Action(42, false, false, false, "", "", "朙月拼音", "", null)
        val fcitx = api { name ->
            when (name) {
                "enabledIme" -> enabled
                "availableIme" -> enabled + rime
                "rimeSchemaActions" -> arrayOf(schema)
                else -> error("Unexpected API call: $name")
            }
        }
        val entries = InputMethodData.resolveInternal(fcitx, LangSwitchLongPressBehavior.BuiltInAndRime)
        assertEquals(listOf("keyboard-us", "pinyin", "rime-schemas", "rime-schema:42"), entries.map { it.uniqueName })
        assertTrue(entries[2].isGroupHeader)
        assertEquals("Rime", entries[2].name)
    }

    private fun api(call: (String) -> Any): FcitxAPI = Proxy.newProxyInstance(
        FcitxAPI::class.java.classLoader,
        arrayOf(FcitxAPI::class.java)
    ) { _, method, _ -> call(method.name) } as FcitxAPI
}
