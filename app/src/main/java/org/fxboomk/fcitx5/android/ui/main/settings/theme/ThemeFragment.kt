/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings.theme

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.Keep
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import org.fxboomk.fcitx5.android.R
import org.fxboomk.fcitx5.android.data.theme.Theme
import org.fxboomk.fcitx5.android.data.theme.ThemeManager
import org.fxboomk.fcitx5.android.data.theme.ThemePrefs
import org.fxboomk.fcitx5.android.ui.main.MainViewModel
import org.fxboomk.fcitx5.android.utils.alpha
import org.fxboomk.fcitx5.android.utils.isDarkMode
import splitties.dimensions.dp
import splitties.resources.styledColor
import splitties.resources.styledDrawable
import splitties.views.backgroundColor
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.imageResource
import splitties.views.setPaddingDp

class ThemeFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels()

    companion object {
        // Visual scale applied to the keyboard preview.
        private const val PREVIEW_SCALE = 0.68f
    }

    private lateinit var previewUi: KeyboardPreviewUi

    private lateinit var tabLayout: TabLayout

    private lateinit var viewPager: ViewPager2

    private lateinit var prevThemeButton: ImageView
    private lateinit var nextThemeButton: ImageView
    private lateinit var carouselFooter: LinearLayout
    private lateinit var footerThemeName: TextView
    private lateinit var footerInUseBadge: TextView

    // Themes of the active pool shown by the preview carousel; empty when the
    // pool has fewer than two themes and the carousel is disabled.
    private var carouselThemes: List<Theme> = emptyList()
    private var displayedThemeName: String? = null

    // Pool membership toggles write the shared preferences directly (bypassing
    // ManagedPreference.setValue), so listen at the SharedPreferences level.
    // Theme, index and follow-system changes all arrive via OnThemeChangeListener.
    private val carouselPrefKeys = setOf(
        ThemeManager.prefs.lightModeThemes.key,
        ThemeManager.prefs.darkModeThemes.key
    )

    @Keep
    private val onThemeChangeListener = ThemeManager.OnThemeChangeListener {
        lifecycleScope.launch {
            refreshPreview()
        }
    }

    @Keep
    private val onThemeListChangeListener = ThemeManager.OnThemeListChangeListener {
        lifecycleScope.launch {
            refreshPreview()
        }
    }

    private val onCarouselPrefsChange = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in carouselPrefKeys) {
            lifecycleScope.launch {
                refreshPreview()
            }
        }
    }

    /**
     * The pool whose themes the preview carousel cycles through.
     *
     * When following the system day/night mode, the pool matching the current
     * system mode is used. In manual mode, the pool containing the active theme
     * is used; an active theme that belongs to no pool disables the carousel.
     */
    private fun resolveCarouselPool(): List<Theme> {
        val prefs = ThemeManager.prefs
        if (prefs.followSystemDayNightTheme.getValue()) {
            return if (resources.configuration.isDarkMode()) {
                prefs.darkModeThemes.getThemes()
            } else {
                prefs.lightModeThemes.getThemes()
            }
        }
        val active = ThemeManager.activeTheme
        // A theme can only ever join the pool matching its day/night variant.
        val pool = when {
            prefs.darkModeThemes.isSelected(active.name) -> prefs.darkModeThemes
            prefs.lightModeThemes.isSelected(active.name) -> prefs.lightModeThemes
            else -> null
        }
        return pool?.getThemes() ?: emptyList()
    }

    private fun refreshPreview() {
        val active = ThemeManager.activeTheme
        val pool = resolveCarouselPool()
        carouselThemes = if (pool.size >= 2) pool else emptyList()
        val carousel = carouselThemes.isNotEmpty()
        prevThemeButton.isVisible = carousel
        nextThemeButton.isVisible = carousel
        carouselFooter.isVisible = carousel
        if (!carousel) {
            displayedThemeName = active.name
            previewUi.setTheme(active)
            return
        }
        // Keep showing the browsed theme if it is still in the pool; otherwise
        // fall back to the active theme's position, or the head of the pool.
        val index = carouselThemes.indexOfFirst { it.name == displayedThemeName }
            .takeIf { it >= 0 }
            ?: carouselThemes.indexOfFirst { it.name == active.name }.takeIf { it >= 0 }
            ?: 0
        showPoolTheme(carouselThemes[index])
    }

    private fun showPoolTheme(theme: Theme) {
        displayedThemeName = theme.name
        previewUi.setTheme(theme)
        footerThemeName.text = theme.name
        footerInUseBadge.isVisible = theme.name == ThemeManager.activeTheme.name
    }

    private fun stepCarousel(step: Int) {
        if (carouselThemes.size < 2) return
        val current = carouselThemes.indexOfFirst { it.name == displayedThemeName }.coerceAtLeast(0)
        showPoolTheme(carouselThemes[(current + step).mod(carouselThemes.size)])
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = with(requireContext()) {
        previewUi = KeyboardPreviewUi(this, ThemeManager.activeTheme, cropBottomBlank = true)
        ThemeManager.addOnChangedListener(onThemeChangeListener)
        val preview = previewUi.root.apply {
            id = View.generateViewId()
            // Scale from the top edge (matching the theme editors) so the
            // toolbar stays fully visible at every preview height.
            pivotY = 0f
            scaleX = PREVIEW_SCALE
            scaleY = PREVIEW_SCALE
            outlineProvider = ViewOutlineProvider.BOUNDS
            elevation = dp(4f)
            tag = "theme_preview_capture"
        }

        // The preview wrapper background resolves to a neutral theme surface,
        // so tint the chrome with the adaptive primary text color instead of
        // assuming a dark-on-color primary background.
        val chromeTint = styledColor(android.R.attr.textColorPrimary)
        prevThemeButton = imageView {
            imageResource = R.drawable.ic_baseline_keyboard_arrow_left_24
            contentDescription = getString(R.string.theme_preview_previous)
            background = styledDrawable(android.R.attr.selectableItemBackgroundBorderless)
            imageTintList = ColorStateList.valueOf(chromeTint)
            scaleType = ImageView.ScaleType.CENTER
            setOnClickListener { stepCarousel(-1) }
        }
        nextThemeButton = imageView {
            imageResource = R.drawable.ic_baseline_keyboard_arrow_right_24
            contentDescription = getString(R.string.theme_preview_next)
            background = styledDrawable(android.R.attr.selectableItemBackgroundBorderless)
            imageTintList = ColorStateList.valueOf(chromeTint)
            scaleType = ImageView.ScaleType.CENTER
            setOnClickListener { stepCarousel(1) }
        }

        footerThemeName = textView {
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(180)
            setTextColor(chromeTint)
        }
        footerInUseBadge = textView {
            textSize = 12f
            text = getString(R.string.theme_preview_in_use)
            visibility = View.GONE
            setTextColor(chromeTint)
            background = GradientDrawable().apply {
                cornerRadius = dp(9f)
                setColor(chromeTint.alpha(0.25f))
            }
            setPaddingDp(8, 2, 8, 2)
        }
        carouselFooter = horizontalLayout {
            gravity = Gravity.CENTER_VERTICAL
            add(footerThemeName, lParams(wrapContent, wrapContent))
            add(footerInUseBadge, lParams(wrapContent, wrapContent) {
                marginStart = dp(8)
            })
        }

        tabLayout = TabLayout(this)

        viewPager = ViewPager2(this).apply {
            adapter = object : FragmentStateAdapter(this@ThemeFragment) {
                override fun getItemCount() = 2
                override fun createFragment(position: Int): Fragment = when (position) {
                    0 -> ThemeListFragment()
                    else -> ThemeSettingsFragment()
                }
            }
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = getString(
                when (position) {
                    0 -> R.string.theme
                    else -> R.string.configure
                }
            )
        }.attach()

        val pendingPreferenceKey = viewModel.peekPendingPreferenceScrollKey()
        val themePreferenceKeys = ThemeManager.prefs.managedPreferencesUi.map { it.key } +
            ThemePrefs.CandidateAppearancePreferenceKeys
        if (pendingPreferenceKey in themePreferenceKeys) {
            viewPager.setCurrentItem(1, false)
        }

        // The preview renders at PREVIEW_SCALE from its top edge (pivotY = 0),
        // so its layout bounds extend past the visible bitmap by (1 - scale) of
        // the height at the bottom. Pull the footer up to sit right under the
        // visible bitmap, and center the arrows on the visible keyboard.
        preview.addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
            // Keep horizontal scaling centered around the actual view width.
            view.pivotX = (right - left) / 2f
            val overflow = ((bottom - top) * (1f - PREVIEW_SCALE)).toInt()
            if (overflow <= 0) return@addOnLayoutChangeListener
            val footerParams = carouselFooter.layoutParams as? ConstraintLayout.LayoutParams
            if (footerParams != null) {
                val topMargin = -(overflow - dp(6))
                if (footerParams.topMargin != topMargin) {
                    footerParams.topMargin = topMargin
                    carouselFooter.layoutParams = footerParams
                }
            }
            listOf(prevThemeButton, nextThemeButton).forEach { button ->
                val params = button.layoutParams as? ConstraintLayout.LayoutParams
                if (params != null && params.bottomMargin != overflow) {
                    params.bottomMargin = overflow
                    button.layoutParams = params
                }
            }
        }

        val previewWrapper = constraintLayout {
            add(preview, lParams(wrapContent, wrapContent) {
                topOfParent()
                startOfParent()
                endOfParent()
            })
            add(prevThemeButton, lParams(dp(40), dp(40)) {
                // Vertically centered on the visible preview area: the layout
                // change listener trims the offscreen scaled overflow below.
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = preview.id
                startOfParent(dp(2))
            })
            add(nextThemeButton, lParams(dp(40), dp(40)) {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = preview.id
                endOfParent(dp(2))
            })
            add(carouselFooter, lParams(wrapContent, wrapContent) {
                below(preview)
                centerHorizontally()
            })
            add(tabLayout, lParams(matchParent, wrapContent) {
                below(carouselFooter, dp(4))
            })
            backgroundColor = styledColor(android.R.attr.colorPrimary)
            elevation = dp(4f)
        }

        refreshPreview()

        constraintLayout {
            add(previewWrapper, lParams(height = wrapContent) {
                topOfParent()
                startOfParent()
                endOfParent()
            })
            add(viewPager, lParams {
                below(previewWrapper)
                startOfParent()
                endOfParent()
                bottomOfParent()
            })
        }
    }

    override fun onStart() {
        super.onStart()
        ThemeManager.prefs.lightModeThemes.sharedPreferences
            .registerOnSharedPreferenceChangeListener(onCarouselPrefsChange)
        ThemeManager.addOnThemeListChangedListener(onThemeListChangeListener)
    }

    override fun onStop() {
        ThemeManager.prefs.lightModeThemes.sharedPreferences
            .unregisterOnSharedPreferenceChangeListener(onCarouselPrefsChange)
        ThemeManager.removeOnThemeListChangedListener(onThemeListChangeListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ThemeManager.syncToDeviceEncryptedStorage()
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshPreview()
    }

    override fun onDestroy() {
        ThemeManager.removeOnChangedListener(onThemeChangeListener)
        super.onDestroy()
    }

}
