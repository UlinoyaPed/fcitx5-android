/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior.dialog

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doOnTextChanged
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.data.theme.SystemColorResourceId
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.data.theme.ThemeMonet
import org.fxboomk.fcitx5.android.data.theme.THEME_COLOR_REF_PREFIX
import org.fxboomk.fcitx5.android.data.theme.resolveThemeColorReference
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.utils.KeyboardRowStyleUtils
import org.fxboomk.fcitx5.android.ui.main.settings.theme.ThemeColorEditorActivity
import org.fxboomk.fcitx5.android.ui.main.settings.theme.SystemColorResourcePickerDialog
import org.fxboomk.fcitx5.android.utils.serializable
import splitties.dimensions.dp
import splitties.resources.styledColor
import splitties.views.backgroundColor
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import java.util.HashMap

class RowEditorActivity : AppCompatActivity() {

    private val toolbar by lazy {
        Toolbar(this).apply {
            backgroundColor = styledColor(android.R.attr.colorPrimary)
            elevation = dp(4f)
            setSubtitleTextAppearance(context, android.R.style.TextAppearance_Small)
            setSubtitleTextColor(styledColor(android.R.attr.textColorSecondary))
        }
    }

    private val contentContainer by lazy {
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(12)
            setPadding(pad, pad, pad, pad)
        }
    }

    private val scrollView by lazy {
        ScrollView(this).apply {
            addView(contentContainer, LinearLayout.LayoutParams(matchParent, wrapContent))
        }
    }

    private val rootView by lazy {
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar, LinearLayout.LayoutParams(matchParent, wrapContent))
            addView(scrollView, LinearLayout.LayoutParams(matchParent, 0).apply { weight = 1f })
        }
    }

    private var rowIndex: Int = -1
    private var rowStyle: KeyboardRowStyleUtils.RowStyle = KeyboardRowStyleUtils.RowStyle()
    private var initialStyle: KeyboardRowStyleUtils.RowStyle = KeyboardRowStyleUtils.RowStyle()
    private var saveMenuItem: MenuItem? = null

    private lateinit var heightMultiplierEdit: EditText
    private lateinit var keyWidthMultiplierEdit: EditText
    private lateinit var subLabelPositionSpinner: Spinner
    private lateinit var subLabelOnePositionSpinner: Spinner
    private lateinit var backgroundStyleSpinner: Spinner
    private lateinit var backgroundColorValue: TextView
    private lateinit var backgroundColorSwatch: View
    private val colorSelectionHistory by lazy { ColorSelectionHistory(this) }
    private var suppressSpinnerCallbacks = false

    private val colorEditorLauncher =
        registerForActivityResult(ThemeColorEditorActivity.Contract()) { result ->
            result ?: return@registerForActivityResult
            colorSelectionHistory.record(
                COLOR_HISTORY_ATTRIBUTE,
                ColorSelection.Argb(result.color)
            )
            rowStyle = rowStyle.copy(backgroundColor = result.color, backgroundColorMonet = null)
            bindUiState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(rootView)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        rowIndex = intent.getIntExtra(EXTRA_ROW_INDEX, -1)
        rowStyle = KeyboardRowStyleUtils.rowStyleFromMeta(
            intent.serializable<HashMap<String, Any?>>(EXTRA_ROW_META)
        )
        initialStyle = rowStyle

        supportActionBar?.title = getString(R.string.text_keyboard_layout_row_editor_title)
        toolbar.subtitle = getString(R.string.text_keyboard_layout_row_editor_subtitle, rowIndex + 1)

        val toolbarBaseTopPadding = toolbar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = toolbarBaseTopPadding + statusTop)
            insets
        }
        ViewCompat.requestApplyInsets(toolbar)

        buildForm()
        bindUiState()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        saveMenuItem = menu.add(Menu.NONE, MENU_SAVE_ID, 1, getString(R.string.save)).apply {
            setIcon(R.drawable.ic_baseline_save_24)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        updateSaveButtonState()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        MENU_SAVE_ID -> {
            saveAndFinish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun buildForm() {
        val uiBuilder = KeyboardEditorUiBuilder(this)
        val heightField = uiBuilder.createEditField(
            getString(R.string.text_keyboard_layout_row_height_multiplier),
            rowStyle.heightMultiplier.toString()
        )
        heightMultiplierEdit = heightField.second
        heightMultiplierEdit.hint = "1.0"
        heightMultiplierEdit.doOnTextChanged { _, _, _, _ -> updateSaveButtonState() }
        contentContainer.addView(heightField.first)

        val keyWidthField = uiBuilder.createEditField(
            getString(R.string.text_keyboard_layout_row_key_width_multiplier),
            rowStyle.keyWidthMultiplier?.toString().orEmpty()
        )
        keyWidthMultiplierEdit = keyWidthField.second
        keyWidthMultiplierEdit.hint = getString(R.string.text_keyboard_layout_row_key_width_multiplier_hint)
        keyWidthMultiplierEdit.doOnTextChanged { _, _, _, _ -> updateSaveButtonState() }
        contentContainer.addView(keyWidthField.first)

        val subLabelRow = createSpinnerRow(
            title = getString(R.string.text_keyboard_layout_row_sub_label_position),
            items = subLabelPositionLabels()
        )
        subLabelPositionSpinner = subLabelRow.second
        subLabelPositionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSpinnerCallbacks) return
                val selected = subLabelPositionFromIndex(position)
                val next = rowStyle.copy(
                    altTextPosition = selected,
                    altTextPosition1 = if (
                        selected == KeyboardRowStyleUtils.AltTextPosition.TopRight &&
                        rowStyle.altTextPosition1 == selected
                    ) null else rowStyle.altTextPosition1
                )
                if (next == rowStyle) return
                rowStyle = next
                bindUiState()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        contentContainer.addView(subLabelRow.first)

        val subLabelOneRow = createSpinnerRow(
            title = getString(R.string.text_keyboard_layout_row_sub_label_one_position),
            items = subLabelPositionLabels()
        )
        subLabelOnePositionSpinner = subLabelOneRow.second
        subLabelOnePositionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSpinnerCallbacks) return
                val selected = subLabelPositionFromIndex(position)
                val next = rowStyle.copy(
                    altTextPosition1 = selected,
                    altTextPosition = if (
                        selected == KeyboardRowStyleUtils.AltTextPosition.TopRight &&
                        rowStyle.altTextPosition == selected
                    ) null else rowStyle.altTextPosition
                )
                if (next == rowStyle) return
                rowStyle = next
                bindUiState()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        contentContainer.addView(subLabelOneRow.first)

        val backgroundStyleRow = createSpinnerRow(
            title = getString(R.string.text_keyboard_layout_row_background_style),
            items = backgroundStyleLabels()
        )
        backgroundStyleSpinner = backgroundStyleRow.second
        backgroundStyleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSpinnerCallbacks) return
                val next = rowStyle.copy(backgroundStyle = backgroundStyleFromIndex(position))
                if (next == rowStyle) return
                rowStyle = next
                bindUiState()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        contentContainer.addView(backgroundStyleRow.first)

        val backgroundColorRow = createColorActionRow(
            title = getString(R.string.text_keyboard_layout_row_background_color)
        ) {
            showBackgroundColorOptions()
        }
        backgroundColorValue = backgroundColorRow.second
        backgroundColorSwatch = backgroundColorRow.third
        contentContainer.addView(backgroundColorRow.first)
    }

    private fun bindUiState() {
        suppressSpinnerCallbacks = true
        try {
            if (subLabelPositionSpinner.selectedItemPosition != subLabelPositionIndex(rowStyle.altTextPosition, false)) {
                subLabelPositionSpinner.setSelection(subLabelPositionIndex(rowStyle.altTextPosition, false))
            }
            if (subLabelOnePositionSpinner.selectedItemPosition != subLabelPositionIndex(rowStyle.altTextPosition1, true)) {
                subLabelOnePositionSpinner.setSelection(subLabelPositionIndex(rowStyle.altTextPosition1, true))
            }
            if (backgroundStyleSpinner.selectedItemPosition != backgroundStyleIndex(rowStyle.backgroundStyle)) {
                backgroundStyleSpinner.setSelection(backgroundStyleIndex(rowStyle.backgroundStyle))
            }
        } finally {
            suppressSpinnerCallbacks = false
        }

        val colorReference = rowStyle.backgroundColorMonet?.takeIf { it.isNotBlank() }
        val color = colorReference?.let {
            resolveThemeColorReference(this, ThemeManager.activeTheme, it)
        } ?: rowStyle.backgroundColor
        backgroundColorValue.text = when {
            colorReference != null -> formatColorReferenceName(colorReference)
            color != null -> String.format("#%08X", color)
            else -> getString(R.string.text_keyboard_layout_row_background_not_set)
        }
        backgroundColorSwatch.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(4).toFloat()
            setColor(color ?: ThemeManager.activeTheme.keyBackgroundColor)
        }
        backgroundColorSwatch.alpha = if (colorReference == null && color == null) 0.4f else 1f
        backgroundColorValue.alpha = if (rowStyle.backgroundStyle == null) 0.55f else 1f
        backgroundColorSwatch.alpha = if (rowStyle.backgroundStyle == null) 0.25f else backgroundColorSwatch.alpha
        updateSaveButtonState()
    }

    private fun subLabelPositionLabels(): List<String> = listOf(
        getString(R.string.text_keyboard_layout_row_follow_theme),
        getString(R.string.text_keyboard_layout_row_sub_label_top),
        getString(R.string.text_keyboard_layout_row_sub_label_top_right),
        getString(R.string.text_keyboard_layout_row_sub_label_bottom)
    )

    private fun subLabelPositionIndex(
        position: KeyboardRowStyleUtils.AltTextPosition?,
        secondLabel: Boolean
    ): Int = when (position) {
        KeyboardRowStyleUtils.AltTextPosition.TopBottom -> if (secondLabel) 3 else 1
        KeyboardRowStyleUtils.AltTextPosition.Top -> 1
        KeyboardRowStyleUtils.AltTextPosition.TopRight -> 2
        KeyboardRowStyleUtils.AltTextPosition.Bottom -> 3
        null -> 0
    }

    private fun subLabelPositionFromIndex(index: Int): KeyboardRowStyleUtils.AltTextPosition? = when (index) {
        1 -> KeyboardRowStyleUtils.AltTextPosition.Top
        2 -> KeyboardRowStyleUtils.AltTextPosition.TopRight
        3 -> KeyboardRowStyleUtils.AltTextPosition.Bottom
        else -> null
    }

    private fun backgroundStyleLabels(): List<String> = listOf(
        getString(R.string.text_keyboard_layout_row_background_style_default),
        getString(R.string.text_keyboard_layout_row_background_style_solid),
        getString(R.string.text_keyboard_layout_row_background_style_gradient)
    )

    private fun backgroundStyleIndex(style: KeyboardRowStyleUtils.BackgroundStyle?): Int = when (style) {
        KeyboardRowStyleUtils.BackgroundStyle.Solid -> 1
        KeyboardRowStyleUtils.BackgroundStyle.Gradient -> 2
        null -> 0
    }

    private fun backgroundStyleFromIndex(index: Int): KeyboardRowStyleUtils.BackgroundStyle? = when (index) {
        1 -> KeyboardRowStyleUtils.BackgroundStyle.Solid
        2 -> KeyboardRowStyleUtils.BackgroundStyle.Gradient
        else -> null
    }

    private fun openColorEditor() {
        val initialColor = rowStyle.backgroundColorMonet?.let {
            resolveThemeColorReference(this, ThemeManager.activeTheme, it)
        } ?: rowStyle.backgroundColor ?: ThemeManager.activeTheme.keyBackgroundColor
        colorEditorLauncher.launch(
            ThemeColorEditorActivity.EditorInput(
                fieldName = "rowBackgroundColor",
                titleRes = R.string.text_keyboard_layout_row_background_color,
                initialColor = initialColor
            )
        )
    }

    private fun currentEditedStyle(): KeyboardRowStyleUtils.RowStyle? {
        val heightMultiplier = heightMultiplierEdit.text?.toString()
            ?.trim()
            ?.takeUnless { it.isEmpty() }
            ?.toFloatOrNull()
            ?: return null
        if (heightMultiplier <= 0f || !heightMultiplier.isFinite()) return null
        val widthText = keyWidthMultiplierEdit.text?.toString()?.trim().orEmpty()
        val keyWidthMultiplier = if (widthText.isEmpty()) null else widthText.toFloatOrNull() ?: return null
        if (keyWidthMultiplier != null && (keyWidthMultiplier <= 0f || !keyWidthMultiplier.isFinite())) return null
        return rowStyle.copy(
            heightMultiplier = heightMultiplier,
            keyWidthMultiplier = keyWidthMultiplier
        )
    }

    private fun hasRequiredBackgroundColor(style: KeyboardRowStyleUtils.RowStyle): Boolean {
        return style.backgroundStyle == null ||
            style.backgroundColor != null ||
            !style.backgroundColorMonet.isNullOrBlank()
    }

    private fun updateSaveButtonState() {
        val candidate = currentEditedStyle()
        val enabled = candidate != null &&
            hasRequiredBackgroundColor(candidate) &&
            candidate != initialStyle
        saveMenuItem?.isEnabled = enabled
        saveMenuItem?.icon?.mutate()?.setTint(if (enabled) Color.BLACK else Color.GRAY)
    }

    private fun saveAndFinish() {
        val normalizedStyle = currentEditedStyle() ?: run {
            val height = heightMultiplierEdit.text?.toString()?.trim()?.toFloatOrNull()
            val message = if (height == null || height <= 0f || !height.isFinite()) {
                R.string.text_keyboard_layout_row_height_multiplier_invalid
            } else {
                R.string.text_keyboard_layout_row_key_width_multiplier_invalid
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasRequiredBackgroundColor(normalizedStyle)) {
            Toast.makeText(this, R.string.text_keyboard_layout_row_background_color_required, Toast.LENGTH_SHORT).show()
            return
        }
        val result = Intent().apply {
            putExtra(EXTRA_RESULT_ACTION, RESULT_ACTION_SAVE)
            putExtra(EXTRA_ROW_INDEX, rowIndex)
            putExtra(EXTRA_RESULT_ROW_META, HashMap(KeyboardRowStyleUtils.buildMeta(normalizedStyle)))
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private fun showBackgroundColorOptions() {
        val supportsMonet = ThemeMonet.supportsCustomMappingEditor(this)
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (colorSelectionHistory.recent().isNotEmpty()) {
            options += getString(R.string.text_keyboard_layout_key_color_mode_recent)
            actions += { showRecentColorOptions() }
        }

        colorSelectionHistory.last(COLOR_HISTORY_ATTRIBUTE)?.let { last ->
            options += getString(R.string.text_keyboard_layout_key_color_mode_last)
            actions += { applyColorSelection(last) }
        }

        options += getString(R.string.text_keyboard_layout_key_color_mode_theme)
        actions += {
            rowStyle = rowStyle.copy(
                backgroundStyle = null,
                backgroundColor = null,
                backgroundColorMonet = null
            )
            bindUiState()
        }

        options += getString(R.string.text_keyboard_layout_key_color_mode_custom)
        actions += { openColorEditor() }

        options += getString(R.string.text_keyboard_layout_key_color_mode_theme_key_pick)
        actions += { openThemeColorTokenPicker() }

        if (supportsMonet) {
            options += getString(R.string.text_keyboard_layout_key_color_mode_monet_pick)
            actions += { openMonetColorPicker() }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.text_keyboard_layout_row_background_color)
            .setItems(options.toTypedArray()) { _, which ->
                actions.getOrNull(which)?.invoke()
            }
            .show()
    }

    private fun showRecentColorOptions() {
        val recent = colorSelectionHistory.recent()
        if (recent.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.text_keyboard_layout_key_color_mode_recent)
            .setItems(
                recent.map(::formatColorSelection).toTypedArray()
            ) { _, which ->
                recent.getOrNull(which)?.let { applyColorSelection(it) }
            }
            .show()
    }

    private fun applyColorSelection(selection: ColorSelection) {
        colorSelectionHistory.record(COLOR_HISTORY_ATTRIBUTE, selection)
        rowStyle = when (selection) {
            is ColorSelection.Argb -> rowStyle.copy(
                backgroundColor = selection.color,
                backgroundColorMonet = null
            )
            is ColorSelection.Monet -> rowStyle.copy(
                backgroundColor = null,
                backgroundColorMonet = selection.resourceId
            )
            is ColorSelection.ThemeReference -> rowStyle.copy(
                backgroundColor = null,
                backgroundColorMonet = selection.reference
            )
        }
        bindUiState()
    }

    private fun formatColorSelection(selection: ColorSelection): String = when (selection) {
        is ColorSelection.Argb -> String.format("#%08X", selection.color)
        is ColorSelection.Monet -> formatColorReferenceName(selection.resourceId)
        is ColorSelection.ThemeReference -> formatColorReferenceName(selection.reference)
    }

    private fun openThemeColorTokenPicker() {
        val currentToken = rowStyle.backgroundColorMonet
            ?.takeIf { it.startsWith(THEME_COLOR_REF_PREFIX) }
            ?.removePrefix(THEME_COLOR_REF_PREFIX)
        val checkedIndex = ThemeColorTokenPicker.tokens.indexOf(currentToken)

        AlertDialog.Builder(this)
            .setTitle(R.string.text_keyboard_layout_key_color_mode_theme_key_pick)
            .setSingleChoiceItems(
                ThemeColorTokenPicker.PreviewAdapter(
                    this,
                    ThemeColorTokenPicker.tokens,
                    ThemeManager.activeTheme,
                    singleLineLabel = true
                ),
                checkedIndex
            ) { dialog, which ->
                val token = ThemeColorTokenPicker.tokens.getOrNull(which)
                    ?: return@setSingleChoiceItems
                val reference = "$THEME_COLOR_REF_PREFIX$token"
                colorSelectionHistory.record(
                    COLOR_HISTORY_ATTRIBUTE,
                    ColorSelection.ThemeReference(reference)
                )
                rowStyle = rowStyle.copy(
                    backgroundColor = null,
                    backgroundColorMonet = reference
                )
                dialog.dismiss()
                bindUiState()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openMonetColorPicker() {
        if (!ThemeMonet.supportsCustomMappingEditor(this)) {
            Toast.makeText(
                this,
                R.string.text_keyboard_layout_key_color_monet_unavailable,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val available = SystemColorResourceId.getAvailableForSdk(Build.VERSION.SDK_INT)
        val current = rowStyle.backgroundColorMonet
            ?.takeUnless { it.startsWith(THEME_COLOR_REF_PREFIX) }
            ?.let(SystemColorResourceId::fromResourceName)
            ?: available.firstOrNull()
            ?: return
        SystemColorResourcePickerDialog.show(
            this,
            current,
            object : SystemColorResourcePickerDialog.OnColorResourceSelectedListener {
                override fun onColorResourceSelected(resourceId: SystemColorResourceId) {
                    applyColorSelection(ColorSelection.Monet(resourceId.resourceId))
                }
            }
        )
    }

    private fun formatColorReferenceName(ref: String): String {
        return if (ref.startsWith(THEME_COLOR_REF_PREFIX)) {
            val token = ref.removePrefix(THEME_COLOR_REF_PREFIX)
            getString(
                R.string.text_keyboard_layout_key_color_mode_theme_ref,
                ThemeColorTokenPicker.formatTokenName(token)
            )
        } else {
            getString(
                R.string.text_keyboard_layout_key_color_mode_monet,
                ref.removePrefix("system_").replace("_", " ")
            )
        }
    }

    private fun createActionRow(
        title: String,
        onClick: () -> Unit
    ): Pair<LinearLayout, TextView> {
        val valueView = TextView(this).apply {
            textSize = 13f
            setTextColor(styledColor(android.R.attr.textColorPrimary))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            background = resources.getDrawable(android.R.drawable.list_selector_background, theme)
            setOnClickListener { onClick() }

            addView(
                TextView(this@RowEditorActivity).apply {
                    text = title
                    textSize = 13f
                    setTextColor(styledColor(android.R.attr.textColorSecondary))
                },
                LinearLayout.LayoutParams(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dp(8)
                }
            )
            addView(
                valueView,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    weight = 1f
                }
            )
        }
        return row to valueView
    }

    private fun createSpinnerRow(
        title: String,
        items: List<String>
    ): Pair<LinearLayout, Spinner> {
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@RowEditorActivity,
                android.R.layout.simple_spinner_item,
                items
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))

            addView(
                TextView(this@RowEditorActivity).apply {
                    text = title
                    textSize = 13f
                    setTextColor(styledColor(android.R.attr.textColorSecondary))
                },
                LinearLayout.LayoutParams(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dp(8)
                }
            )
            addView(
                spinner,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    weight = 1f
                }
            )
        }
        return row to spinner
    }

    private fun createColorActionRow(
        title: String,
        onClick: () -> Unit
    ): Triple<LinearLayout, TextView, View> {
        val (row, valueView) = createActionRow(title, onClick)
        val swatch = View(this)
        row.addView(
            swatch,
            LinearLayout.LayoutParams(dp(20), dp(20))
        )
        return Triple(row, valueView, swatch)
    }

    companion object {
        const val EXTRA_ROW_INDEX = "extra_row_index"
        const val EXTRA_ROW_META = "extra_row_meta"
        const val EXTRA_RESULT_ROW_META = "extra_result_row_meta"
        const val EXTRA_RESULT_ACTION = "extra_result_action"

        const val RESULT_ACTION_SAVE = "save"

        private const val COLOR_HISTORY_ATTRIBUTE = "row:backgroundColor"
        private const val MENU_SAVE_ID = 1
    }
}
