/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.FloatRange
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.data.theme.Theme
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.PunctuationPosition
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs.UppercasePosition
import org.fxboomk.fcitx5.android.data.theme.resolveThemeColorReference
import org.fxboomk.fcitx5.android.input.AutoScaleTextView
import org.fxboomk.fcitx5.android.input.keyboard.KeyDef.Appearance.Border
import org.fxboomk.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import org.fxboomk.fcitx5.android.utils.styledFloat
import org.fxboomk.fcitx5.android.utils.unset
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.parentId
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.existingOrNewId
import splitties.views.imageResource
import splitties.views.padding
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class AltTextSwipeTarget {
    Primary,
    Secondary,
    Uppercase
}

interface SwipeHintAwareKeyView {
    fun selectAltTextSwipeTarget(totalY: Int): AltTextSwipeTarget?

    /**
     * The swipe target used when the swipe direction setting resolves to the
     * "second" label of this key. Defaults to [AltTextSwipeTarget.Secondary];
     * keys whose second label is the uppercase hint report [AltTextSwipeTarget.Uppercase].
     */
    fun secondarySwipeTarget(): AltTextSwipeTarget = AltTextSwipeTarget.Secondary
}

/**
 * Implemented by [KeyView] subclasses that render an [ImageView] icon (e.g. the
 * caps/shift key). Attaching a swipe label ("划动标签") to such a key changes its
 * concrete class from [ImageKeyView] to [ImageAltTextKeyView], so callers that
 * need to update the icon must look the key up by this interface rather than by
 * a concrete type, otherwise the labelled variant is missed and its icon never
 * refreshes.
 */
interface KeyViewWithImage {
    val img: ImageView
}

abstract class KeyView(
    ctx: Context,
    var theme: Theme,
    val def: KeyDef.Appearance,
    horizontalGapScale: Float = 1f
) :
    CustomGestureView(ctx) {

    internal var useFloatingGboardSideKeyStyle: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            refreshGboardSideKeyShape()
        }

    private fun isMainKeyAreaById(viewId: Int): Boolean {
        return viewId == R.id.button_space ||
                viewId == R.id.button_lang
    }

    private fun resolvedViewId(): Int {
        return (tag as? Int) ?: def.viewId
    }

    private fun resolveKeyBackgroundColor(theme: Theme): Int {
        if (isMainKeyAreaById(def.viewId)) {
            return theme.keyBackgroundColor
        }
        return when (def.variant) {
            Variant.Normal -> theme.keyBackgroundColor
            Variant.AltForeground, Variant.Alternative -> theme.altKeyBackgroundColor
            Variant.Accent -> theme.accentKeyBackgroundColor
        }
    }

    val bordered: Boolean
    val borderStroke: Boolean
    val rippled: Boolean
    val radius: Float
    val hMargin: Int
    val vMargin: Int
    var isCircularSideKey: Boolean = false
        private set
    protected val cornerLabelHorizontalSafeInset: Int
    protected val cornerLabelTopSafeInset: Int

    init {
        val prefs = ThemeManager.prefs
        bordered = prefs.keyBorder.getValue()
        borderStroke = prefs.keyBorderStroke.getValue()
        rippled = prefs.keyRippleEffect.getValue()
        radius = dp(prefs.keyRadius.getValue().toFloat())
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val hMarginPref =
            if (landscape) prefs.keyHorizontalMarginLandscape else prefs.keyHorizontalMargin
        val vMarginPref =
            if (landscape) prefs.keyVerticalMarginLandscape else prefs.keyVerticalMargin
        val hScale = horizontalGapScale.coerceIn(0.5f, 1f)
        val hMarginValue = (hMarginPref.getValue().toFloat() * hScale).roundToInt().coerceAtLeast(0)
        hMargin = if (def.margin) dp(hMarginValue) else 0
        vMargin = if (def.margin) dp(vMarginPref.getValue()) else 0
        cornerLabelHorizontalSafeInset = dp(3)
        cornerLabelTopSafeInset = dp(1)
    }

    private val cachedLocation = intArrayOf(0, 0)
    private val cachedBounds = Rect()
    private var boundsValid = false
    val bounds: Rect
        get() = cachedBounds.also {
            if (!boundsValid) updateBounds()
        }

    fun invalidateCachedBounds() {
        boundsValid = false
    }

    /**
     * KeyView content left margin, in percentage of parent width
     */
    @FloatRange(0.0, 1.0)
    var layoutMarginLeft = 0f

    /**
     * KeyView content right margin, in percentage of parent width
     */
    @FloatRange(0.0, 1.0)
    var layoutMarginRight = 0f

    /**
     * [KeyView] contains 2 parts: `TouchEventView` and `AppearanceView`.
     *
     * `TouchEventView` is the outer [CustomGestureView] that handles touch events.
     *
     * `AppearanceView` in the inner [ConstraintLayout], it can be smaller than its parent,
     * and holds the [bounds] for popup.
     */
    protected val appearanceView = constraintLayout {
        // sync any state from parent
        isDuplicateParentStateEnabled = true
    }

    init {
        // trigger setEnabled(true)
        isEnabled = true
        isClickable = true
        isHapticFeedbackEnabled = false
        if (def.viewId > 0) {
            id = View.generateViewId()
            tag = def.viewId
        }
        // Side keys (?123 and return) - always handle first, before border check
        val viewId = resolvedViewId()
        val isSideKey = viewId == R.id.button_layout_switch || viewId == R.id.button_return
        if (isSideKey) {
            val defaultBkgColor = when (def.variant) {
                Variant.Normal, Variant.AltForeground -> theme.keyBackgroundColor
                Variant.Alternative -> theme.altKeyBackgroundColor
                Variant.Accent -> theme.accentKeyBackgroundColor
            }
            val bkgColor = resolveBackgroundColor(theme, defaultBkgColor)
            if (ThemeManager.prefs.gboardStyleSideKeys.getValue() && shouldUseCircularGboardSideKeys()) {
                // Circular shape will be applied in onSizeChanged/onLayout when dimensions are available
                isCircularSideKey = true
            } else {
                applyRoundedSideKeyBackground(bkgColor)
            }
            setupPressHighlight()
        } else if ((bordered && def.border != Border.Off) || def.border == Border.On) {
            val defaultBkgColor = if (isMainKeyAreaById(viewId)) {
                theme.keyBackgroundColor
            } else {
                when (def.variant) {
                    Variant.Normal, Variant.AltForeground -> theme.keyBackgroundColor
                    Variant.Alternative -> theme.altKeyBackgroundColor
                    Variant.Accent -> theme.accentKeyBackgroundColor
                }
            }
            val bkgColor = resolveBackgroundColor(theme, defaultBkgColor)
            val borderOrShadowWidth = dp(1)
            // background: key border
            appearanceView.background = if (borderStroke) borderedKeyBackgroundDrawable(
                bkgColor, resolveShadowColor(theme),
                radius, borderOrShadowWidth, hMargin, vMargin
            ) else shadowedKeyBackgroundDrawable(
                bkgColor, resolveShadowColor(theme),
                radius, borderOrShadowWidth, hMargin, vMargin
            )
            // foreground: press highlight or ripple
            setupPressHighlight()
        } else {
            // normal press highlight for keys without special background
            // special background is handled in `onSizeChanged()`
            if (def.border != Border.Special) {
                setupPressHighlight()
            }
        }
        add(appearanceView, lParams(matchParent, matchParent))
    }

    private fun resolveMonetColor(resourceName: String?): Int? {
        val name = resourceName?.takeIf { it.isNotBlank() } ?: return null
        val colorResId = context.resources.getIdentifier(name, "color", "android")
        if (colorResId == 0) return null
        return runCatching { context.getColor(colorResId) }.getOrNull()
    }

    private fun resolveColorOverride(theme: Theme, staticColor: Int?, colorRef: String?): Int? {
        return resolveThemeColorReference(context, theme, colorRef)
            ?: resolveMonetColor(colorRef)
            ?: staticColor
    }

    protected fun resolveBackgroundColor(theme: Theme, defaultColor: Int): Int {
        return resolveColorOverride(theme, def.backgroundColor, def.backgroundColorMonet) ?: defaultColor
    }

    protected fun resolveShadowColor(theme: Theme): Int {
        return resolveColorOverride(theme, def.shadowColor, def.shadowColorMonet) ?: theme.keyShadowColor
    }

    protected fun resolveTextColor(defaultColor: Int): Int {
        return resolveColorOverride(theme, def.textColor, def.textColorMonet) ?: defaultColor
    }

    protected fun resolveAltTextColor(defaultColor: Int): Int {
        return resolveColorOverride(theme, def.altTextColor, def.altTextColorMonet) ?: defaultColor
    }

    fun resolveBlurClipInsets(viewWidth: Int, viewHeight: Int): Pair<Int, Int> {
        if (!isCircularSideKey) return hMargin to vMargin
        return resolveSideKeyCircleInsets(viewWidth, viewHeight)
    }

    private fun resolveSideKeyCircleInsets(viewWidth: Int, viewHeight: Int): Pair<Int, Int> {
        val minInset = dp(4)
        val usableWidth = (viewWidth - minInset * 2).coerceAtLeast(0)
        val usableHeight = (viewHeight - minInset * 2).coerceAtLeast(0)
        val diameter = min(usableWidth, usableHeight)
        val horizontalInset = ((viewWidth - diameter) / 2).coerceAtLeast(minInset)
        val verticalInset = ((viewHeight - diameter) / 2).coerceAtLeast(minInset)
        return horizontalInset to verticalInset
    }

    private fun applyCircularSideKeyBackground(
        viewWidth: Int,
        viewHeight: Int,
        @ColorInt backgroundColor: Int
    ) {
        val (hInset, vInset) = resolveSideKeyCircleInsets(viewWidth, viewHeight)
        isCircularSideKey = true
        appearanceView.background = insetOvalDrawable(hInset, vInset, backgroundColor)
        // Keep content inside the visible circular background when the side key becomes short.
        appearanceView.setPadding(hInset, vInset, hInset, vInset)
        setupPressHighlight(
            insetOvalDrawable(
                hInset, vInset,
                if (rippled) Color.WHITE else theme.keyPressHighlightColor
            )
        )
    }

    private fun applyRoundedSideKeyBackground(@ColorInt backgroundColor: Int) {
        val borderOrShadowWidth = dp(1)
        isCircularSideKey = false
        appearanceView.background = shadowedKeyBackgroundDrawable(
            backgroundColor, resolveShadowColor(theme),
            radius, borderOrShadowWidth, hMargin, vMargin
        )
        appearanceView.padding = 0
        setupPressHighlight(
            insetRadiusDrawable(
                hMargin, vMargin, radius,
                if (rippled) Color.WHITE else theme.keyPressHighlightColor
            )
        )
    }

    private fun shouldUseCircularGboardSideKeys(): Boolean {
        return resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE ||
            useFloatingGboardSideKeyStyle
    }

    private fun refreshGboardSideKeyShape() {
        val viewWidth = appearanceView.width.takeIf { it > 0 } ?: width
        val viewHeight = appearanceView.height.takeIf { it > 0 } ?: height
        if (viewWidth <= 0 || viewHeight <= 0) return
        maybeRefreshGboardSideKeyShape(viewWidth, viewHeight)
    }

    private fun maybeRefreshGboardSideKeyShape(viewWidth: Int, viewHeight: Int) {
        if (!ThemeManager.prefs.gboardStyleSideKeys.getValue()) return
        val viewId = resolvedViewId()
        if (viewId != R.id.button_layout_switch && viewId != R.id.button_return) return
        val defaultBkgColor = when (def.variant) {
            Variant.Normal, Variant.AltForeground -> theme.keyBackgroundColor
            Variant.Alternative -> theme.altKeyBackgroundColor
            Variant.Accent -> theme.accentKeyBackgroundColor
        }
        val backgroundColor = resolveBackgroundColor(theme, defaultBkgColor)
        if (shouldUseCircularGboardSideKeys()) {
            applyCircularSideKeyBackground(
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                backgroundColor = backgroundColor
            )
        } else {
            applyRoundedSideKeyBackground(backgroundColor)
        }
    }

    private fun setupPressHighlight(mask: Drawable? = null) {
        appearanceView.foreground = if (rippled) {
            RippleDrawable(
                ColorStateList.valueOf(theme.keyPressHighlightColor), null,
                // ripple should be masked with an opaque color
                mask ?: highlightMaskDrawable(Color.WHITE)
            )
        } else if (bordered && borderStroke) {
            StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_pressed),
                    borderedKeyBackgroundDrawable(
                        Color.TRANSPARENT, resolveShadowColor(theme),
                        radius, dp(2), hMargin, vMargin
                    )
                )
            }
        } else {
            StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_pressed),
                    // use mask drawable as highlight directly
                    mask ?: highlightMaskDrawable(theme.keyPressHighlightColor)
                )
            }
        }
    }

    private fun highlightMaskDrawable(@ColorInt color: Int): Drawable {
        return if (bordered) insetRadiusDrawable(hMargin, vMargin, radius, color)
        else InsetDrawable(ColorDrawable(color), hMargin, vMargin, hMargin, vMargin)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        appearanceView.alpha = if (enabled) 1f else styledFloat(android.R.attr.disabledAlpha)
    }

    fun updateBounds() {
        val (x, y) = cachedLocation.also { appearanceView.getLocationInWindow(it) }
        cachedBounds.set(x, y, x + appearanceView.width, y + appearanceView.height)
        boundsValid = true
    }

    open fun setTextScale(scale: Float) {
        // default implementation does nothing
    }

    protected open fun onAppearanceLayoutChanged(width: Int, height: Int) {
        // default implementation does nothing
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        boundsValid = false
        if (layoutMarginLeft != 0f || layoutMarginRight != 0f) {
            val w = right - left
            val h = bottom - top
            val layoutWidth = (w * (1f - layoutMarginLeft - layoutMarginRight)).roundToInt()
            appearanceView.updateLayoutParams<LayoutParams> {
                leftMargin = (w * layoutMarginLeft).roundToInt()
                rightMargin = (w * layoutMarginRight).roundToInt()
            }
            // sets `measuredWidth` and `measuredHeight` of `AppearanceView`
            // https://developer.android.com/guide/topics/ui/how-android-draws#measure
            appearanceView.measure(
                MeasureSpec.makeMeasureSpec(layoutWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
            )
        }
        super.onLayout(changed, left, top, right, bottom)
        onAppearanceLayoutChanged(appearanceView.width, appearanceView.height)
        val appearanceWidth = appearanceView.width.takeIf { it > 0 } ?: (right - left)
        val appearanceHeight = appearanceView.height.takeIf { it > 0 } ?: (bottom - top)
        maybeRefreshGboardSideKeyShape(appearanceWidth, appearanceHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (
            bordered &&
            resolvedViewId() != R.id.button_layout_switch &&
            resolvedViewId() != R.id.button_return
        ) return
        when (resolvedViewId()) {
            R.id.button_layout_switch -> {
                val defaultBkgColor = when (def.variant) {
                    Variant.Normal, Variant.AltForeground -> theme.keyBackgroundColor
                    Variant.Alternative -> theme.altKeyBackgroundColor
                    Variant.Accent -> theme.accentKeyBackgroundColor
                }
                val bkgColor = resolveBackgroundColor(theme, defaultBkgColor)
                if (ThemeManager.prefs.gboardStyleSideKeys.getValue()) {
                    if (shouldUseCircularGboardSideKeys()) {
                        applyCircularSideKeyBackground(w, h, bkgColor)
                    } else {
                        applyRoundedSideKeyBackground(bkgColor)
                    }
                } else {
                    applyRoundedSideKeyBackground(bkgColor)
                }
            }

            R.id.button_space -> {
                val bkgRadius = dp(3f)
                val minHeight = dp(26)
                val hInset = dp(10)
                val vInset = if (h < minHeight) 0 else min((h - minHeight) / 2, dp(16))
                appearanceView.background = insetRadiusDrawable(
                    hInset, vInset, bkgRadius, resolveBackgroundColor(theme, theme.spaceBarColor)
                )
                // InsetDrawable sets padding to container view; remove padding to prevent text from bing clipped
                appearanceView.padding = 0
                // apply press highlight for background area
                setupPressHighlight(
                    insetRadiusDrawable(
                        hInset, vInset, bkgRadius,
                        if (rippled) Color.WHITE else theme.keyPressHighlightColor
                    )
                )
            }

            R.id.button_return -> {
                val defaultBkgColor = when (def.variant) {
                    Variant.Normal, Variant.AltForeground -> theme.keyBackgroundColor
                    Variant.Alternative -> theme.altKeyBackgroundColor
                    Variant.Accent -> theme.accentKeyBackgroundColor
                }
                val bkgColor = resolveBackgroundColor(theme, defaultBkgColor)
                if (ThemeManager.prefs.gboardStyleSideKeys.getValue()) {
                    if (shouldUseCircularGboardSideKeys()) {
                        applyCircularSideKeyBackground(w, h, bkgColor)
                    } else {
                        applyRoundedSideKeyBackground(bkgColor)
                    }
                } else {
                    applyRoundedSideKeyBackground(bkgColor)
                }
            }
        }
    }

    /**
     * Update theme without rebuilding view
     */
    open fun updateTheme(newTheme: Theme) {
        theme = newTheme

        // Side keys (?123 and return) - always handle first, before border check
        val viewId = resolvedViewId()
        val isSideKey = viewId == R.id.button_layout_switch || viewId == R.id.button_return
        if (isSideKey) {
            val defaultBkgColor = when (def.variant) {
                Variant.Normal, Variant.AltForeground -> newTheme.keyBackgroundColor
                Variant.Alternative -> newTheme.altKeyBackgroundColor
                Variant.Accent -> newTheme.accentKeyBackgroundColor
            }
            val bkgColor = resolveBackgroundColor(newTheme, defaultBkgColor)
            if (ThemeManager.prefs.gboardStyleSideKeys.getValue()) {
                if (shouldUseCircularGboardSideKeys()) {
                    val w = appearanceView.width
                    val h = appearanceView.height
                    if (w > 0 && h > 0) {
                        applyCircularSideKeyBackground(w, h, bkgColor)
                    }
                } else {
                    applyRoundedSideKeyBackground(bkgColor)
                }
            } else {
                applyRoundedSideKeyBackground(bkgColor)
            }
        } else if ((bordered && def.border != Border.Off) || def.border == Border.On) {
            val defaultBkgColor = if (isMainKeyAreaById(viewId)) {
                newTheme.keyBackgroundColor
            } else {
                when (def.variant) {
                    Variant.Normal, Variant.AltForeground -> newTheme.keyBackgroundColor
                    Variant.Alternative -> newTheme.altKeyBackgroundColor
                    Variant.Accent -> newTheme.accentKeyBackgroundColor
                }
            }
            val bkgColor = resolveBackgroundColor(newTheme, defaultBkgColor)
            val borderOrShadowWidth = dp(1)
            // background: key border
            appearanceView.background = if (borderStroke) borderedKeyBackgroundDrawable(
                bkgColor, resolveShadowColor(newTheme),
                radius, borderOrShadowWidth, hMargin, vMargin
            ) else shadowedKeyBackgroundDrawable(
                bkgColor, resolveShadowColor(newTheme),
                radius, borderOrShadowWidth, hMargin, vMargin
            )
        }
        // Update press highlight for all keys
        setupPressHighlight()

        // Update special backgrounds for spaceBar and returnKey
        val w = appearanceView.width
        val h = appearanceView.height
        if (w > 0 && h > 0) {
            onSizeChanged(w, h, w, h)
        }
    }
}

@SuppressLint("ViewConstructor")
open class TextKeyView(
    ctx: Context,
    theme: Theme,
    def: KeyDef.Appearance.Text,
    horizontalGapScale: Float = 1f
) :
    KeyView(ctx, theme, def, horizontalGapScale) {
    private val baseMainTextSizeSp: Float = when (def.viewId) {
        R.id.button_space -> def.textSize
        R.id.button_layout_switch -> def.textSize
        R.id.button_symbol_layout_switch -> def.textSize
        else -> org.fxboomk.fcitx5.android.input.font.FontProviders.getFontSize(
            "key_main_font", def.textSize
        )
    }

    val mainText = view(::AutoScaleTextView) {
        isClickable = false
        isFocusable = false
        background = null
        scaleMode = AutoScaleTextView.Mode.Proportional
        gravity = Gravity.CENTER
        text = def.displayText
        setTextSize(TypedValue.COMPLEX_UNIT_SP, baseMainTextSizeSp)
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
        // Set font key for batch setting in BaseKeyboard.reloadLayout()
        fontKey = "key_main_font"
        setTypeface(typeface, def.textStyle)
        setTextColor(
            resolveTextColor(
                when (def.variant) {
                    Variant.Normal -> theme.keyTextColor
                    Variant.AltForeground, Variant.Alternative -> theme.altKeyTextColor
                    Variant.Accent -> theme.accentKeyTextColor
                }
            )
        )
    }

    init {
        appearanceView.apply {
            if (def.viewId == R.id.button_space) {
                val insetPadding = dp(10)
                mainText.setPadding(insetPadding + hMargin, 0, insetPadding + hMargin, 0)
                add(mainText, lParams(matchParent, wrapContent) {
                    centerInParent()
                })
            } else {
                mainText.setPadding(hMargin, 0, hMargin, 0)
                add(mainText, lParams(matchParent, wrapContent) {
                    centerInParent()
                })
            }
        }
    }

    override fun setTextScale(scale: Float) {
        if (def is KeyDef.Appearance.Text) {
            mainText.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseMainTextSizeSp * scale)
            mainText.requestLayout()
        }
    }

    override fun updateTheme(newTheme: Theme) {
        super.updateTheme(newTheme)
        mainText.setTextColor(
            resolveTextColor(
                when (def.variant) {
                    Variant.Normal -> newTheme.keyTextColor
                    Variant.AltForeground, Variant.Alternative -> newTheme.altKeyTextColor
                    Variant.Accent -> newTheme.accentKeyTextColor
                }
            )
        )
    }
}

@SuppressLint("ViewConstructor")
class AltTextKeyView(
    ctx: Context,
    theme: Theme,
    def: KeyDef.Appearance.AltText,
    horizontalGapScale: Float = 1f
) :
    TextKeyView(ctx, theme, def, horizontalGapScale), SwipeHintAwareKeyView {
    private enum class AltTextLayoutMode {
        TopBottom,
        TopCorners,
        BottomCorners,
        Top,
        TopRight,
        Bottom,
        UpperTopPunctBottom,
        PunctTopUpperBottom,
        PunctTopRightUpperBottom,
        PunctUpperTopCorners,
        PunctUpperBottomCorners,
        PunctTopRightUpperTopLeft,
        UpperTop,
        UpperBottom,
        Independent,
        Hidden
    }

    private val baseAltTextSizeSp = org.fxboomk.fcitx5.android.input.font.FontProviders.getFontSize(
        "key_alt_font", 10.666667f
    )
    private var lastLayoutMode: AltTextLayoutMode? = null
    private var lastLayoutHeight = -1
    private var remainingSpaceTopLabels = emptyList<AutoScaleTextView>()
    private var remainingSpaceBottomLabels = emptyList<AutoScaleTextView>()

    /**
     * The base-class `def` is typed as the generic [KeyDef.Appearance];
     * member functions use this AltText-typed reference instead of re-casting.
     */
    private val altDef = def

    val altText = view(::AutoScaleTextView) {
        isClickable = false
        isFocusable = false
        scaleMode = AutoScaleTextView.Mode.Proportional
        gravity = Gravity.CENTER
        setPadding(hMargin, 0, hMargin, 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, baseAltTextSizeSp)
        // Set font key for batch setting in BaseKeyboard.reloadLayout()
        fontKey = "key_alt_font"
        setTypeface(typeface, Typeface.BOLD)
        text = def.altText
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
        setTextColor(
            resolveAltTextColor(
                when (def.variant) {
                    Variant.Normal, Variant.AltForeground, Variant.Alternative -> theme.altKeyTextColor
                    Variant.Accent -> theme.accentKeyTextColor
                }
            )
        )
    }

    val altText1 = view(::AutoScaleTextView) {
        isClickable = false
        isFocusable = false
        scaleMode = AutoScaleTextView.Mode.Proportional
        gravity = Gravity.CENTER
        setPadding(hMargin, 0, hMargin, 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, baseAltTextSizeSp)
        fontKey = "key_alt_font"
        setTypeface(typeface, Typeface.NORMAL)
        text = def.altText1.orEmpty()
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
        setTextColor(
            resolveAltTextColor(
                when (def.variant) {
                    Variant.Normal, Variant.AltForeground, Variant.Alternative -> theme.altKeyTextColor
                    Variant.Accent -> theme.accentKeyTextColor
                }
            )
        )
    }

    val upperText = view(::AutoScaleTextView) {
        isClickable = false
        isFocusable = false
        scaleMode = AutoScaleTextView.Mode.Proportional
        gravity = Gravity.CENTER
        setPadding(hMargin, 0, hMargin, 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, baseAltTextSizeSp)
        // Set font key for batch setting in BaseKeyboard.reloadLayout()
        fontKey = "key_alt_font"
        setTypeface(typeface, Typeface.BOLD)
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
        visibility = View.GONE
        setTextColor(
            resolveAltTextColor(
                when (def.variant) {
                    Variant.Normal, Variant.AltForeground, Variant.Alternative -> theme.altKeyTextColor
                    Variant.Accent -> theme.accentKeyTextColor
                }
            )
        )
    }

    init {
        appearanceView.apply {
            add(altText, lParams(0, wrapContent))
            add(altText1, lParams(0, wrapContent))
            add(upperText, lParams(0, wrapContent))
        }
        // 修复时序问题：使用 post 延后执行，确保获取到 layout 后的最终高度
        appearanceView.post {
            applyLayout()
        }
    }

    override fun setTextScale(scale: Float) {
        super.setTextScale(scale)
        altText.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseAltTextSizeSp * scale)
        altText1.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseAltTextSizeSp * scale)
        upperText.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseAltTextSizeSp * scale)
        altText.requestLayout()
        altText1.requestLayout()
        upperText.requestLayout()
        lastLayoutMode = null
        applyLayout()
    }

    private fun hasSecondAltText(): Boolean = !altText1.text.isNullOrBlank()

    private fun resolveUppercaseMode(): UppercasePosition {
        val pref = ThemeManager.prefs.uppercasePosition.getValue()
        if (pref == UppercasePosition.None) return UppercasePosition.None
        if (!altDef.supportsUppercaseHint) return UppercasePosition.None
        // Keys already showing two sublabels have no room for the uppercase hint,
        // unless the second sublabel is the auto-filled uppercase alias
        if (hasSecondAltText() && !isUppercaseAliasAlt1()) return UppercasePosition.None
        val character = altDef.character
        if (character.length != 1 || !character[0].isLetter()) return UppercasePosition.None
        return pref
    }

    /**
     * Whether [KeyDef.Appearance.AltText.altText1] is the uppercase letter of this key,
     * i.e. the value auto-filled by the layout editor when uppercase labels are enabled.
     */
    private fun isUppercaseAliasAlt1(): Boolean {
        val alt1 = altDef.altText1 ?: return false
        if (alt1.length != 1) return false
        val character = altDef.character
        return character.length == 1 && character[0].isLetter() && alt1 == character.uppercase()
    }

    internal fun uppercaseSwipeAction(): KeyAction? {
        if (resolveUppercaseMode() == UppercasePosition.None) return null
        val character = altDef.character
        if (character.length != 1) return null
        // Commit as-is: must not be lowercased by the caps state transformation
        return KeyAction.CommitAction(character.uppercase())
    }

    private fun syncUppercaseText() {
        val text = if (resolveUppercaseMode() == UppercasePosition.None) {
            ""
        } else {
            altDef.character.uppercase()
        }
        if (upperText.text.toString() != text) {
            upperText.text = text
        }
    }

    private fun applyMainTextCenterPosition() {
        mainText.gravity = Gravity.CENTER
        mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topMargin = 0
            bottomMargin = 0
            bottomToTop = unset
            topToTop = parentId
            bottomToBottom = parentId
        }
    }

    private fun applyMainTextAboveBottomAltPosition(anchor: View) {
        mainText.gravity = Gravity.CENTER
        mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            bottomToBottom = unset
            bottomMargin = 0
            topToTop = parentId
            topMargin = vMargin
            bottomToTop = anchor.existingOrNewId
        }
    }

    private fun applyTopRightAltTextPadding() {
        altText.setPaddingRelative(0, 0, cornerLabelHorizontalSafeInset, 0)
    }

    private fun applyBottomAltTextPadding() {
        altText.setPadding(hMargin, 0, hMargin, 0)
    }

    private fun applyBottomAltText1Padding() {
        altText1.setPadding(hMargin, 0, hMargin, 0)
    }

    private fun hideAltText1() {
        altText1.visibility = View.GONE
        applyBottomAltText1Padding()
        altText1.gravity = Gravity.CENTER
    }

    /**
     * Place a pair of sublabels at the top and bottom key edges. Uppercase pairs
     * additionally reserve a separate center region for the main label.
     */
    private fun applyVerticalPairAltTextPosition(topLabel: AutoScaleTextView, bottomLabel: AutoScaleTextView) {
        applyMainTextCenterPosition()
        val separateMainText = topLabel === upperText || bottomLabel === upperText
        topLabel.visibility = View.VISIBLE
        topLabel.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = 0
            bottomToBottom = unset
            bottomMargin = 0
            topToTop = parentId
            // A top/bottom pair is an edge-pinned layout, not a centered overlay.
            topMargin = vMargin
            leftToLeft = parentId
            leftMargin = hMargin
            rightToRight = parentId
            rightMargin = hMargin
        }
        topLabel.setPadding(hMargin, 0, hMargin, 0)
        topLabel.useGlyphBounds = true
        topLabel.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

        bottomLabel.visibility = View.VISIBLE
        bottomLabel.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = 0
            topToTop = unset
            topMargin = 0
            leftToLeft = parentId
            leftMargin = hMargin
            rightToRight = parentId
            rightMargin = hMargin
            bottomToBottom = parentId
            bottomMargin = vMargin
        }
        bottomLabel.setPadding(hMargin, 0, hMargin, 0)
        bottomLabel.useGlyphBounds = true
        bottomLabel.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        if (separateMainText) {
            mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
                height = 0
                topToTop = unset
                bottomToBottom = unset
                topToBottom = topLabel.existingOrNewId
                bottomToTop = bottomLabel.existingOrNewId
                topMargin = dp(2)
                bottomMargin = dp(2)
            }
        }
    }

    private fun applyCornerPairAltTextPosition(
        top: Boolean,
        secondLabel: AutoScaleTextView = altText1,
        primaryRight: Boolean = false
    ) {
        if (top) {
            applyMainTextCenterPosition()
        } else {
            applyMainTextAboveBottomAltPosition(altText)
        }

        val topMargin = vMargin + cornerLabelTopSafeInset
        val bottomMargin = vMargin + dp(2)
        altText.visibility = View.VISIBLE
        altText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = wrapContent
            leftToRight = unset
            rightToLeft = unset
            rightToRight = unset
            rightMargin = 0
            if (top) {
                topToTop = parentId
                this.topMargin = topMargin
                bottomToBottom = unset
                this.bottomMargin = 0
            } else {
                topToTop = unset
                this.topMargin = 0
                bottomToBottom = parentId
                this.bottomMargin = bottomMargin
            }
            leftToLeft = if (primaryRight) unset else parentId
            leftMargin = if (primaryRight) 0 else hMargin
            rightToRight = if (primaryRight) parentId else unset
            rightMargin = if (primaryRight) hMargin else 0
        }
        altText.setPadding(0, 0, 0, 0)
        altText.gravity = Gravity.CENTER

        secondLabel.visibility = View.VISIBLE
        secondLabel.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = wrapContent
            leftToLeft = if (primaryRight) parentId else unset
            leftToRight = unset
            leftMargin = if (primaryRight) hMargin else 0
            rightToLeft = unset
            if (top) {
                topToTop = parentId
                this.topMargin = topMargin
                bottomToBottom = unset
                this.bottomMargin = 0
            } else {
                topToTop = unset
                this.topMargin = 0
                bottomToBottom = parentId
                this.bottomMargin = bottomMargin
            }
            rightToRight = if (primaryRight) unset else parentId
            rightMargin = hMargin
        }
        secondLabel.setPadding(0, 0, 0, 0)
        secondLabel.gravity = Gravity.CENTER
    }

    private fun placeIndependentTopLabel(label: AutoScaleTextView, edge: Int) {
        label.visibility = View.VISIBLE
        label.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = 0
            leftToLeft = parentId
            leftMargin = hMargin
            rightToRight = parentId
            rightMargin = hMargin
            topToTop = parentId
            topMargin = vMargin + cornerLabelTopSafeInset
            bottomToBottom = unset
            bottomMargin = 0
        }
        label.setPadding(0, 0, if (edge > 0) cornerLabelHorizontalSafeInset else 0, 0)
        label.gravity = if (edge > 0) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.CENTER
    }

    private fun placeIndependentBottomLabel(label: AutoScaleTextView) {
        label.visibility = View.VISIBLE
        label.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = 0
            leftToLeft = parentId
            leftMargin = hMargin
            rightToRight = parentId
            rightMargin = hMargin
            topToTop = unset
            topMargin = 0
            bottomToBottom = parentId
            bottomMargin = vMargin + dp(2)
        }
        label.setPadding(0, 0, 0, 0)
        label.gravity = Gravity.CENTER
    }

    private fun applyIndependentAltTextPosition() {
        // The second label is either a stored alt1 or the auto uppercase hint
        // (rendered via upperText). resolveUppercaseMode() already returns None when
        // a distinct real alt1 exists, so a non-None value means the hint is shown.
        val uppercase = resolveUppercaseMode() != UppercasePosition.None
        val secondLabel = if (uppercase) upperText else altText1
        val primary = independentPosition(def.altTextPositionOverride)
        var secondary = independentAltText1Position()
        // There is only one right-top corner. Keep the primary label there and
        // move the second label to the left-top corner for legacy/invalid data.
        if (primary == PunctuationPosition.TopRight && secondary == PunctuationPosition.TopRight) {
            secondary = PunctuationPosition.Top
        }

        val primaryTop = primary == PunctuationPosition.Top || primary == PunctuationPosition.TopRight
        val secondaryTop = secondary == PunctuationPosition.Top || secondary == PunctuationPosition.TopRight

        if (altText.text.isNullOrBlank() || primary == PunctuationPosition.None) {
            altText.visibility = View.GONE
            when (secondary) {
                PunctuationPosition.Top -> placeIndependentTopLabel(secondLabel, 0)
                PunctuationPosition.TopRight -> placeIndependentTopLabel(secondLabel, 1)
                PunctuationPosition.Bottom -> placeIndependentBottomLabel(secondLabel)
                PunctuationPosition.None -> secondLabel.visibility = View.GONE
            }
            if (uppercase) hideAltText1() else hideUpperText()
            return
        }

        if (secondary == PunctuationPosition.None) {
            secondLabel.visibility = View.GONE
            when (primary) {
                PunctuationPosition.Top -> placeIndependentTopLabel(altText, 0)
                PunctuationPosition.TopRight -> placeIndependentTopLabel(altText, 1)
                PunctuationPosition.Bottom -> placeIndependentBottomLabel(altText)
                PunctuationPosition.None -> altText.visibility = View.GONE
            }
            if (uppercase) hideAltText1() else hideUpperText()
            return
        }

        if (primaryTop && secondaryTop) {
            // At most one label may be TopRight (normalized above). Split the two
            // labels to opposite top corners so they never overlap — including the
            // both-"Top" case, where the primary takes the left corner.
            val primaryRight = primary == PunctuationPosition.TopRight
            applyCornerPairAltTextPosition(top = true, secondLabel = secondLabel, primaryRight = primaryRight)
        } else if (primaryTop != secondaryTop) {
            if (primaryTop) {
                placeIndependentTopLabel(altText, if (primary == PunctuationPosition.TopRight) 1 else 0)
                placeIndependentBottomLabel(secondLabel)
            } else {
                placeIndependentTopLabel(secondLabel, if (secondary == PunctuationPosition.TopRight) 1 else 0)
                placeIndependentBottomLabel(altText)
            }
        } else {
            applyCornerPairAltTextPosition(top = false, secondLabel = secondLabel)
        }
        if (uppercase) hideAltText1() else hideUpperText()
    }

    private fun positionAltTextAtTopRight() {
        altText.visibility = View.VISIBLE
        altText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            // reset
            width = 0
            bottomToBottom = unset; bottomMargin = 0
            // set
            topToTop = parentId; topMargin = vMargin + cornerLabelTopSafeInset
            leftToLeft = parentId; leftMargin = hMargin
            rightToRight = parentId; rightMargin = hMargin
        }
        applyTopRightAltTextPadding()
        altText.gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }

    private fun applyTopRightAltTextPosition() {
        applyMainTextCenterPosition()
        positionAltTextAtTopRight()
        hideAltText1()
        hideUpperText()
    }

    private fun applyTopAltTextPosition() {
        mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topMargin = 0
            bottomToTop = unset
            topToTop = parentId
            bottomToBottom = parentId
        }
        altText.visibility = View.VISIBLE
        altText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = 0
            bottomToBottom = unset
            bottomMargin = 0
            topToTop = parentId
            topMargin = vMargin + cornerLabelTopSafeInset
            leftToLeft = parentId
            leftMargin = hMargin
            rightToRight = parentId
            rightMargin = hMargin
        }
        applyBottomAltTextPadding()
        altText.gravity = Gravity.CENTER
        hideAltText1()
        hideUpperText()
    }

    private fun applyBottomAltTextPosition() {
        mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            // reset
            bottomToBottom = unset
            // set
            topToTop = parentId; topMargin = vMargin
            bottomToTop = altText.existingOrNewId
        }
        altText.visibility = View.VISIBLE
        altText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            // reset
            width = 0
            topToTop = unset; topMargin = 0
            leftMargin = hMargin
            rightMargin = hMargin
            // set
            leftToLeft = parentId
            rightToRight = parentId
            bottomToBottom = parentId; bottomMargin = vMargin + dp(2)
        }
        applyBottomAltTextPadding()
        altText.gravity = Gravity.CENTER
        hideAltText1()
        hideUpperText()
    }

    private fun applyNoAltTextPosition() {
        mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            // reset
            topMargin = 0
            bottomToTop = unset
            // set
            topToTop = parentId
            bottomToBottom = parentId
        }
        altText.visibility = View.GONE
        applyBottomAltTextPadding()
        altText.gravity = Gravity.CENTER
        hideAltText1()
        hideUpperText()
    }

    private fun hideUpperText() {
        upperText.visibility = View.GONE
    }

    private fun showUpperTextAtTop() {
        upperText.visibility = View.VISIBLE
        upperText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = 0
            bottomToBottom = unset
            bottomMargin = 0
            topToTop = parentId
            topMargin = vMargin + cornerLabelTopSafeInset
            leftToLeft = parentId
            leftMargin = hMargin
            rightToRight = parentId
            rightMargin = hMargin
        }
        upperText.setPadding(hMargin, 0, hMargin, 0)
        upperText.gravity = Gravity.CENTER
    }

    private fun showUpperTextAtBottom() {
        upperText.visibility = View.VISIBLE
        upperText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = 0
            topToTop = unset
            topMargin = 0
            leftToLeft = parentId
            leftMargin = hMargin
            rightToRight = parentId
            rightMargin = hMargin
            bottomToBottom = parentId
            bottomMargin = vMargin + dp(2)
        }
        upperText.setPadding(hMargin, 0, hMargin, 0)
        upperText.gravity = Gravity.CENTER
    }

    private fun showUpperTextAtCorner(top: Boolean, left: Boolean) {
        upperText.visibility = View.VISIBLE
        upperText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = wrapContent
            if (top) {
                topToTop = parentId
                topMargin = vMargin + cornerLabelTopSafeInset
                bottomToBottom = unset
                bottomMargin = 0
            } else {
                topToTop = unset
                topMargin = 0
                bottomToBottom = parentId
                bottomMargin = vMargin + dp(2)
            }
            if (left) {
                leftToLeft = parentId
                leftMargin = hMargin
                rightToRight = unset
                rightMargin = 0
            } else {
                leftToLeft = unset
                leftMargin = 0
                rightToRight = parentId
                rightMargin = hMargin
            }
        }
        upperText.setPadding(0, 0, 0, 0)
        upperText.gravity = Gravity.CENTER
    }

    private fun applyUpperTopPosition() {
        applyMainTextCenterPosition()
        altText.visibility = View.GONE
        showUpperTextAtTop()
        hideAltText1()
    }

    private fun applyUpperBottomPosition() {
        applyMainTextAboveBottomAltPosition(upperText)
        altText.visibility = View.GONE
        showUpperTextAtBottom()
        hideAltText1()
    }

    private fun resolveThemeLayoutMode(): AltTextLayoutMode {
        val pref = ThemeManager.prefs.punctuationPosition.getValue()
        if (pref == PunctuationPosition.None) return AltTextLayoutMode.Hidden
        return when (pref) {
            PunctuationPosition.Top -> AltTextLayoutMode.Top
            PunctuationPosition.TopRight -> AltTextLayoutMode.TopRight
            PunctuationPosition.Bottom -> AltTextLayoutMode.Bottom
            PunctuationPosition.None -> AltTextLayoutMode.Hidden
        }
    }

    private fun resolveThemeCornerPairLayoutMode(): AltTextLayoutMode {
        return when (ThemeManager.prefs.punctuationPosition.getValue()) {
            PunctuationPosition.Top, PunctuationPosition.TopRight -> AltTextLayoutMode.TopCorners
            PunctuationPosition.Bottom -> AltTextLayoutMode.BottomCorners
            PunctuationPosition.None -> AltTextLayoutMode.Hidden
        }
    }

    /**
     * The punctuation sublabel position for this key: the row-level override set in
     * the layout editor when present, otherwise the global theme preference.
     */
    private fun effectivePunctuationPosition(): PunctuationPosition {
        return independentPosition(def.altTextPositionOverride)
    }

    private fun resolveLayoutMode(keyHeight: Int): AltTextLayoutMode {
        if (shouldUseIndependentAltTextPositions()) {
            return resolveIndependentLayoutMode(keyHeight)
        }
        val uppercase = resolveUppercaseMode()
        if (uppercase != UppercasePosition.None) {
            return resolveUppercaseLayoutMode(keyHeight, uppercase)
        }
        return resolvePunctuationLayoutMode(keyHeight)
    }

    private fun shouldUseIndependentAltTextPositions(): Boolean {
        // The second label participates in independent positioning whether it is a
        // stored alt1 or the auto uppercase hint (upperText). A row-level override
        // for either label then takes precedence over the global theme positions.
        val hasSecondLabel = hasSecondAltText() || resolveUppercaseMode() != UppercasePosition.None
        return hasSecondLabel &&
                (def.altText1PositionOverride != null ||
                    (def.altTextPositionOverride != null &&
                        def.altTextPositionOverride != KeyDef.Appearance.AltTextPosition.TopBottom))
    }

    private fun independentPosition(position: KeyDef.Appearance.AltTextPosition?): PunctuationPosition {
        return when (position) {
            KeyDef.Appearance.AltTextPosition.Top -> PunctuationPosition.Top
            KeyDef.Appearance.AltTextPosition.TopRight -> PunctuationPosition.TopRight
            KeyDef.Appearance.AltTextPosition.Bottom -> PunctuationPosition.Bottom
            KeyDef.Appearance.AltTextPosition.TopBottom, null ->
                ThemeManager.prefs.punctuationPosition.getValue()
        }
    }

    private fun independentAltText1Position(): PunctuationPosition {
        val override = def.altText1PositionOverride
        if (override != null) return independentPosition(override)
        // No explicit 副字符一位置: the uppercase hint keeps following the global
        // uppercase position, everything else follows the global punctuation position.
        if (resolveUppercaseMode() != UppercasePosition.None) {
            return when (ThemeManager.prefs.uppercasePosition.getValue()) {
                UppercasePosition.Top -> PunctuationPosition.Top
                UppercasePosition.Bottom -> PunctuationPosition.Bottom
                UppercasePosition.None -> PunctuationPosition.Bottom
            }
        }
        return independentPosition(null)
    }

    private fun resolveIndependentLayoutMode(keyHeight: Int): AltTextLayoutMode {
        if (keyHeight <= 0) return AltTextLayoutMode.Independent
        val contentHeight = keyHeight - vMargin * 2
        val primary = independentPosition(def.altTextPositionOverride)
        val secondary = independentAltText1Position()
        if (!altText.text.isNullOrBlank() &&
            (primary == PunctuationPosition.Top || primary == PunctuationPosition.TopRight) &&
            (secondary == PunctuationPosition.Top || secondary == PunctuationPosition.TopRight)
        ) {
            return if (contentHeight >= cornerLabelTopSafeInset + dp(4) + 3) {
                AltTextLayoutMode.Independent
            } else AltTextLayoutMode.Hidden
        }
        val minHeight = max(
            altText.paint.run { fontMetrics.bottom - fontMetrics.top },
            altText1.paint.run { fontMetrics.bottom - fontMetrics.top }
        ) + cornerLabelTopSafeInset
        return if (contentHeight >= minHeight) AltTextLayoutMode.Independent else AltTextLayoutMode.Hidden
    }

    private fun resolveUppercaseLayoutMode(keyHeight: Int, uppercase: UppercasePosition): AltTextLayoutMode {
        val hasPunct = !altText.text.isNullOrBlank()
        val punctPref = effectivePunctuationPosition()
        // Either label set to "None" simply hides that label; the other keeps its own position
        val preferred = if (!hasPunct || punctPref == PunctuationPosition.None) {
            if (uppercase == UppercasePosition.Top) AltTextLayoutMode.UpperTop else AltTextLayoutMode.UpperBottom
        } else when (uppercase) {
            UppercasePosition.Top -> when (punctPref) {
                PunctuationPosition.Bottom -> AltTextLayoutMode.UpperTopPunctBottom
                PunctuationPosition.Top -> AltTextLayoutMode.PunctUpperTopCorners
                PunctuationPosition.TopRight -> AltTextLayoutMode.PunctTopRightUpperTopLeft
                PunctuationPosition.None -> AltTextLayoutMode.UpperTop
            }
            UppercasePosition.Bottom -> when (punctPref) {
                PunctuationPosition.Bottom -> AltTextLayoutMode.PunctUpperBottomCorners
                PunctuationPosition.Top -> AltTextLayoutMode.PunctTopUpperBottom
                PunctuationPosition.TopRight -> AltTextLayoutMode.PunctTopRightUpperBottom
                PunctuationPosition.None -> AltTextLayoutMode.UpperBottom
            }
            UppercasePosition.None -> AltTextLayoutMode.Hidden
        }
        if (keyHeight <= 0) return preferred

        val contentHeight = keyHeight - vMargin * 2
        val mainHeight = mainText.paint.run { fontMetrics.bottom - fontMetrics.top }
        val altHeight = altText.paint.run { fontMetrics.bottom - fontMetrics.top }
        val upperHeight = upperText.paint.run { fontMetrics.bottom - fontMetrics.top }
        // Compact overlays only need the sublabel itself to fit: the centered main
        // text auto-scales down on short rows (e.g. 0.75x heightMultiplier rows),
        // so its unscaled metrics must not gate the compact minimum height.
        val compactMinHeight = altHeight + cornerLabelTopSafeInset
        val dualCompactMinHeight = max(compactMinHeight, upperHeight + cornerLabelTopSafeInset)
        val upperCompactMinHeight = upperHeight + cornerLabelTopSafeInset
        val upperStackedMinHeight = mainHeight + upperHeight + dp(1)

        return when (preferred) {
            AltTextLayoutMode.UpperTopPunctBottom,
            AltTextLayoutMode.PunctTopUpperBottom -> when {
                // All three labels can scale inside their own regions on short rows.
                contentHeight >= cornerLabelTopSafeInset * 2 + dp(4) + 3 -> preferred
                else -> resolvePunctuationLayoutMode(keyHeight)
            }
            AltTextLayoutMode.PunctUpperTopCorners,
            AltTextLayoutMode.PunctTopRightUpperTopLeft -> when {
                contentHeight >= cornerLabelTopSafeInset + dp(4) + 3 -> preferred
                else -> AltTextLayoutMode.Hidden
            }
            AltTextLayoutMode.PunctTopRightUpperBottom,
            AltTextLayoutMode.PunctUpperBottomCorners -> when {
                contentHeight >= dualCompactMinHeight -> preferred
                // Not enough room for both sublabels: fall back to punctuation-only layout
                else -> resolvePunctuationLayoutMode(keyHeight)
            }
            AltTextLayoutMode.UpperTop -> when {
                contentHeight >= upperCompactMinHeight -> preferred
                else -> AltTextLayoutMode.Hidden
            }
            AltTextLayoutMode.UpperBottom -> when {
                contentHeight >= upperStackedMinHeight -> preferred
                else -> AltTextLayoutMode.Hidden
            }
            else -> resolvePunctuationLayoutMode(keyHeight)
        }
    }

    private fun resolvePunctuationLayoutMode(keyHeight: Int): AltTextLayoutMode {
        if (altText.text.isNullOrBlank()) return AltTextLayoutMode.Hidden
        // An explicit row-level override must display even when the global
        // preference hides sublabels everywhere else
        if (def.altTextPositionOverride == null &&
            ThemeManager.prefs.punctuationPosition.getValue() == PunctuationPosition.None
        ) {
            return AltTextLayoutMode.Hidden
        }
        val hasSecondAlt = hasSecondAltText()
        val preferred = when (def.altTextPositionOverride) {
            KeyDef.Appearance.AltTextPosition.TopBottom -> {
                if (hasSecondAlt) AltTextLayoutMode.TopBottom else resolveThemeLayoutMode()
            }
            KeyDef.Appearance.AltTextPosition.Top -> {
                if (hasSecondAlt) AltTextLayoutMode.TopCorners else AltTextLayoutMode.Top
            }
            KeyDef.Appearance.AltTextPosition.TopRight -> {
                if (hasSecondAlt) AltTextLayoutMode.TopCorners else AltTextLayoutMode.TopRight
            }
            KeyDef.Appearance.AltTextPosition.Bottom -> {
                if (hasSecondAlt) AltTextLayoutMode.BottomCorners else AltTextLayoutMode.Bottom
            }
            null -> if (hasSecondAlt) resolveThemeCornerPairLayoutMode() else resolveThemeLayoutMode()
        }
        if (keyHeight <= 0) return preferred

        val contentHeight = keyHeight - vMargin * 2
        val altHeight = altText.paint.run { fontMetrics.bottom - fontMetrics.top }
        val altText1Height = altText1.paint.run { fontMetrics.bottom - fontMetrics.top }
        // Compact overlays only need the sublabel itself to fit: the centered main
        // text auto-scales down on short rows (e.g. 0.75x heightMultiplier rows),
        // so its unscaled metrics must not gate the compact minimum height.
        val compactMinHeight = altHeight + cornerLabelTopSafeInset
        val topBottomCompactMinHeight = max(compactMinHeight, altText1Height + cornerLabelTopSafeInset)

        return when (preferred) {
            AltTextLayoutMode.TopBottom -> when {
                contentHeight >= topBottomCompactMinHeight -> AltTextLayoutMode.TopBottom
                hasSecondAlt && contentHeight >= compactMinHeight -> resolveThemeCornerPairLayoutMode()
                else -> AltTextLayoutMode.Hidden
            }
            AltTextLayoutMode.Bottom -> when {
                contentHeight >= compactMinHeight -> AltTextLayoutMode.Bottom
                else -> AltTextLayoutMode.Hidden
            }
            AltTextLayoutMode.TopCorners -> when {
                contentHeight >= cornerLabelTopSafeInset + dp(4) + 3 -> preferred
                else -> AltTextLayoutMode.Hidden
            }
            AltTextLayoutMode.BottomCorners -> when {
                contentHeight >= compactMinHeight -> preferred
                else -> AltTextLayoutMode.Hidden
            }
            AltTextLayoutMode.Top -> when {
                contentHeight >= compactMinHeight -> AltTextLayoutMode.Top
                else -> AltTextLayoutMode.Hidden
            }
            AltTextLayoutMode.TopRight -> when {
                contentHeight >= compactMinHeight -> AltTextLayoutMode.TopRight
                else -> AltTextLayoutMode.Hidden
            }
            AltTextLayoutMode.Hidden -> AltTextLayoutMode.Hidden
            // Uppercase modes are resolved in resolveUppercaseLayoutMode and never reach here
            else -> resolveThemeLayoutMode()
        }
    }

    private fun applyLayout(keyHeight: Int = appearanceView.height) {
        syncUppercaseText()
        val mode = resolveLayoutMode(keyHeight)
        if (mode == lastLayoutMode && keyHeight == lastLayoutHeight) {
            updateRemainingSpaceLabels(keyHeight)
            return
        }
        lastLayoutMode = mode
        lastLayoutHeight = keyHeight
        remainingSpaceTopLabels = emptyList()
        remainingSpaceBottomLabels = emptyList()
        mainText.glyphPlacement = null
        mainText.useGlyphBounds = false
        val separateMainText = mode == AltTextLayoutMode.UpperTopPunctBottom ||
                mode == AltTextLayoutMode.PunctTopUpperBottom
        val labelMaxHeight = if (separateMainText && keyHeight > 0) {
            // Reserve at least a third of the usable height for the main label.
            ((keyHeight - vMargin * 2 - cornerLabelTopSafeInset * 2 - dp(4)) / 3)
                .coerceAtLeast(1)
        } else {
            Int.MAX_VALUE
        }
        altText.useGlyphBounds = separateMainText
        upperText.useGlyphBounds = separateMainText
        altText.maxHeight = labelMaxHeight
        altText1.maxHeight = Int.MAX_VALUE
        upperText.maxHeight = labelMaxHeight
        mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = wrapContent
            topToBottom = unset
            topMargin = 0
            bottomMargin = 0
        }
        when (mode) {
            AltTextLayoutMode.TopBottom -> {
                applyVerticalPairAltTextPosition(altText, altText1)
                hideUpperText()
            }
            AltTextLayoutMode.TopCorners -> {
                applyCornerPairAltTextPosition(top = true)
                hideUpperText()
            }
            AltTextLayoutMode.BottomCorners -> {
                applyCornerPairAltTextPosition(top = false)
                hideUpperText()
            }
            AltTextLayoutMode.Bottom -> applyBottomAltTextPosition()
            AltTextLayoutMode.Top -> applyTopAltTextPosition()
            AltTextLayoutMode.TopRight -> applyTopRightAltTextPosition()
            AltTextLayoutMode.UpperTopPunctBottom -> {
                applyVerticalPairAltTextPosition(upperText, altText)
                hideAltText1()
            }
            AltTextLayoutMode.PunctTopUpperBottom -> {
                applyVerticalPairAltTextPosition(altText, upperText)
                hideAltText1()
            }
            AltTextLayoutMode.PunctTopRightUpperBottom -> {
                positionAltTextAtTopRight()
                showUpperTextAtBottom()
                hideAltText1()
            }
            AltTextLayoutMode.PunctUpperTopCorners -> {
                applyCornerPairAltTextPosition(top = true, secondLabel = upperText)
                hideAltText1()
            }
            AltTextLayoutMode.PunctUpperBottomCorners -> {
                applyCornerPairAltTextPosition(top = false, secondLabel = upperText)
                hideAltText1()
            }
            AltTextLayoutMode.PunctTopRightUpperTopLeft -> {
                positionAltTextAtTopRight()
                showUpperTextAtCorner(top = true, left = true)
                hideAltText1()
            }
            AltTextLayoutMode.UpperTop -> applyUpperTopPosition()
            AltTextLayoutMode.UpperBottom -> applyUpperBottomPosition()
            AltTextLayoutMode.Independent -> applyIndependentAltTextPosition()
            AltTextLayoutMode.Hidden -> applyNoAltTextPosition()
        }
        updateRemainingSpaceLabels(keyHeight)
    }
    private fun updateRemainingSpaceLabels(keyHeight: Int) {
        val visibleLabels = listOf(altText, altText1, upperText).filter {
            it.visibility == View.VISIBLE && !it.text.isNullOrBlank()
        }
        remainingSpaceTopLabels = visibleLabels.filter {
            (it.layoutParams as ConstraintLayout.LayoutParams).topToTop == ConstraintLayout.LayoutParams.PARENT_ID
        }
        remainingSpaceBottomLabels = visibleLabels.filter {
            (it.layoutParams as ConstraintLayout.LayoutParams).bottomToBottom == ConstraintLayout.LayoutParams.PARENT_ID
        }
        // Preserve each sublabel's anchor, but reserve room for the main ink and
        // two gaps, including on short rows with oversized fonts.
        if (visibleLabels.isNotEmpty()) {
            val topInset = remainingSpaceTopLabels.maxOfOrNull {
                (it.layoutParams as ConstraintLayout.LayoutParams).topMargin
            } ?: vMargin
            val bottomInset = remainingSpaceBottomLabels.maxOfOrNull {
                (it.layoutParams as ConstraintLayout.LayoutParams).bottomMargin
            } ?: vMargin
            val hasSingleEdge = remainingSpaceTopLabels.isNotEmpty() xor
                remainingSpaceBottomLabels.isNotEmpty()
            if (keyHeight > 0 && hasSingleEdge) {
                val maxLabelHeight = ((keyHeight - topInset - bottomInset - dp(4)) / 3)
                    .coerceAtLeast(1)
                // Same-edge corner labels need a bounded band; mixed-edge labels
                // already have independent edge constraints and their own sizing.
                visibleLabels.forEach { it.maxHeight = maxLabelHeight }
            }
            mainText.useGlyphBounds = true
            // A full-height drawing region avoids clipping ink positioned relative
            // to other glyphs rather than their (larger) TextView boxes.
            mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
                height = 0
                topToTop = parentId
                bottomToBottom = parentId
                topToBottom = unset
                bottomToTop = unset
                topMargin = 0
                bottomMargin = 0
            }
        } else {
            mainText.glyphPlacement = null
            applyMainTextCenterPosition()
        }
    }


    override fun secondarySwipeTarget(): AltTextSwipeTarget {
        return if (resolveUppercaseMode() != UppercasePosition.None) {
            AltTextSwipeTarget.Uppercase
        } else {
            AltTextSwipeTarget.Secondary
        }
    }

    override fun selectAltTextSwipeTarget(totalY: Int): AltTextSwipeTarget? {
        if (totalY == 0) return null
        val mode = lastLayoutMode ?: resolveLayoutMode(appearanceView.height)
        // Fallback layouts (TopBottom/TopCorners/BottomCorners) may carry the uppercase alias
        val secondary = secondarySwipeTarget()
        return when (mode) {
            AltTextLayoutMode.TopBottom,
            AltTextLayoutMode.TopCorners -> if (totalY < 0) {
                AltTextSwipeTarget.Primary
            } else {
                secondary
            }
            AltTextLayoutMode.BottomCorners -> if (totalY < 0) {
                secondary
            } else {
                AltTextSwipeTarget.Primary
            }
            AltTextLayoutMode.Top,
            AltTextLayoutMode.TopRight ->
                AltTextSwipeTarget.Primary.takeIf { totalY < 0 }
            AltTextLayoutMode.Bottom ->
                AltTextSwipeTarget.Primary.takeIf { totalY > 0 }
            // Punctuation sits above the uppercase hint: swipe up commits
            // punctuation, swipe down commits the uppercase letter
            AltTextLayoutMode.PunctTopUpperBottom,
            AltTextLayoutMode.PunctTopRightUpperBottom,
            AltTextLayoutMode.PunctUpperTopCorners,
            AltTextLayoutMode.PunctTopRightUpperTopLeft -> if (totalY < 0) {
                AltTextSwipeTarget.Primary
            } else {
                AltTextSwipeTarget.Uppercase
            }
            // Uppercase hint sits above the punctuation: swipe up commits the
            // uppercase letter, swipe down commits punctuation
            AltTextLayoutMode.UpperTopPunctBottom,
            AltTextLayoutMode.PunctUpperBottomCorners -> if (totalY > 0) {
                AltTextSwipeTarget.Primary
            } else {
                AltTextSwipeTarget.Uppercase
            }
            AltTextLayoutMode.UpperTop ->
                AltTextSwipeTarget.Uppercase.takeIf { totalY < 0 }
            AltTextLayoutMode.UpperBottom ->
                AltTextSwipeTarget.Uppercase.takeIf { totalY > 0 }
            // 顶底双角标 (both labels on the same edge): fixed gesture regardless of
            // edge — swipe up = 副字符一 (uppercase / second alt), swipe down = 副字符
            // (punctuation). 一顶一底 (mixed) and single-label layouts instead follow
            // each label's actual vertical position (up = top label, down = bottom).
            AltTextLayoutMode.Independent -> {
                val primaryPos = independentPosition(def.altTextPositionOverride)
                val secondaryPos = independentAltText1Position()
                val primaryShown = !altText.text.isNullOrBlank() && primaryPos != PunctuationPosition.None
                val secondaryShown = secondaryPos != PunctuationPosition.None
                val primaryTop = primaryPos == PunctuationPosition.Top ||
                    primaryPos == PunctuationPosition.TopRight
                val secondaryTop = secondaryPos == PunctuationPosition.Top ||
                    secondaryPos == PunctuationPosition.TopRight
                if (primaryShown && secondaryShown && primaryTop == secondaryTop) {
                    if (totalY < 0) secondary else AltTextSwipeTarget.Primary
                } else {
                    val topTarget = when {
                        primaryShown && primaryTop -> AltTextSwipeTarget.Primary
                        secondaryShown && secondaryTop -> secondary
                        else -> null
                    }
                    val bottomTarget = when {
                        primaryShown && !primaryTop -> AltTextSwipeTarget.Primary
                        secondaryShown && !secondaryTop -> secondary
                        else -> null
                    }
                    if (totalY < 0) topTarget else bottomTarget
                }
            }
            AltTextLayoutMode.Hidden -> null
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        lastLayoutMode = null
        // 修复时序问题：使用 post 延后执行，确保获取到 layout 后的最终高度
        appearanceView.post {
            applyLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = MeasureSpec.getSize(heightMeasureSpec)
        if (height > 0) applyLayout(height)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onAppearanceLayoutChanged(width: Int, height: Int) {
        applyLayout(height)
        // Standalone previews can contain one key. BaseKeyboard repeats this once
        // for the complete row after every sibling has finished laying out.
        alignRemainingSpaceMainLabels(listOf(this))
    }

    companion object {
        internal fun alignRemainingSpaceMainLabels(keys: List<AltTextKeyView>) {
            val eligible = keys.filter { key ->
                key.visibility == View.VISIBLE &&
                    (key.remainingSpaceTopLabels.isNotEmpty() || key.remainingSpaceBottomLabels.isNotEmpty()) &&
                    key.mainText.width > 0 && key.mainText.height > 0
            }
            // Keys with the same top/bottom occupancy share a baseline. Different
            // remaining-space bands are centered independently.
            eligible.groupBy { key ->
                key.remainingSpaceTopLabels.isNotEmpty() to key.remainingSpaceBottomLabels.isNotEmpty()
            }.values.forEach { band ->
                val glyphs = band.mapNotNull { key ->
                    val text = key.mainText.text.toString()
                    val bounds = Rect()
                    key.mainText.paint.getTextBounds(text, 0, text.length, bounds)
                    if (bounds.isEmpty) return@mapNotNull null
                    val availableWidth = (key.mainText.width - key.mainText.paddingLeft - key.mainText.paddingRight)
                        .coerceAtLeast(0)
                    Triple(key, bounds, min(1f, availableWidth.toFloat() / bounds.width()))
                }
                if (glyphs.isEmpty()) return@forEach

                val bandTop = if (glyphs.first().first.remainingSpaceTopLabels.isNotEmpty()) {
                    glyphs.maxOf { (key) ->
                        key.remainingSpaceTopLabels.maxOf { label ->
                            key.top + key.appearanceView.top + label.top + label.renderedGlyphBounds().bottom
                        }
                    }
                } else {
                    glyphs.maxOf { (key) ->
                        (key.top + key.appearanceView.top + key.vMargin).toFloat()
                    }
                }
                val bandBottom = if (glyphs.first().first.remainingSpaceBottomLabels.isNotEmpty()) {
                    glyphs.minOf { (key) ->
                        key.remainingSpaceBottomLabels.minOf { label ->
                            key.top + key.appearanceView.top + label.top + label.renderedGlyphBounds().top
                        }
                    }
                } else {
                    glyphs.minOf { (key) ->
                        (key.top + key.appearanceView.top + key.appearanceView.height - key.vMargin).toFloat()
                    }
                }
                val glyphTop = glyphs.minOf { (_, bounds, scale) -> bounds.top * scale }
                val glyphBottom = glyphs.maxOf { (_, bounds, scale) -> bounds.bottom * scale }
                val minGap = glyphs.maxOf { (key) -> key.dp(2) }
                val availableHeight = (bandBottom - bandTop - minGap * 2).coerceAtLeast(0f)
                val heightScale = min(1f, availableHeight / (glyphBottom - glyphTop))
                // Align actual ink, not font metrics, halfway through the free band.
                val baseline = (bandTop + bandBottom - (glyphTop + glyphBottom) * heightScale) / 2f
                glyphs.forEach { (key, _, widthScale) ->
                    key.mainText.glyphPlacement = AutoScaleTextView.GlyphPlacement(
                        baseline - key.top - key.appearanceView.top - key.mainText.top,
                        widthScale * heightScale
                    )
                }
            }
        }
    }

    /**
     * Force refresh layout with current final height.
     * Used by BaseKeyboard to ensure correct layout after keyboard size is fully applied.
     */
    internal fun refreshLayout() {
        lastLayoutMode = null
        applyLayout()
    }

    override fun updateTheme(newTheme: Theme) {
        super.updateTheme(newTheme)
        lastLayoutMode = null
        // 修复时序问题：使用 post 延后执行，确保获取到 layout 后的最终高度
        appearanceView.post {
            applyLayout()
        }
        altText.setTextColor(
            resolveAltTextColor(
                when (def.variant) {
                    Variant.Normal, Variant.AltForeground, Variant.Alternative -> newTheme.altKeyTextColor
                    Variant.Accent -> newTheme.accentKeyTextColor
                }
            )
        )
        altText1.setTextColor(
            resolveAltTextColor(
                when (def.variant) {
                    Variant.Normal, Variant.AltForeground, Variant.Alternative -> newTheme.altKeyTextColor
                    Variant.Accent -> newTheme.accentKeyTextColor
                }
            )
        )
        upperText.setTextColor(
            resolveAltTextColor(
                when (def.variant) {
                    Variant.Normal, Variant.AltForeground, Variant.Alternative -> newTheme.altKeyTextColor
                    Variant.Accent -> newTheme.accentKeyTextColor
                }
            )
        )
        lastLayoutMode = null
        applyLayout()
    }
}

@SuppressLint("ViewConstructor")
class ImageAltTextKeyView(
    ctx: Context,
    theme: Theme,
    def: KeyDef.Appearance.ImageAltText,
    horizontalGapScale: Float = 1f
) : KeyView(ctx, theme, def, horizontalGapScale), SwipeHintAwareKeyView, KeyViewWithImage {
    private enum class AltTextLayoutMode {
        Top,
        TopRight,
        Bottom,
        Hidden
    }

    private val baseAltTextSizeSp = org.fxboomk.fcitx5.android.input.font.FontProviders.getFontSize(
        "key_alt_font", 10.666667f
    )
    private var lastLayoutMode: AltTextLayoutMode? = null

    override val img = imageView { configure(theme, def.src, def.variant, def.viewId) }.apply {
        imageTintList = ColorStateList.valueOf(
            resolveTextColor(
                when (def.variant) {
                    Variant.Normal -> theme.keyTextColor
                    Variant.AltForeground, Variant.Alternative -> theme.altKeyTextColor
                    Variant.Accent -> theme.accentKeyTextColor
                }
            )
        )
    }

    val altText = view(::AutoScaleTextView) {
        isClickable = false
        isFocusable = false
        scaleMode = AutoScaleTextView.Mode.Proportional
        gravity = Gravity.CENTER
        setPadding(hMargin, 0, hMargin, 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, baseAltTextSizeSp)
        fontKey = "key_alt_font"
        setTypeface(typeface, Typeface.BOLD)
        text = def.altText
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
        setTextColor(
            resolveAltTextColor(
                when (def.variant) {
                    Variant.Normal, Variant.AltForeground, Variant.Alternative -> theme.altKeyTextColor
                    Variant.Accent -> theme.accentKeyTextColor
                }
            )
        )
    }

    init {
        appearanceView.apply {
            add(img, lParams(wrapContent, wrapContent))
            add(altText, lParams(0, wrapContent))
        }
        applyLayout()
    }

    override fun setTextScale(scale: Float) {
        altText.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseAltTextSizeSp * scale)
        altText.requestLayout()
        lastLayoutMode = null
        applyLayout()
    }


    private fun applyTopRightAltTextPadding() {
        altText.setPaddingRelative(0, 0, cornerLabelHorizontalSafeInset, 0)
    }

    private fun applyBottomAltTextPadding() {
        altText.setPadding(hMargin, 0, hMargin, 0)
    }

    private fun applyTopRightAltTextPosition() {
        img.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topToTop = parentId
            bottomToBottom = parentId
            startToStart = parentId
            endToEnd = parentId
            topMargin = 0
            bottomMargin = 0
            bottomToTop = unset
        }
        altText.visibility = View.VISIBLE
        altText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = 0
            topToTop = parentId; topMargin = vMargin + cornerLabelTopSafeInset
            bottomToBottom = unset; bottomMargin = 0
            leftToLeft = parentId; leftMargin = hMargin
            rightToRight = parentId; rightMargin = hMargin
        }
        applyTopRightAltTextPadding()
        altText.gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }

    private fun applyTopAltTextPosition() {
        img.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topToTop = parentId
            bottomToBottom = parentId
            startToStart = parentId
            endToEnd = parentId
            topMargin = 0
            bottomMargin = 0
            bottomToTop = unset
        }
        altText.visibility = View.VISIBLE
        altText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = 0
            topToTop = parentId
            topMargin = vMargin + cornerLabelTopSafeInset
            bottomToBottom = unset
            bottomMargin = 0
            leftToLeft = parentId
            leftMargin = hMargin
            rightToRight = parentId
            rightMargin = hMargin
        }
        applyBottomAltTextPadding()
        altText.gravity = Gravity.CENTER
    }

    private fun applyBottomAltTextPosition() {
        img.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topToTop = parentId; topMargin = vMargin
            bottomToTop = altText.existingOrNewId
            bottomToBottom = unset; bottomMargin = 0
            startToStart = parentId
            endToEnd = parentId
        }
        altText.visibility = View.VISIBLE
        altText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = 0
            topToTop = unset; topMargin = 0
            leftToLeft = parentId; leftMargin = hMargin
            rightToRight = parentId; rightMargin = hMargin
            bottomToBottom = parentId; bottomMargin = vMargin + dp(2)
        }
        applyBottomAltTextPadding()
        altText.gravity = Gravity.CENTER
    }

    private fun applyNoAltTextPosition() {
        img.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topToTop = parentId
            bottomToBottom = parentId
            startToStart = parentId
            endToEnd = parentId
            topMargin = 0
            bottomMargin = 0
            bottomToTop = unset
        }
        altText.visibility = View.GONE
        applyBottomAltTextPadding()
        altText.gravity = Gravity.CENTER
    }

    private fun resolveThemeLayoutMode(): AltTextLayoutMode {
        val pref = ThemeManager.prefs.punctuationPosition.getValue()
        if (pref == PunctuationPosition.None) return AltTextLayoutMode.Hidden
        return when (pref) {
            PunctuationPosition.Top -> AltTextLayoutMode.Top
            PunctuationPosition.TopRight -> AltTextLayoutMode.TopRight
            PunctuationPosition.Bottom -> AltTextLayoutMode.Bottom
            PunctuationPosition.None -> AltTextLayoutMode.Hidden
        }
    }


    private fun resolveLayoutMode(keyHeight: Int): AltTextLayoutMode {
        if (altText.text.isNullOrBlank()) return AltTextLayoutMode.Hidden
        if (ThemeManager.prefs.punctuationPosition.getValue() == PunctuationPosition.None) {
            return AltTextLayoutMode.Hidden
        }
        val preferred = when (def.altTextPositionOverride) {
            KeyDef.Appearance.AltTextPosition.TopBottom -> resolveThemeLayoutMode()
            KeyDef.Appearance.AltTextPosition.Top -> AltTextLayoutMode.Top
            KeyDef.Appearance.AltTextPosition.TopRight -> AltTextLayoutMode.TopRight
            KeyDef.Appearance.AltTextPosition.Bottom -> AltTextLayoutMode.Bottom
            null -> resolveThemeLayoutMode()
        }
        if (keyHeight <= 0) return preferred

        val contentHeight = keyHeight - vMargin * 2
        val iconHeight = img.measuredHeight.takeIf { it > 0 } ?: dp(24)
        val altHeight = altText.paint.run { fontMetrics.bottom - fontMetrics.top }
        val compactMinHeight = max(iconHeight.toFloat(), altHeight + cornerLabelTopSafeInset.toFloat())
        val stackedMinHeight = iconHeight + altHeight + dp(1)

        return when (preferred) {
            AltTextLayoutMode.Bottom -> when {
                contentHeight >= stackedMinHeight -> AltTextLayoutMode.Bottom
                contentHeight >= compactMinHeight -> AltTextLayoutMode.TopRight
                else -> AltTextLayoutMode.Hidden
            }
            AltTextLayoutMode.Top -> when {
                contentHeight >= compactMinHeight -> AltTextLayoutMode.Top
                else -> AltTextLayoutMode.Hidden
            }
            AltTextLayoutMode.TopRight -> when {
                contentHeight >= compactMinHeight -> AltTextLayoutMode.TopRight
                else -> AltTextLayoutMode.Hidden
            }
            AltTextLayoutMode.Hidden -> AltTextLayoutMode.Hidden
        }
    }

    private fun applyLayout(keyHeight: Int = appearanceView.height) {
        val mode = resolveLayoutMode(keyHeight)
        if (mode == lastLayoutMode) return
        lastLayoutMode = mode
        when (mode) {
            AltTextLayoutMode.Bottom -> applyBottomAltTextPosition()
            AltTextLayoutMode.Top -> applyTopAltTextPosition()
            AltTextLayoutMode.TopRight -> applyTopRightAltTextPosition()
            AltTextLayoutMode.Hidden -> applyNoAltTextPosition()
        }
    }

    override fun selectAltTextSwipeTarget(totalY: Int): AltTextSwipeTarget? {
        if (totalY == 0) return null
        return when (lastLayoutMode ?: resolveLayoutMode(appearanceView.height)) {
            AltTextLayoutMode.Bottom -> AltTextSwipeTarget.Primary.takeIf { totalY > 0 }
            AltTextLayoutMode.Top,
            AltTextLayoutMode.TopRight -> AltTextSwipeTarget.Primary.takeIf { totalY < 0 }
            AltTextLayoutMode.Hidden -> null
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        lastLayoutMode = null
        applyLayout()
    }

    override fun onAppearanceLayoutChanged(width: Int, height: Int) {
        applyLayout(height)
    }

    override fun updateTheme(newTheme: Theme) {
        super.updateTheme(newTheme)
        img.imageTintList = ColorStateList.valueOf(
            resolveTextColor(
                when (def.variant) {
                    Variant.Normal -> newTheme.keyTextColor
                    Variant.AltForeground, Variant.Alternative -> newTheme.altKeyTextColor
                    Variant.Accent -> newTheme.accentKeyTextColor
                }
            )
        )
        altText.setTextColor(
            resolveAltTextColor(
                when (def.variant) {
                    Variant.Normal, Variant.AltForeground, Variant.Alternative -> newTheme.altKeyTextColor
                    Variant.Accent -> newTheme.accentKeyTextColor
                }
            )
        )
        lastLayoutMode = null
        applyLayout()
    }
}

@SuppressLint("ViewConstructor")
class ImageKeyView(
    ctx: Context,
    theme: Theme,
    def: KeyDef.Appearance.Image,
    horizontalGapScale: Float = 1f
) :
    KeyView(ctx, theme, def, horizontalGapScale), KeyViewWithImage {
    override val img = imageView { configure(theme, def.src, def.variant, def.viewId) }.apply {
        val defaultColor = when (def.variant) {
            Variant.Normal -> theme.keyTextColor
            Variant.AltForeground, Variant.Alternative -> theme.altKeyTextColor
            Variant.Accent -> theme.accentKeyTextColor
        }
        imageTintList = ColorStateList.valueOf(resolveTextColor(defaultColor))
    }

    init {
        appearanceView.apply {
            add(img, lParams(wrapContent, wrapContent) {
                centerInParent()
            })
        }
    }

    override fun updateTheme(newTheme: Theme) {
        super.updateTheme(newTheme)
        val defaultColor = when (def.variant) {
            Variant.Normal -> newTheme.keyTextColor
            Variant.AltForeground, Variant.Alternative -> newTheme.altKeyTextColor
            Variant.Accent -> newTheme.accentKeyTextColor
        }
        img.imageTintList = ColorStateList.valueOf(resolveTextColor(defaultColor))
    }
}

private fun resolveForegroundColor(theme: Theme, variant: Variant, viewId: Int): Int {
    return when (variant) {
        Variant.Normal -> theme.keyTextColor
        Variant.AltForeground, Variant.Alternative -> theme.altKeyTextColor
        Variant.Accent -> theme.accentKeyTextColor
    }
}

private fun ImageView.configure(
    theme: Theme,
    @DrawableRes src: Int,
    variant: Variant,
    viewId: Int
) = apply {
    isClickable = false
    isFocusable = false
    imageTintList = ColorStateList.valueOf(resolveForegroundColor(theme, variant, viewId))
    imageResource = src
}

@SuppressLint("ViewConstructor")
class ImageTextKeyView(
    ctx: Context,
    theme: Theme,
    def: KeyDef.Appearance.ImageText,
    horizontalGapScale: Float = 1f
) :
    TextKeyView(ctx, theme, def, horizontalGapScale), KeyViewWithImage {
    override val img = imageView {
        configure(theme, def.src, def.variant, def.viewId)
        val defaultColor = when (def.variant) {
            Variant.Normal -> theme.keyTextColor
            Variant.AltForeground, Variant.Alternative -> theme.altKeyTextColor
            Variant.Accent -> theme.accentKeyTextColor
        }
        imageTintList = ColorStateList.valueOf(resolveTextColor(defaultColor))
    }

    init {
        appearanceView.apply {
            add(img, lParams(dp(13), dp(13)))
        }
        mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            centerHorizontally()
            bottomToBottom = parentId
            bottomMargin = vMargin + dp(4)
            topToTop = unset
        }
        img.updateLayoutParams<ConstraintLayout.LayoutParams> {
            centerHorizontally()
            topToTop = parentId
        }
        updateMargins(resources.configuration.orientation)
    }

    private fun updateMargins(orientation: Int) {
        when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    bottomMargin = vMargin + dp(2)
                }
                img.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    topMargin = vMargin + dp(4)
                }
            }

            else -> {
                mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    bottomMargin = vMargin + dp(4)
                }
                img.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    topMargin = vMargin + dp(8)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        updateMargins(newConfig.orientation)
    }

    override fun updateTheme(newTheme: Theme) {
        super.updateTheme(newTheme)
        val defaultColor = when (def.variant) {
            Variant.Normal -> newTheme.keyTextColor
            Variant.AltForeground, Variant.Alternative -> newTheme.altKeyTextColor
            Variant.Accent -> newTheme.accentKeyTextColor
        }
        img.imageTintList = ColorStateList.valueOf(resolveTextColor(defaultColor))
    }
}
