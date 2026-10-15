package org.fxboomk.fcitx5.android.input.candidates

/** Retained engines and unavailable Pinyin bindings keep their native cursor behavior. */
internal fun shouldSyncCandidateCursorWithHighlight(addon: String, hasVerifiedPhraseBindings: Boolean): Boolean =
    addon != "pinyin" || !hasVerifiedPhraseBindings
