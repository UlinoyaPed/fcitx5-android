/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.maintenance

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import org.fxboomk.fcitx5.android.input.FcitxInputMethodService
import org.fxboomk.fcitx5.android.input.voice.VoiceInputProviderManager
import org.junit.Assert.assertTrue

internal class MaintenanceImeSession : AutoCloseable {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    private val ime = "${context.packageName}/org.fxboomk.fcitx5.android.input.FcitxInputMethodService"
    private val oldIme = shell("settings get secure default_input_method").trim()
    private val wasEnabled = shell("ime list -s").lineSequence().any { it.trim() == ime }
    lateinit var activity: MaintenanceEditorActivity
    val service: FcitxInputMethodService get() = checkNotNull(MaintenanceImeFactory.service.get())
    fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command)
        .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText() }
    fun open(): MaintenanceImeSession {
        shell("pm grant ${context.packageName} android.permission.RECORD_AUDIO")
        shell("ime enable $ime"); shell("ime set $ime")
        activity = instrumentation.startActivitySync(Intent(context, MaintenanceEditorActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MaintenanceEditorActivity
        await("active editor and focused IME") {
            MaintenanceImeFactory.service.get()?.let {
                it.currentInputEditorInfo?.fieldId == activity.editor.id && it.editorSession.capture() != null
            } == true
        }
        instrumentation.waitForIdleSync()
        return this
    }
    fun <T> main(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
    fun await(message: String, timeoutMs: Long = 20000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (main(condition)) return
            SystemClock.sleep(40)
        }
        assertTrue("Timed out: $message", main(condition))
    }
    override fun close() {
        main { VoiceInputProviderManager.stop(context); if (::activity.isInitialized) activity.finish() }
        if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
        if (!wasEnabled) shell("ime disable $ime")
    }
}
internal fun View.descendants(): Sequence<View> = sequence {
    yield(this@descendants)
    if (this@descendants is ViewGroup) for (i in 0 until childCount) yieldAll(getChildAt(i).descendants())
}
