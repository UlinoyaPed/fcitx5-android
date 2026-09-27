/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.dialog

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.fxboomk.fcitx5.android.daemon.FcitxDaemon
import org.fxboomk.fcitx5.android.input.keyboard.LangSwitchLongPressBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LanguageInputMethodPickerTest {
    @Test
    fun daemonConnectionForwardsBothRimeMethods() = runBlocking {
        val name = "LanguageInputMethodPickerTest-forwarding"
        val connection = FcitxDaemon.connect(name)
        try {
            connection.runOnReady {
                rimeSchemaActions()
                assertFalse(activateRimeSchemaAction(-1))
            }
        } finally {
            FcitxDaemon.disconnect(name)
        }
    }

    @Test
    fun missingPluginKeepsMixedPickerUsable() = runBlocking {
        val name = "LanguageInputMethodPickerTest-missing-plugin"
        val connection = FcitxDaemon.connect(name)
        try {
            connection.runOnReady {
                assumeTrue("Requires an app installation without the Rime plugin",
                    availableIme().none { it.addon == "rime" })
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val entries = InputMethodData.resolve(this, context, LangSwitchLongPressBehavior.BuiltInAndRime)
                assertEquals(enabledIme().map { it.uniqueName }, entries.map { it.uniqueName })
                assertTrue(entries.isNotEmpty())
                assertTrue(entries.none { it.ime || it.rimeSchemaActionId != null })
                assertTrue(InputMethodData.resolve(this, context, LangSwitchLongPressBehavior.RimeOnly).isEmpty())
            }
        } finally {
            FcitxDaemon.disconnect(name)
        }
    }
}
