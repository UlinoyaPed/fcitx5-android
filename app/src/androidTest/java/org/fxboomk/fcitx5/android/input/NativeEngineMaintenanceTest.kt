package org.fxboomk.fcitx5.android.input

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.fxboomk.fcitx5.android.core.CapabilityFlags
import org.fxboomk.fcitx5.android.daemon.FcitxDaemon
import org.junit.Assert.*
import org.junit.Test

/** Loads the packaged native engine/model, without contacting an AI service. */
class NativeEngineMaintenanceTest {
    @Test fun pinyinModelProducesCandidatesAndTableEngineRemainsAvailable() = runBlocking {
        val name = "maintenance-native-engine-test"
        val connection = FcitxDaemon.connect(name)
        try {
            withTimeout(60000) {
                connection.runOnReady {
                    val enabled = enabledIme().map { it.uniqueName }.toTypedArray()
                    val active = currentIme().uniqueName
                    val available = availableIme().map { it.uniqueName }
                    assertTrue("Pinyin must remain packaged", "pinyin" in available)
                    assertTrue("Existing Wubi tables must survive update", "wbx" in available)
                    try {
                        setEnabledIme((enabled.toList() + "pinyin").distinct().toTypedArray())
                        activate(12345, "org.ulinoyaped.fcitx5.android.debug")
                        setCapFlags(CapabilityFlags.DefaultFlags)
                        focus(true)
                        activateIme("pinyin")
                        assertEquals("pinyin", currentIme().uniqueName)
                        reset()
                        "nihao".forEach { sendKey(it) }
                        assertTrue("Quantized model should load and produce native candidates", getCandidates(0, 10).isNotEmpty())
                    } finally {
                        reset()
                        setEnabledIme(enabled)
                        if (active.isNotBlank()) activateIme(active)
                        focus(false)
                        deactivate(12345)
                    }
                }
            }
        } finally { FcitxDaemon.disconnect(name) }
    }
}
