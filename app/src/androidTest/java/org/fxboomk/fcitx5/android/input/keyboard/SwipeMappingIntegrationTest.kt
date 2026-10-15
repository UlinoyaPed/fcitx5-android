package org.fxboomk.fcitx5.android.input.keyboard

import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.data.prefs.AppPrefs
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.fxboomk.fcitx5.android.input.config.ConfigProvider
import org.fxboomk.fcitx5.android.input.config.ConfigProviders
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SwipeMappingIntegrationTest {
    @Test fun dispatchKeepsLetterShiftAcrossEveryPunctuationStrategy() = fixture {
        for (strategy in PunctuationSwipeStrategy.entries) {
            val (keyboard, output) = keyboard()
            send(keyboard, KeyAction.CapsAction(false))
            send(keyboard, punctuationSwipeAction(KeyAction.CommitAction("ß", followShift = true), strategy))
            send(keyboard, punctuationSwipeAction(KeyAction.CommitAction("ä", followShift = true), strategy))
            assertEquals(listOf("ẞ", "ä"), output.map { it.text })
        }
    }

    @Test fun explicitSymbolMappingWorksAndDoesNotConsumeLetterShift() = fixture {
        val (keyboard, output) = keyboard()
        send(keyboard, KeyAction.CapsAction(false))
        send(keyboard, punctuationSwipeAction(KeyAction.CommitAction("?"), PunctuationSwipeStrategy.Raw))
        send(keyboard, punctuationSwipeAction(KeyAction.CommitAction("ß", followShift = true), PunctuationSwipeStrategy.Raw))
        assertEquals(listOf("!", "ẞ"), output.map { it.text })
    }

    @Test fun unmappedCapsSymbolsKeepTheChosenPunctuationStrategy() = fixture {
        val (keyboard, output) = keyboard()
        send(keyboard, KeyAction.CapsAction(true))
        send(keyboard, punctuationSwipeAction(KeyAction.CommitAction(","), PunctuationSwipeStrategy.FollowInputMode))
        assertTrue(output.single().followPunctuationMode)
    }

    @Test fun letterOptOutStillWinsOverSymbolMapping() = fixture {
        AppPrefs.getInstance().keyboard.swipeLettersFollowShift.setValue(false)
        val (keyboard, output) = keyboard()
        send(keyboard, KeyAction.CapsAction(true))
        send(keyboard, punctuationSwipeAction(KeyAction.CommitAction("ä", followShift = true), PunctuationSwipeStrategy.Raw))
        assertEquals("ä", output.single().text)
    }

    private fun keyboard(): Pair<TextKeyboard, MutableList<KeyAction.CommitAction>> {
        val output = mutableListOf<KeyAction.CommitAction>()
        val keyboard = TextKeyboard(InstrumentationRegistry.getInstrumentation().targetContext, ThemePreset.MaterialLight)
        keyboard.keyActionListener = KeyActionListener { action, _ -> if (action is KeyAction.CommitAction) output += action }
        return keyboard to output
    }
    private fun send(keyboard: TextKeyboard, action: KeyAction) {
        TextKeyboard::class.java.getDeclaredMethod("onAction", KeyAction::class.java, KeyActionListener.Source::class.java)
            .apply { isAccessible = true }.invoke(keyboard, action, KeyActionListener.Source.Keyboard)
    }
    private fun fixture(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalProvider = ConfigProviders.provider
        val pref = AppPrefs.getInstance().keyboard.swipeLettersFollowShift
        val original = pref.getValue()
        val file = File(context.cacheDir, "swipe-mapping-integration.json")
        file.writeText("""{"?":["？","!"],"ä":["","UNWANTED"]}""")
        try {
            ConfigProviders.provider = object : ConfigProvider by originalProvider {
                override fun swipeSymbolMappingFile() = file
            }
            SwipeSymbolMappingStore.invalidate()
            pref.setValue(true)
            block()
        } finally {
            ConfigProviders.provider = originalProvider
            SwipeSymbolMappingStore.invalidate()
            pref.setValue(original)
            file.delete()
        }
    }
}
