/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.data.pinyin

/**
 * 双拼布局的分组键。
 *
 * Fcitx5 引擎为每个双拼方案（小鹤、微软、自然码……以及用户自定义方案）各自暴露一个
 * 输入法条目（uniqueName 形如 `shuangpin-xiaohe`、`shuangpin-custom`）。在键盘布局管理
 * 列表中它们统一归并到一个父级输入法布局条目 [BASE_LAYOUT_KEY]（展示为“双拼”）之下，各方案
 * 作为其子布局。
 *
 * 子布局在布局文件中仍以各自的输入法 uniqueName 作为顶层键存储（不迁移既有数据），
 * 运行时解析布局时由 [parentBaseLayoutKey] 提供回退到“双拼”父级输入法布局的键名。
 *
 * 自定义方案的 uniqueName 由用户在重命名时填写 id（[isValidCustomSuffix]）后拼成，
 * 默认仍是 `shuangpin-custom`（[DEFAULT_CUSTOM_SUFFIX]）。
 */
object ShuangpinLayoutKeys {

    /** 所有双拼方案共用的父级输入法布局键（同时也是旧版单一条目双拼输入法的 uniqueName）。 */
    const val BASE_LAYOUT_KEY = "shuangpin"

    /** 双拼方案输入法 uniqueName 的公共前缀。 */
    const val SCHEME_PREFIX = "$BASE_LAYOUT_KEY-"

    /** 自定义方案在用户未填写 id 时使用的默认后缀。 */
    const val DEFAULT_CUSTOM_SUFFIX = "custom"

    /**
     * 引擎内置双拼方案的后缀，与 fcitx5-chinese-addons 的 `BuiltinShuangpinEntries` 一致。
     * 自定义方案的 id 不允许与它们重复，否则会与内置方案冲突。
     */
    val BUILTIN_SCHEME_SUFFIXES = setOf(
        "xiaohe", "ms", "ziguang", "gb", "ziranma", "abc",
        "zhongwenzhixing", "pinyinjiajia"
    )

    /** 由方案后缀拼出输入法 uniqueName（如 `custom` → `shuangpin-custom`）。 */
    fun uniqueNameForSuffix(suffix: String): String = "$SCHEME_PREFIX$suffix"

    /** 方案键对应的后缀；非方案键返回 null。 */
    fun suffixOf(uniqueName: String): String? =
        if (isSchemeUniqueName(uniqueName)) uniqueName.removePrefix(SCHEME_PREFIX) else null

    /** 判断是否为某个双拼方案的输入法 uniqueName（如 `shuangpin-xiaohe`）。 */
    fun isSchemeUniqueName(uniqueName: String): Boolean =
        uniqueName.startsWith(SCHEME_PREFIX) && uniqueName.length > SCHEME_PREFIX.length

    /** 是否为引擎内置方案的后缀（大小写不敏感）。 */
    fun isBuiltinSchemeSuffix(suffix: String): Boolean =
        suffix.lowercase() in BUILTIN_SCHEME_SUFFIXES

    /** 是否为“非内置”的方案键，即自定义双拼方案。 */
    fun isCustomSchemeUniqueName(uniqueName: String): Boolean =
        suffixOf(uniqueName)?.let { !isBuiltinSchemeSuffix(it) } ?: false

    /** 自定义方案 id（uniqueName 去掉 `shuangpin-` 前缀后的后缀）的合法性。 */
    fun isValidCustomSuffix(suffix: String): Boolean {
        val trimmed = suffix.trim().lowercase()
        return trimmed.isNotEmpty() &&
            trimmed.length <= 32 &&
            trimmed.all { it in 'a'..'z' || it in '0'..'9' || it == '-' } &&
            !trimmed.startsWith('-') &&
            !trimmed.endsWith('-') &&
            !isBuiltinSchemeSuffix(trimmed)
    }

    /**
     * 布局键对应的双拼父级输入法布局键：方案键返回 [BASE_LAYOUT_KEY]，其余返回 null。
     */
    fun parentBaseLayoutKey(key: String): String? =
        if (isSchemeUniqueName(key)) BASE_LAYOUT_KEY else null
}
