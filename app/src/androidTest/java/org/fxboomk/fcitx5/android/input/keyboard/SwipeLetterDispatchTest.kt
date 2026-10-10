package org.fxboomk.fcitx5.android.input.keyboard

import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.data.prefs.AppPrefs
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeLetterDispatchTest {
    @Test fun onceShiftIsConsumedByLettersAndNotByPunctuation() = onMain {
        val keyboard = TextKeyboard(InstrumentationRegistry.getInstrumentation().targetContext, ThemePreset.MaterialLight)
        val actions = mutableListOf<KeyAction>()
        keyboard.keyActionListener = KeyActionListener { action, _ -> actions.add(action) }
        val method = TextKeyboard::class.java.getDeclaredMethod("onAction", KeyAction::class.java, KeyActionListener.Source::class.java).apply { isAccessible = true }
        fun send(action: KeyAction) { method.invoke(keyboard, action, KeyActionListener.Source.Keyboard) }
        send(KeyAction.CapsAction(false))
        send(KeyAction.CommitAction("?", followShift = true))
        send(KeyAction.CommitAction("ß", followShift = true))
        send(KeyAction.CommitAction("ä", followShift = true))
        assertEquals(listOf("?", "ẞ", "ä"), actions.filterIsInstance<KeyAction.CommitAction>().map { it.text })
    }
    @Test fun capsLockAndOptOutRespectLiteralAndUppercaseHintActions() = onMain {
        val keyboard = TextKeyboard(InstrumentationRegistry.getInstrumentation().targetContext, ThemePreset.MaterialLight)
        val actions = mutableListOf<KeyAction>()
        keyboard.keyActionListener = KeyActionListener { action, _ -> actions.add(action) }
        val method = TextKeyboard::class.java.getDeclaredMethod("onAction", KeyAction::class.java, KeyActionListener.Source::class.java).apply { isAccessible = true }
        fun send(action: KeyAction) { method.invoke(keyboard, action, KeyActionListener.Source.Keyboard) }
        send(KeyAction.CapsAction(true))
        send(KeyAction.CommitAction("α", followShift = true))
        send(KeyAction.CommitAction("ö", followShift = true))
        send(KeyAction.CommitAction("literal"))
        AppPrefs.getInstance().keyboard.swipeLettersFollowShift.setValue(false)
        send(KeyAction.CommitAction("ä", followShift = true))
        assertEquals(listOf("Α", "Ö", "literal", "ä"), actions.filterIsInstance<KeyAction.CommitAction>().map { it.text })
    }
    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val pref = AppPrefs.getInstance().keyboard.swipeLettersFollowShift
            val old = pref.getValue()
            try { pref.setValue(true); block() } finally { pref.setValue(old) }
        }
    }
}
