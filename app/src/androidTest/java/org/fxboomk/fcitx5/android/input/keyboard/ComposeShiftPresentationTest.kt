package org.fxboomk.fcitx5.android.input.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.allViews
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.core.InputMethodEntry
import org.fxboomk.fcitx5.android.data.prefs.AppPrefs
import org.fxboomk.fcitx5.android.data.theme.ThemePreset
import org.fxboomk.fcitx5.android.input.config.ConfigProviders
import org.fxboomk.fcitx5.android.input.config.MemoryConfigProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeShiftPresentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun bothShiftKeysShowFilledAndUnderlinedStatesDuringComposition() = withLayout {
        listOf(false, true).forEach { labeled ->
            val keyboard = createKeyboard(labeled)
            keyboard.onCompositionStateChanged(true)
            send(keyboard, KeyAction.CapsAction(false))
            assertIcon(keyboard, R.id.button_caps, R.drawable.ic_capslock_once)
            assertIcon(keyboard, R.id.button_lang, R.drawable.ic_capslock_once)
            keyboard.onCompositionStateChanged(true)
            assertIcon(keyboard, R.id.button_lang, R.drawable.ic_capslock_once)
            send(keyboard, KeyAction.CapsAction(true))
            assertIcon(keyboard, R.id.button_caps, R.drawable.ic_capslock_lock)
            assertIcon(keyboard, R.id.button_lang, R.drawable.ic_capslock_lock)
        }
    }

    @Test fun clearingCompositionRestoresLowercaseAndLanguageIconForOnceAndLock() = withLayout {
        listOf(false, true).forEach { lock ->
            val keyboard = createKeyboard(labeled = true)
            keyboard.onCompositionStateChanged(true)
            send(keyboard, KeyAction.CapsAction(lock))
            keyboard.onCompositionStateChanged(false)
            assertIcon(keyboard, R.id.button_caps, R.drawable.ic_capslock_none)
            assertIcon(keyboard, R.id.button_lang, R.drawable.ic_baseline_language_24)
            val actions = mutableListOf<KeyAction>()
            keyboard.keyActionListener = KeyActionListener { action, _ -> actions += action }
            send(keyboard, KeyAction.CommitAction("q", followShift = true))
            assertEquals("q", actions.filterIsInstance<KeyAction.CommitAction>().single().text)
            keyboard.onCompositionStateChanged(true)
            assertIcon(keyboard, R.id.button_lang, R.drawable.ic_capslock_none)
        }
    }

    @Test fun repeatedEmptyNotificationsKeepShiftChosenOutsideComposition() = withLayout {
        val keyboard = createKeyboard(labeled = false)
        send(keyboard, KeyAction.CapsAction(true))
        keyboard.onCompositionStateChanged(false)
        keyboard.onCompositionStateChanged(false)
        assertIcon(keyboard, R.id.button_caps, R.drawable.ic_capslock_lock)
        assertIcon(keyboard, R.id.button_lang, R.drawable.ic_baseline_language_24)
    }

    @Test fun originalShiftIdentityDoesNotOverwriteItsActiveLanguageRole() = withLayout {
        val keyboard = createKeyboard(labeled = false, reverseCaps = true)
        keyboard.onCompositionStateChanged(true)
        send(keyboard, KeyAction.CapsAction(false))
        assertIcon(keyboard, R.id.button_caps, R.drawable.ic_baseline_language_24)
        assertIcon(keyboard, R.id.button_lang, R.drawable.ic_capslock_once)
    }

    private fun createKeyboard(labeled: Boolean, reverseCaps: Boolean = false): TextKeyboard {
        val label = if (labeled) ",\"swipeUpLabel\":\"UP\"" else ""
        val reverse = if (reverseCaps) ",\"composeOverride\":{\"type\":\"LanguageKey\"}" else ""
        val json = Json.parseToJsonElement("""{"test":[[
            {"type":"CapsKey"$reverse},
            {"type":"LanguageKey","composeOverride":{"type":"CapsKey"$label}},
            {"type":"AlphabetKey","main":"q","alt":"!"}
        ]]}""").jsonObject
        ConfigProviders.provider = MemoryConfigProvider(json, ConfigProviders.provider)
        TextKeyboard.clearCachedKeyDefLayouts()
        return TextKeyboard(context, ThemePreset.MaterialLight, InputMethodEntry("test")).apply {
            onCompositionStateChanged(false)
        }
    }

    private fun send(keyboard: TextKeyboard, action: KeyAction) {
        TextKeyboard::class.java.getDeclaredMethod(
            "onAction", KeyAction::class.java, KeyActionListener.Source::class.java
        ).apply { isAccessible = true }.invoke(keyboard, action, KeyActionListener.Source.Keyboard)
    }

    private fun assertIcon(keyboard: TextKeyboard, tag: Int, resource: Int) {
        val view = keyboard.allViews.filterIsInstance<KeyView>().single { it.tag == tag }
        val image = (view as KeyViewWithImage).img
        val expected = requireNotNull(AppCompatResources.getDrawable(context, resource)).mutate()
        expected.setTintList(image.imageTintList)
        assertTrue("Wrong icon for key $tag, expected $resource", pixels(expected).contentEquals(pixels(image.drawable)))
    }

    private fun pixels(drawable: Drawable): IntArray {
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val original = android.graphics.Rect(drawable.bounds)
        drawable.setBounds(0, 0, 48, 48)
        drawable.draw(Canvas(bitmap))
        drawable.bounds = original
        return IntArray(48 * 48).also { bitmap.getPixels(it, 0, 48, 0, 0, 48, 48) }
    }

    private fun withLayout(block: () -> Unit) = instrumentation.runOnMainSync {
        val provider = ConfigProviders.provider
        val prefs = AppPrefs.getInstance().keyboard
        val originalCaps = prefs.capsKeyBehavior.getValue()
        val originalLanguage = prefs.showLangSwitchKey.getValue()
        try {
            prefs.capsKeyBehavior.setValue(CapsKeyBehavior.Default)
            prefs.showLangSwitchKey.setValue(true)
            block()
        } finally {
            prefs.capsKeyBehavior.setValue(originalCaps)
            prefs.showLangSwitchKey.setValue(originalLanguage)
            ConfigProviders.provider = provider
            TextKeyboard.clearCachedKeyDefLayouts()
        }
    }
}
