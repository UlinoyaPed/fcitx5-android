/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.input.keyboard

import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.fxboomk.fcitx5.android.core.FcitxAPI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EnglishInputSwitcherTest {
    @Test fun dispatchesOnlyTheNativeStandalonePairAndPropagatesFailure() = runBlocking {
        val api = Proxy.newProxyInstance(
            FcitxAPI::class.java.classLoader, arrayOf(FcitxAPI::class.java)
        ) { _, method, args ->
            assertEquals("switchInputMethodPair", method.name)
            assertEquals("keyboard-us", args[0])
            false
        } as FcitxAPI
        assertFalse(api.switchToEnglishInputMode())
    }
}
