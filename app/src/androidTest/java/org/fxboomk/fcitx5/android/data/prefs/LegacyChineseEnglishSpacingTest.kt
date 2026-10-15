package org.fxboomk.fcitx5.android.data.prefs

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LegacyChineseEnglishSpacingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun importsOldTrueAndFalseWithoutReadingOtherSections() = fixture { prefs, file ->
        listOf(true, false).forEach { enabled ->
            prefs.edit().clear().commit()
            file.writeText("[Other]\nInsertSpaceBetweenChineseAndEnglish=${!enabled}\n[Behavior]\nInsertSpaceBetweenChineseAndEnglish=$enabled\n")
            migrateLegacyChineseEnglishSpacing(prefs, file)
            assertEquals(enabled, prefs.getBoolean("insert_space_between_chinese_and_english", !enabled))
        }
    }

    @Test fun existingChoiceWinsAndMigrationRunsOnlyOnce() = fixture { prefs, file ->
        file.writeText("[Behavior]\nInsertSpaceBetweenChineseAndEnglish=True\n")
        prefs.edit().putBoolean("insert_space_between_chinese_and_english", false).commit()
        migrateLegacyChineseEnglishSpacing(prefs, file)
        assertFalse(prefs.getBoolean("insert_space_between_chinese_and_english", true))
        prefs.edit().clear().commit()
        migrateLegacyChineseEnglishSpacing(prefs, file)
        file.writeText("[Behavior]\nInsertSpaceBetweenChineseAndEnglish=False\n")
        migrateLegacyChineseEnglishSpacing(prefs, file)
        assertTrue(prefs.getBoolean("insert_space_between_chinese_and_english", false))
    }

    @Test fun newInstallDefaultsOffAndIoFailureDoesNotPersistAChoice() = fixture { prefs, file ->
        file.delete()
        migrateLegacyChineseEnglishSpacing(prefs, file)
        assertFalse(prefs.getBoolean("insert_space_between_chinese_and_english", true))
        prefs.edit().clear().commit()
        file.mkdir()
        migrateLegacyChineseEnglishSpacing(prefs, file)
        assertFalse(prefs.contains("insert_space_between_chinese_and_english"))
    }

    private fun fixture(block: (android.content.SharedPreferences, File) -> Unit) {
        val prefs = context.getSharedPreferences("legacy_spacing_test", Context.MODE_PRIVATE)
        val file = File(context.cacheDir, "legacy-spacing-test-config")
        prefs.edit().clear().commit()
        file.deleteRecursively()
        try { block(prefs, file) } finally { prefs.edit().clear().commit(); file.deleteRecursively() }
    }
}
