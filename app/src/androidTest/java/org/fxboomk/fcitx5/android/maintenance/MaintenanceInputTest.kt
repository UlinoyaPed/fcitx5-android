/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.maintenance

import android.graphics.Region
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.text.Spannable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.fcitx.fcitx5.android.common.ipc.IVoiceInputCallback
import org.fcitx.fcitx5.android.common.ipc.IVoiceInputProvider
import org.fxboomk.fcitx5.android.FcitxApplication
import org.fxboomk.fcitx5.android.data.prefs.AppPrefs
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.input.keyboard.KeyAction
import org.fxboomk.fcitx5.android.input.keyboard.KeyActionListener
import org.fxboomk.fcitx5.android.input.keyboard.TextKeyView
import org.fxboomk.fcitx5.android.input.picker.*
import org.fxboomk.fcitx5.android.input.popup.PopupActionListener
import org.fxboomk.fcitx5.android.input.voice.*
import org.fxboomk.fcitx5.android.input.voice.js.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Controlled responses exercise Android requests, editor composition and commits, not ASR quality. */
class MaintenanceInputTest {
    @Test fun javascriptRequestPartialDisplayAndFinalCommitKeepSpacesAndEmoji() {
        MaintenanceImeSession().use { s ->
            s.open()
            val id = "maintenance-${UUID.randomUUID()}"
            val requests = CopyOnWriteArrayList<Pair<String, JSONObject>>()
            val errors = CopyOnWriteArrayList<String>()
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val buffer = Buffer(); request.body!!.writeTo(buffer)
                requests.add(request.url.encodedPath to JSONObject(buffer.readUtf8()))
                val text = if (request.url.encodedPath == "/partial") "hello 👋" else " hello 👋 world "
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .body(JSONObject().put("text", text).toString().toResponseBody()).build()
            }.build()
            val script = """
                var sent=false;
                swan.plugin.register({id:'fixture',name:'Fixture',hosts:['voice.example'],
                features:[{type:'voice.asr',id:'fixture',label:'Fixture',sampleRate:16000}],
                start(ctx){swan.voice.ready();},
                async audio(ctx){if(sent)return;sent=true;
                  const r=await swan.http.post('https://voice.example/partial','',JSON.stringify({pcm:ctx.pcm,sampleRate:ctx.sampleRate}),'{}');
                  swan.voice.partial(JSON.parse(r).text);},
                async stop(ctx){const r=await swan.http.post('https://voice.example/final','',JSON.stringify({sampleRate:ctx.sampleRate}),'{}');
                  swan.voice.final(JSON.parse(r).text);swan.voice.ended();}});
            """.trimIndent()
            val provider = JsVoiceInputProvider(id, { script }, setOf("voice.example"), 16000, "en-US", http)
            JsVoiceHostPermissions.approve(id, setOf("voice.example"))
            try {
                // Warm the JS/JNI bridge using one bounded PCM frame. This is not a cold-start benchmark.
                val ready = AtomicBoolean(); val partial = AtomicBoolean(); val ended = AtomicBoolean()
                provider.startSession(object : IVoiceInputCallback.Stub() {
                    override fun onReady() { ready.set(true) }
                    override fun onVolumeLevel(rms: Int) {}
                    override fun onPartialResult(text: String?) { partial.set(true) }
                    override fun onSegmentFinal(text: String?) {}
                    override fun onSessionEnded() { ended.set(true) }
                    override fun onError(code: Int, message: String?) { errors.add("$code: $message") }
                })
                s.await("JS ready") { ready.get() || errors.isNotEmpty() }; assertTrue(errors.toString(), errors.isEmpty())
                provider.feedAudio(ByteArray(3200), 0, 3200, 0)
                s.await("JS partial") { partial.get() || errors.isNotEmpty() }; assertTrue(errors.toString(), errors.isEmpty())
                provider.endStream(); s.await("JS warm session ended") { ended.get() }; requests.clear()
                VoiceInputProviderRegistry.register(VoiceInputProviderRegistry.Entry(id, "Fixture", s.context.packageName, provider))
                s.main { VoiceInputProviderManager.start(s.service, id, {}, {}, { errors.add(it) }) }
                s.await("partial is displayed as editor composition") {
                    s.activity.editor.text.toString() == "hello 👋" && BaseInputConnection.getComposingSpanStart(s.activity.editor.text) >= 0 || errors.isNotEmpty()
                }
                assertTrue(errors.toString(), errors.isEmpty())
                val pcm = requests.single { it.first == "/partial" }.second
                assertEquals(16000, pcm.getInt("sampleRate"))
                assertTrue(android.util.Base64.decode(pcm.getString("pcm"), android.util.Base64.DEFAULT).isNotEmpty())
                s.main { VoiceInputProviderManager.finish(s.context) }
                s.await("final text commits with insertion spaces") { s.activity.editor.text.toString() == " hello 👋 world " && !VoiceInputProviderManager.isActive() }
                assertEquals(-1, s.main { BaseInputConnection.getComposingSpanStart(s.activity.editor.text) })
                assertEquals(2, requests.size); assertTrue(errors.toString(), errors.isEmpty())
            } finally {
                s.main { VoiceInputProviderManager.stop(s.context) }
                VoiceInputProviderRegistry.unregister(id); provider.stopSession(); JsVoiceHostPermissions.revoke(id)
                http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll()
            }
        }
    }

    @Test fun finishCommitsLastPartialAndLateCallbacksCannotWriteToReplacementEditor() {
        MaintenanceImeSession().use { s ->
            s.open()
            val id = "maintenance-${UUID.randomUUID()}"
            val callback = AtomicReference<IVoiceInputCallback>()
            val errors = CopyOnWriteArrayList<String>()
            val provider = object : IVoiceInputProvider.Stub() {
                override fun isAvailable() = true
                override fun getPreferredConfig() = Bundle().apply { putInt("sampleRate", 16000) }
                override fun configure(params: Bundle?) {}
                override fun startSession(cb: IVoiceInputCallback) { callback.set(cb); cb.onReady() }
                override fun feedAudio(pcm: ByteArray?, offset: Int, len: Int, ptsMs: Long) {}
                override fun endStream() { callback.get().onSessionEnded() }
                override fun cancelSession() {}
                override fun stopSession() {}
            }
            VoiceInputProviderRegistry.register(VoiceInputProviderRegistry.Entry(id, "Fixture", s.context.packageName, provider))
            try {
                s.main { VoiceInputProviderManager.start(s.service, id, {}, {}, { errors.add(it) }) }
                s.await("provider ready") { callback.get() != null }
                callback.get().onPartialResult("你好 👋")
                s.await("partial shown") { s.activity.editor.text.toString() == "你好 👋" }
                s.main { VoiceInputProviderManager.finish(s.context) }
                s.await("last partial committed") { !VoiceInputProviderManager.isActive() && BaseInputConnection.getComposingSpanStart(s.activity.editor.text) < 0 }
                assertEquals("你好 👋", s.main { s.activity.editor.text.toString() })
                callback.set(null)
                s.main { VoiceInputProviderManager.start(s.service, id, {}, {}, { errors.add(it) }) }
                s.await("second provider session ready") { callback.get() != null }
                val stale = callback.get()
                s.main { s.activity.replaceEditor() }
                s.await("replacement editor session") { s.service.currentInputEditorInfo?.fieldId == s.activity.editor.id }
                stale.onSegmentFinal("must not leak"); stale.onSessionEnded(); s.instrumentation.waitForIdleSync()
                assertEquals("", s.main { s.activity.editor.text.toString() }); assertTrue(errors.toString(), errors.isEmpty())
            } finally { s.main { VoiceInputProviderManager.stop(s.context) }; VoiceInputProviderRegistry.unregister(id) }
        }
    }

    @Test fun deniedBinderFallsBackToExternalFloatingServiceAndCommitsTokenBoundResult() {
        MaintenanceImeSession().use { s ->
            s.open()
            val testPackage = s.instrumentation.context.packageName
            val id = "plugin:" + ComponentName(testPackage, "org.fxboomk.fcitx5.android.maintenance.external.DeniedVoiceProviderService").flattenToShortString()
            val errors = CopyOnWriteArrayList<String>()
            assertTrue(VoiceInputProviderManager.listProviders(s.context).any { it.id == id })
            s.main { VoiceInputProviderManager.start(s.service, id, {}, {}, { errors.add(it) }) }
            s.await("external partial composition") { s.activity.editor.text.toString() == "fallback partial" }
            s.await("external final result") { s.activity.editor.text.toString() == " fallback 👋 " && !VoiceInputProviderManager.isActive() }
            assertEquals(-1, s.main { BaseInputConnection.getComposingSpanStart(s.activity.editor.text) })
            assertTrue(errors.toString(), errors.isEmpty())
        }
    }

    @Test fun bothSymbolPanelStylesCommitThroughRealEditor() {
        MaintenanceImeSession().use { s ->
            s.open()
            val pref = AppPrefs.getInstance().symbols.symbolPanelStyle; val original = pref.getValue()
            val recents = FcitxApplication.getInstance().directBootAwareContext.getSharedPreferences("picker_recently_used", Context.MODE_PRIVATE)
            val oldRecent = recents.getString("Symbol", null)
            try {
                recents.edit().remove("Symbol").commit()
                for (style in listOf(SymbolPanelStyle.Sidebar, SymbolPanelStyle.TopBar)) {
                    val ui = s.main {
                        pref.setValue(style); s.activity.editor.setText("")
                        SymbolPanelUi(s.activity, ThemeManager.activeTheme, SymbolCatalogType.Symbols, 6, 20f,
                            DefaultPickerPolicy(), KeyActionListener { action -> if (action is KeyAction.CommitAction) s.service.commitText(action.text) }, PopupActionListener {})
                            .also { panel -> s.activity.addContentView(panel.root, FrameLayout.LayoutParams(-1, 300, Gravity.BOTTOM)); panel.attach(); panel.refresh() }
                    }
                    try {
                        var key: TextKeyView? = null
                        s.await("$style grid cells") {
                            val grid = ui.root.descendants().filterIsInstance<RecyclerView>().firstOrNull { it.layoutManager is GridLayoutManager }
                            key = grid?.descendants()?.filterIsInstance<TextKeyView>()?.firstOrNull { it.isShown && it.width > 0 }
                            key != null
                        }
                        val label = s.main { key!!.mainText.toString().also { key!!.performClick() } }
                        s.await("$style symbol commit") { s.activity.editor.text.toString() == label }
                    } finally { s.main { ui.detach(); (ui.root.parent as? android.view.ViewGroup)?.removeView(ui.root) } }
                }
            } finally {
                s.main { pref.setValue(original) }
                recents.edit().apply { if (oldRecent == null) remove("Symbol") else putString("Symbol", oldRecent) }.commit()
            }
        }
    }

    @Test fun floatingKeyboardResizesRotatesAndKeepsEditorInput() {
        MaintenanceImeSession().use { s ->
            s.open()
            val prefs = AppPrefs.getInstance().symbols.symbolPanelStyle.sharedPreferences
            fun relevant(k: String) = k.startsWith("floating_") || k.startsWith("auto_floating_") || k == "auto_hide_floating_move_handle"
            val saved = prefs.all.filterKeys(::relevant)
            val orientation = s.main { s.activity.requestedOrientation }
            fun field(owner: Any, name: String): View = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as View
            fun drag(view: View, dx: Float, dy: Float) = s.main {
                val now=SystemClock.uptimeMillis()
                for ((action,time,x,y) in listOf(Quad(MotionEvent.ACTION_DOWN,now,4f,4f), Quad(MotionEvent.ACTION_MOVE,now+100,4f+dx,4f+dy), Quad(MotionEvent.ACTION_UP,now+200,4f+dx,4f+dy))) {
                    val event=MotionEvent.obtain(now,time,action,x,y,0); view.dispatchTouchEvent(event); event.recycle()
                }
            }
            try {
                s.await("input view visible") { s.service.inputView?.isShown == true }
                s.main { s.service.inputView!!.let { if (!it.isFloating) it.toggleFloatingMode(true) } }
                s.instrumentation.waitForIdleSync()
                val input = s.main { s.service.inputView!! }
                val keyboard = s.main { field(input,"keyboardView") }
                s.await("floating geometry settled") { keyboard.width > 100 && keyboard.height > 100 }
                val before = s.main { keyboard.width to keyboard.height }
                drag(s.main { field(input,"floatingRightHandle") }, -30f, 0f)
                drag(s.main { field(input,"floatingBottomHandle") }, 0f, -20f)
                s.await("floating resize changes both dimensions") { keyboard.width < before.first && keyboard.height < before.second }
                s.main { s.service.commitText("portrait "); s.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                s.await("landscape editor and floating view") {
                    s.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && s.service.inputView?.let { it.isFloating && it.isShown && it.width > 0 } == true && s.service.currentInputEditorInfo?.fieldId == s.activity.editor.id
                }
                s.main {
                    val region=Region(); s.service.inputView!!.getFloatingKeyboardRegion(region); assertFalse(region.isEmpty)
                    s.service.commitText("landscape 👋")
                }
                s.await("input after rotation") { s.activity.editor.text.toString() == "portrait landscape 👋" }
            } finally {
                s.main { s.activity.requestedOrientation=orientation }
                val edit=prefs.edit(); prefs.all.keys.filter(::relevant).forEach(edit::remove)
                for ((key,value) in saved) when(value) { is Boolean->edit.putBoolean(key,value); is Int->edit.putInt(key,value); is Float->edit.putFloat(key,value); is String->edit.putString(key,value); is Long->edit.putLong(key,value) }
                edit.commit()
            }
        }
    }
    private data class Quad(val action: Int, val time: Long, val x: Float, val y: Float)
}
