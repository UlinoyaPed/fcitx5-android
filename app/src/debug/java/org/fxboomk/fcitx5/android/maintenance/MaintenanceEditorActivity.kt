/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.maintenance

import android.app.Activity
import android.app.AppComponentFactory
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.annotation.RequiresApi
import org.fxboomk.fcitx5.android.input.FcitxInputMethodService
import java.lang.ref.WeakReference

/** Debug-only framework hook; release keeps its original component factory. */
@RequiresApi(28)
class MaintenanceImeFactory : AppComponentFactory() {
    override fun instantiateService(cl: ClassLoader, className: String, intent: Intent?): Service =
        super.instantiateService(cl, className, intent).also {
            if (it is FcitxInputMethodService) service = WeakReference(it)
        }
    companion object { @Volatile var service = WeakReference<FcitxInputMethodService>(null) }
}

class MaintenanceEditorActivity : Activity() {
    lateinit var editor: EditText
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        replaceEditor()
    }
    fun replaceEditor() {
        editor = EditText(this).apply {
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
        setContentView(editor)
        editor.requestFocus()
        editor.post { getSystemService(InputMethodManager::class.java).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT) }
    }
}
