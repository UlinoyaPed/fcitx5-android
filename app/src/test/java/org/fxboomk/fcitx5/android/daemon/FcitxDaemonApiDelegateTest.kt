/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fxboomk.fcitx5.android.daemon

import java.lang.reflect.Modifier
import org.fxboomk.fcitx5.android.core.FcitxAPI
import org.junit.Assert.assertTrue
import org.junit.Test

class FcitxDaemonApiDelegateTest {
    @Test
    fun daemonDelegateImplementsEveryApiMethod() {
        // Inspect the compiled delegate without starting Android or the native engine.
        // Incremental Kotlin builds can otherwise keep an old `FcitxAPI by realFcitx`
        // implementation after the interface gains a new method.
        val delegate = Class.forName(
            "org.fxboomk.fcitx5.android.daemon.FcitxDaemon\$fcitxImpl\$2\$1",
            false,
            javaClass.classLoader,
        )
        val missing = FcitxAPI::class.java.methods.filter { method ->
            Modifier.isAbstract(method.modifiers) &&
                Modifier.isAbstract(delegate.getMethod(method.name, *method.parameterTypes).modifiers)
        }
        assertTrue("Daemon has no concrete forwarding methods: $missing", missing.isEmpty())
    }
}
