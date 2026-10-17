/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior

import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.input.config.ConfigProviders
import org.fxboomk.fcitx5.android.input.keyboard.DefaultSwipeSymbolMapping
import org.fxboomk.fcitx5.android.input.keyboard.SwipeSymbolMappingStore
import org.fxboomk.fcitx5.android.utils.saveIconTint
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.resources.styledColor
import splitties.views.backgroundColor
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import java.io.File

private val prettyJson = Json { prettyPrint = true }

class SwipeSymbolMappingEditorActivity : AppCompatActivity() {

    /** 一行映射：原始半角标点（key） -> 全角 / 大写两列。 */
    private class Row(var full: String, var upper: String)

    private val entries = linkedMapOf<String, Row>()
    private var originalSnapshot: Map<String, Pair<String, String>> = emptyMap()
    private var saveMenuItem: MenuItem? = null
    private var adapter: MappingAdapter? = null

    private val mappingFile: File? by lazy { ConfigProviders.provider.swipeSymbolMappingFile() }

    private val toolbar by lazy {
        Toolbar(this).apply {
            backgroundColor = styledColor(android.R.attr.colorPrimary)
            elevation = dp(4f)
        }
    }

    /** 右下角圆形"加号"按钮，与「输入法」页右下角的添加按钮样式一致。 */
    private val fab by lazy {
        FloatingActionButton(this).apply {
            imageDrawable = drawable(R.drawable.ic_baseline_plus_24)!!.apply {
                setTint(styledColor(android.R.attr.colorForegroundInverse))
            }
            contentDescription = getString(R.string.swipe_symbol_add_mapping)
            setOnClickListener { openEditor(null) }
        }
    }

    private val usageHint by lazy {
        TextView(this).apply {
            text = getString(R.string.swipe_symbol_editor_hint)
            textSize = 12f
            setTextColor(styledColor(android.R.attr.textColorSecondary))
            setPadding(0, dp(8), 0, dp(8))
        }
    }

    private val headerRow by lazy { buildHeaderRow() }

    private val recyclerView by lazy {
        RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@SwipeSymbolMappingEditorActivity)
            isNestedScrollingEnabled = false
            clipToPadding = false
        }
    }

    private val mainContainer by lazy {
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
            addView(usageHint)
            addView(headerRow)
            addView(recyclerView)
        }
    }

    private val ui by lazy {
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar, LinearLayout.LayoutParams(matchParent, wrapContent))
            addView(
                FrameLayout(this@SwipeSymbolMappingEditorActivity).apply {
                    addView(mainContainer, FrameLayout.LayoutParams(matchParent, matchParent))
                    addView(
                        fab,
                        FrameLayout.LayoutParams(wrapContent, wrapContent).apply {
                            gravity = Gravity.BOTTOM or Gravity.END
                            val m = dp(16)
                            setMargins(m, m, m, m)
                        }
                    )
                },
                LinearLayout.LayoutParams(matchParent, 0).apply { weight = 1f }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(ui)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.edit_swipe_symbol_mapping)

        val toolbarBaseTopPadding = toolbar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = toolbarBaseTopPadding + statusTop)
            insets
        }
        ViewCompat.requestApplyInsets(toolbar)

        // 末行可滚动到悬浮按钮上方：列表底部留出 FAB 高度 + 边距的空间。
        fab.doOnLayout { fab ->
            recyclerView.updatePadding(bottom = fab.height + dp(16))
        }

        loadState()
        adapter = MappingAdapter()
        recyclerView.adapter = adapter
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        saveMenuItem = menu.add(Menu.NONE, MENU_SAVE_ID, Menu.NONE, getString(R.string.save)).apply {
            setIcon(R.drawable.ic_baseline_save_24)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        menu.add(Menu.NONE, MENU_RESET_ID, Menu.NONE, getString(R.string.reset))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        updateSaveButtonState()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        MENU_SAVE_ID -> { saveMapping(); true }
        MENU_RESET_ID -> { confirmResetAll(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun defaultSnapshot(): Map<String, Pair<String, String>> =
        DefaultSwipeSymbolMapping.mapValues { it.value.fullWidth to it.value.upper }

    private fun loadState() {
        val loaded = runCatching {
            ConfigProviders.readSwipeSymbolMapping<Map<String, List<String>>>()?.value
        }.getOrNull()
        val base: Map<String, Pair<String, String>> = loaded
            ?.takeIf { it.isNotEmpty() }
            ?.mapValues { (_, cols) -> cols.getOrElse(0) { "" } to cols.getOrElse(1) { "" } }
            ?: defaultSnapshot()
        entries.clear()
        base.toSortedMap().forEach { (k, v) -> entries[k] = Row(v.first, v.second) }
        originalSnapshot = snapshot()
    }

    private fun snapshot(): Map<String, Pair<String, String>> =
        entries.toSortedMap().mapValues { (_, row) -> row.full to row.upper }

    private fun hasChanges(): Boolean = snapshot() != originalSnapshot

    private fun saveMapping() {
        val file = mappingFile ?: run {
            showToast(getString(R.string.cannot_resolve_swipe_symbol_mapping))
            return
        }
        file.parentFile?.mkdirs()
        val jsonElement = JsonObject(entries.toSortedMap().mapValues { (_, row) ->
            JsonArray(listOf(JsonPrimitive(row.full), JsonPrimitive(row.upper)))
        })
        file.writeText(prettyJson.encodeToString(jsonElement) + "\n")
        ConfigProviders.ensureWatching()
        SwipeSymbolMappingStore.invalidate()
        originalSnapshot = snapshot()
        updateSaveButtonState()
        showToast(getString(R.string.swipe_symbol_mapping_saved_at, file.absolutePath))
    }

    private fun confirmResetAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset)
            .setMessage(R.string.swipe_symbol_reset_all_confirm)
            .setPositiveButton(R.string.reset) { _, _ ->
                entries.clear()
                defaultSnapshot().toSortedMap().forEach { (k, v) -> entries[k] = Row(v.first, v.second) }
                adapter?.notifyDataSetChanged()
                updateSaveButtonState()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(key: String) {
        val position = entries.keys.toList().indexOf(key)
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.swipe_symbol_delete_confirm, key))
            .setPositiveButton(R.string.delete) { _, _ ->
                entries.remove(key)
                if (position >= 0) adapter?.notifyItemRemoved(position)
                updateSaveButtonState()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateSaveButtonState() {
        saveMenuItem?.let { item ->
            val changed = hasChanges()
            item.isEnabled = changed
            item.icon?.mutate()?.setTint(saveIconTint(changed))
        }
    }

    private fun labeledField(labelRes: Int, value: String, hintRes: Int): Pair<LinearLayout, EditText> {
        val label = TextView(this).apply {
            setText(labelRes)
            textSize = 13f
            setTextColor(styledColor(android.R.attr.textColorSecondary))
            setPadding(0, dp(10), 0, dp(2))
        }
        val edit = EditText(this).apply {
            setText(value)
            setHint(hintRes)
            setSelection(text.length)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(edit)
        }
        return box to edit
    }

    private fun openEditor(originalKey: String?) {
        val existing = originalKey?.let { entries[it] }
        val (rawBox, rawEdit) = labeledField(
            R.string.swipe_symbol_col_raw, originalKey.orEmpty(), R.string.swipe_symbol_raw_hint
        )
        val (fullBox, fullEdit) = labeledField(
            R.string.swipe_symbol_col_fullwidth, existing?.full.orEmpty(), R.string.swipe_symbol_fullwidth_hint
        )
        val (upperBox, upperEdit) = labeledField(
            R.string.swipe_symbol_col_upper, existing?.upper.orEmpty(), R.string.swipe_symbol_upper_hint
        )
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, dp(4), pad, pad)
            addView(rawBox)
            addView(fullBox)
            addView(upperBox)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (originalKey == null) R.string.swipe_symbol_add_mapping else R.string.edit)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.reset, null)
            .create()

        dialog.setOnShowListener {
            // 复位：按原始半角标点取内置默认全角 / 大写列。
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val raw = rawEdit.text.toString().trim()
                val def = DefaultSwipeSymbolMapping[raw]
                fullEdit.setText(def?.fullWidth.orEmpty())
                upperEdit.setText(def?.upper.orEmpty())
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val raw = rawEdit.text.toString().trim()
                if (raw.length != 1) {
                    showToast(getString(R.string.swipe_symbol_raw_must_be_single_char))
                    return@setOnClickListener
                }
                if (raw != originalKey && entries.containsKey(raw)) {
                    showToast(getString(R.string.swipe_symbol_raw_exists, raw))
                    return@setOnClickListener
                }
                val row = Row(fullEdit.text.toString(), upperEdit.text.toString())
                if (originalKey != null && originalKey != raw) entries.remove(originalKey)
                entries[raw] = row
                // 重新排序后整体刷新，位置随 key 变化。
                val reordered = entries.toSortedMap()
                entries.clear()
                entries.putAll(reordered)
                adapter?.notifyDataSetChanged()
                updateSaveButtonState()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun columnCell(context: android.content.Context, text: CharSequence, weight: Float, bold: Boolean): TextView =
        TextView(context).apply {
            this.text = text
            textSize = if (bold) 13f else 16f
            if (bold) {
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(styledColor(android.R.attr.textColorSecondary))
            } else {
                setTextColor(styledColor(android.R.attr.textColorPrimary))
            }
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                this.weight = weight
            }
        }

    private fun buildHeaderRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(4), 0, dp(4))
        addView(columnCell(context, getString(R.string.swipe_symbol_col_raw), 1f, true))
        addView(columnCell(context, getString(R.string.swipe_symbol_col_fullwidth), 1.3f, true))
        addView(columnCell(context, getString(R.string.swipe_symbol_col_upper), 1.3f, true))
    }

    private inner class MappingAdapter : RecyclerView.Adapter<MappingAdapter.ViewHolder>() {

        private val keys: List<String> get() = entries.keys.toList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val ctx = parent.context
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = RecyclerView.LayoutParams(matchParent, wrapContent)
            }
            val raw = columnCell(ctx, "", 1f, false)
            val full = columnCell(ctx, "", 1.3f, false)
            val upper = columnCell(ctx, "", 1.3f, false)
            row.addView(raw)
            row.addView(full)
            row.addView(upper)
            return ViewHolder(row, raw, full, upper)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(keys[position])
        }

        override fun getItemCount(): Int = entries.size

        inner class ViewHolder(
            row: LinearLayout,
            private val rawCell: TextView,
            private val fullCell: TextView,
            private val upperCell: TextView
        ) : RecyclerView.ViewHolder(row) {
            init {
                row.setPadding(0, dp(4), 0, dp(4))
                row.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) openEditor(keys[pos])
                }
                row.setOnLongClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) confirmDelete(keys[pos])
                    true
                }
            }

            fun bind(key: String) {
                val row = entries[key] ?: return
                rawCell.text = key
                fullCell.text = row.full.ifEmpty { "—" }
                upperCell.text = row.upper.ifEmpty { "—" }
            }
        }
    }

    companion object {
        private const val MENU_SAVE_ID = 3001
        private const val MENU_RESET_ID = 3002
    }
}
