package org.fxboomk.fcitx5.android.input

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.fxboomk.fcitx5.android.core.CapabilityFlags
import org.fxboomk.fcitx5.android.daemon.FcitxDaemon
import org.fxboomk.fcitx5.android.input.config.UserConfigFiles
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
                    val schemes = listOf("shuangpin-xiaohe", "shuangpin-ziranma")
                    schemes.forEach { assertTrue("Missing per-scheme entry: $it", it in available) }
                    val originals = schemes.associateWith {
                        val file = UserConfigFiles.pinyinImConfig(it)
                        if (file.isFile) file.readBytes() else null
                    }
                    try {
                        setEnabledIme((enabled.toList() + "pinyin" + schemes).distinct().toTypedArray())
                        activate(12345, "org.ulinoyaped.fcitx5.android.debug")
                        setCapFlags(CapabilityFlags.DefaultFlags)
                        focus(true)
                        activateIme("pinyin")
                        assertEquals("pinyin", currentIme().uniqueName)
                        reset()
                        "nihao".forEach { sendKey(it) }
                        assertTrue("Quantized model should load and produce native candidates", getCandidates(0, 10).isNotEmpty())
                        for (scheme in schemes) {
                            reset()
                            activateIme(scheme)
                            assertEquals(scheme, currentIme().uniqueName)
                            val configuration = checkNotNull(getImConfig(scheme).findByName("cfg"))
                            setImConfig(scheme, configuration)
                            assertTrue("Java rename path must match native persistence: $scheme",
                                UserConfigFiles.pinyinImConfig(scheme).isFile)
                            "ni".forEach { sendKey(it) }
                            assertTrue("Scheme must produce native candidates: $scheme", getCandidates(0, 10).isNotEmpty())
                        }
                    } finally {
                        originals.forEach { (scheme, bytes) ->
                            val file = UserConfigFiles.pinyinImConfig(scheme)
                            if (bytes == null) file.delete() else file.writeBytes(bytes)
                        }
                        reloadConfig()
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
