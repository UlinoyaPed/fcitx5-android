/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import org.fxboomk.fcitx5.android.core.InputMethodEntry
import org.fxboomk.fcitx5.android.core.KeyStates

internal fun punctuationSwipeAction(
    action: KeyAction,
    strategy: PunctuationSwipeStrategy
): KeyAction {
    if (strategy == PunctuationSwipeStrategy.Default) {
        // Custom layouts use literal commits for their sublabels. Restore the upstream
        // engine key path for single punctuation keys while preserving arbitrary text.
        return if (action is KeyAction.CommitAction &&
            action.text.length == 1 && action.text[0].isAsciiPunctuation()
        ) {
            KeyAction.FcitxKeyAction(action.text)
        } else {
            action
        }
    }
    val text = when (action) {
        is KeyAction.CommitAction -> action.text
        is KeyAction.FcitxKeyAction -> {
            if (action.up || action.states != KeyStates.Virtual) return action
            action.act
        }
        else -> return action
    }
    // Labels may also contain uppercase letters, digits, emoji or arbitrary text.
    if (text.isEmpty() || !text.all { it.isAsciiPunctuation() }) return action
    return KeyAction.CommitAction(
        text,
        followPunctuationMode = strategy == PunctuationSwipeStrategy.FollowInputMode
    )
}

internal fun resolveSwipePunctuation(
    text: String,
    ime: InputMethodEntry,
    isPassword: Boolean = false
): String {
    if (isPassword || !ime.languageCode.startsWith("zh")) return text
    // Rime keeps its Chinese language code in ASCII mode. Its icon is independent
    // of localized/custom schema labels, and also distinguishes Caps Lock mode.
    if (ime.subMode.icon in setOf("fcitx_rime_latin", "fcitx_rime_latin_upper", "fcitx_rime_disable")) {
        return text
    }
    return buildString {
        text.forEach { char ->
            append(when (char) {
                '.' -> "。"
                '\\' -> "、"
                '<' -> "《"
                '>' -> "》"
                '[' -> "【"
                ']' -> "】"
                '^' -> "……"
                '_' -> "——"
                '$' -> "￥"
                else -> if (char.isAsciiPunctuation()) (char.code + 0xFEE0).toChar().toString() else char.toString()
            })
        }
    }
}

private fun Char.isAsciiPunctuation(): Boolean =
    this in '!'..'~' && !isLetterOrDigit()
