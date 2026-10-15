/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import org.fxboomk.fcitx5.android.core.InputMethodEntry
import org.fxboomk.fcitx5.android.core.KeyStates

/**
 * 「符号划动映射」的一行：以键面原始小写半角标点为键，映射到
 * - [fullWidth]：中文全角状态下划动输出的标点；
 * - [upper]：大写（Caps）状态下划动输出的符号，留空表示未定义。
 */
data class SwipeSymbolEntry(
    val fullWidth: String,
    val upper: String = ""
)

/** key = 键面原始小写半角标点（单字符字符串）。 */
typealias SwipeSymbolMap = Map<String, SwipeSymbolEntry>

// 全角标点中与「半角码位 + 0xFEE0」不一致的特例，和既有行为保持一致。
private val SPECIAL_FULLWIDTH: Map<Char, String> = mapOf(
    '.' to "。",
    '\\' to "、",
    '<' to "《",
    '>' to "》",
    '[' to "【",
    ']' to "】",
    '^' to "……",
    '_' to "——",
    '$' to "￥",
)

private fun defaultFullWidth(c: Char): String =
    SPECIAL_FULLWIDTH[c]
        ?: if (c.isAsciiPunctuation()) (c.code + 0xFEE0).toChar().toString() else c.toString()

/**
 * 内置默认映射：覆盖全部 ASCII 标点，全角列沿用既有转换规则，大写列默认留空
 * （留给用户在「符号划动映射」里填写）。用户未自定义时即以此为准。
 */
val DefaultSwipeSymbolMapping: SwipeSymbolMap =
    ('!'..'~')
        .filter { it.isAsciiPunctuation() }
        .associate { it.toString() to SwipeSymbolEntry(defaultFullWidth(it)) }

internal fun punctuationSwipeAction(
    action: KeyAction,
    strategy: PunctuationSwipeStrategy
): KeyAction {
    if (strategy == PunctuationSwipeStrategy.Default) {
        // Custom layouts use literal commits for their sublabels. Restore the upstream
        // engine key path for single punctuation keys while preserving arbitrary text.
        // Non-punctuation literal commits (e.g. a custom "ü" sublabel) stay commits but
        // are marked swipeSymbol so Caps can still remap them via 符号划动映射 (需求 3/5).
        if (action !is KeyAction.CommitAction) return action
        return when {
            action.text.length == 1 && action.text[0].isAsciiPunctuation() ->
                KeyAction.FcitxKeyAction(action.text)
            action.text.isEmpty() -> action
            else -> action.copy(swipeSymbol = true)
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
    if (text.isEmpty()) return action
    val isPunctuation = text.all { it.isAsciiPunctuation() }
    // A non-punctuation engine key swipe (e.g. a built-in letter alt) stays on the
    // engine path; only literal commits become swipe-mapping symbols.
    if (!isPunctuation && action is KeyAction.FcitxKeyAction) return action
    // Full-width conversion (需求 4) applies to ASCII punctuation only; any swiped
    // symbol is eligible for the mapping's uppercase column under Caps (需求 5).
    return KeyAction.CommitAction(
        text,
        followPunctuationMode = isPunctuation && strategy == PunctuationSwipeStrategy.FollowInputMode,
        swipeSymbol = true,
        followShift = (action as? KeyAction.CommitAction)?.followShift == true
    )
}

/**
 * FollowInputMode（跟随中英状态）在中文状态下的解析：按 [mapping] 的全角列输出，
 * 映射缺失时回退到「半角码位 + 0xFEE0」。英文 / 密码框 / Rime 拉丁模式保持原样。
 */
internal fun resolveSwipePunctuation(
    text: String,
    ime: InputMethodEntry,
    isPassword: Boolean = false,
    mapping: SwipeSymbolMap = DefaultSwipeSymbolMapping
): String {
    if (isPassword || !ime.languageCode.startsWith("zh")) return text
    // Rime keeps its Chinese language code in ASCII mode. Its icon is independent
    // of localized/custom schema labels, and also distinguishes Caps Lock mode.
    if (ime.subMode.icon in setOf("fcitx_rime_latin", "fcitx_rime_latin_upper", "fcitx_rime_disable")) {
        return text
    }
    return buildString {
        text.forEach { char ->
            val entry = mapping[char.toString()]
            append(
                when {
                    entry != null && entry.fullWidth.isNotEmpty() -> entry.fullWidth
                    char.isAsciiPunctuation() -> defaultFullWidth(char)
                    else -> char.toString()
                }
            )
        }
    }
}

/**
 * 大写（Caps）状态下的解析（需求 5）：按 [mapping] 的大写列输出，
 * 未定义大写映射时回退为原始小写半角字符。与中英 / 全角状态无关。
 */
internal fun resolveSwipeUppercase(
    text: String,
    mapping: SwipeSymbolMap = DefaultSwipeSymbolMapping
): String = buildString {
    text.forEach { char ->
        val upper = mapping[char.toString()]?.upper
        append(if (!upper.isNullOrEmpty()) upper else char.toString())
    }
}

private fun Char.isAsciiPunctuation(): Boolean =
    this in '!'..'~' && !isLetterOrDigit()
