package org.fxboomk.fcitx5.android.data.pinyin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.fxboomk.fcitx5.android.input.config.UserConfigFiles
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CustomShuangpinPreservationTest {
    private val source = "maintenance-source"
    private val target = "maintenance-target"
    private val foreign = "maintenance-foreign"
    private fun unique(id: String) = "shuangpin-$id"
    private fun entry(id: String) = File(checkNotNull(UserConfigFiles.inputMethodConfDir()), unique(id) + ".conf")
    private fun config(id: String) = UserConfigFiles.pinyinImConfig(unique(id))
    private val layout get() = checkNotNull(UserConfigFiles.textKeyboardLayoutJson("maintenance-store-test"))

    private fun isolated(block: () -> Unit) {
        val files = listOf(UserConfigFiles.customShuangpinSchemeJson(), UserConfigFiles.customShuangpinSpDat(),
            entry(source), entry(target), entry(foreign), config(source), config(target), layout)
        val original = files.associateWith { if (it.isFile) it.readBytes() else null }
        try {
            files.forEach { it.delete() }
            CustomShuangpinSchemeStore.save(CustomShuangpinSchemeCodec.defaultScheme().copy(id = source)).getOrThrow()
            block()
        } finally {
            original.forEach { (file, bytes) ->
                if (bytes == null) file.delete()
                else { file.parentFile?.mkdirs(); file.writeBytes(bytes) }
            }
            CustomShuangpinSchemeStore::class.java.getDeclaredField("cachedScheme").apply {
                isAccessible = true
                set(null, null)
            }
        }
    }

    @Test fun initializationPreservesOtherEntriesAndManualEntrySettings() = isolated {
        val external = "[InputMethod]\nName=User supplied\nAddon=pinyin\n"
        entry(foreign).writeText(external)
        entry(source).writeText(external + "Label=User\n")
        CustomShuangpinSchemeStore.ensureInitialized()
        assertEquals(external, entry(foreign).readText())
        assertEquals(external + "Label=User\n", entry(source).readText())
        assertFalse(CustomShuangpinSchemeStore.isStaleCustomEntry(unique(foreign)))
    }

    @Test fun renameCollisionDoesNotChangeSchemeOrConfigurations() = isolated {
        config(target).parentFile?.mkdirs()
        config(target).writeText("Existing target preferences")
        val before = UserConfigFiles.customShuangpinSchemeJson().readText()
        assertTrue(CustomShuangpinSchemeStore.rename("New name", target).isFailure)
        assertEquals(before, UserConfigFiles.customShuangpinSchemeJson().readText())
        assertEquals("Existing target preferences", config(target).readText())
        assertTrue(entry(source).isFile)
        assertFalse(entry(target).exists())
    }

    @Test fun explicitRenameMovesOnlyItsOwnFilesAndPreservesLayoutMetadata() = isolated {
        entry(source).writeText("[InputMethod]\nName=My own name\nAddon=pinyin\nLabel=Own\n")
        entry(foreign).writeText("Keep this independent entry")
        config(source).parentFile?.mkdirs()
        config(source).writeText("[User]\nSelected=True\n")
        layout.parentFile?.mkdirs()
        layout.writeText("""{"${unique(source)}":{"__meta__":{"heightPercent":123},"default":[[{"type":"AlphabetKey","main":"A","alt":"@"}]]},"default":[[{"type":"SpaceKey"}]]}""")
        val before = Json.parseToJsonElement(layout.readText()).jsonObject
        CustomShuangpinSchemeStore.rename("My renamed scheme", target).getOrThrow()
        val after = Json.parseToJsonElement(layout.readText()).jsonObject
        assertEquals(target, CustomShuangpinSchemeStore.load().id)
        assertEquals(before[unique(source)], after[unique(target)])
        assertEquals(before["default"], after["default"])
        assertFalse(unique(source) in after)
        assertEquals("[User]\nSelected=True\n", config(target).readText())
        assertFalse(config(source).exists())
        assertEquals("[InputMethod]\nName=My own name\nAddon=pinyin\nLabel=Own\n", entry(target).readText())
        assertFalse(entry(source).exists())
        assertEquals("Keep this independent entry", entry(foreign).readText())
    }
}
