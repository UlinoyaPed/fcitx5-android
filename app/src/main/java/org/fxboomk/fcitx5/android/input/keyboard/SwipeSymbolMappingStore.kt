/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.keyboard

import org.fxboomk.fcitx5.android.input.config.ConfigProviders

/**
 * 运行期加载并缓存用户自定义的「符号划动映射」。
 *
 * JSON 文件（config/SwipeSymbolMapping.json）格式：
 * `{ "原始半角标点": ["中文全角", "大写"] }`，缺省的列用空串占位。
 * 文件缺失或为空时退回 [DefaultSwipeSymbolMapping]；缓存随文件变更失效（见
 * [ConfigProviders.addSwipeSymbolMappingListener]），保证编辑后即时生效。
 */
object SwipeSymbolMappingStore {

    @Volatile
    private var cached: SwipeSymbolMap? = null

    @Volatile
    private var listenerRegistered = false

    private fun ensureListener() {
        if (listenerRegistered) return
        synchronized(this) {
            if (listenerRegistered) return
            runCatching {
                ConfigProviders.addSwipeSymbolMappingListener { cached = null }
            }
            listenerRegistered = true
        }
    }

    fun current(): SwipeSymbolMap {
        ensureListener()
        cached?.let { return it }
        val loaded = runCatching {
            ConfigProviders.readSwipeSymbolMapping<Map<String, List<String>>>()?.value
        }.getOrNull()
        val map = loaded
            ?.takeIf { it.isNotEmpty() }
            ?.mapNotNull { (key, cols) ->
                if (key.isEmpty()) return@mapNotNull null
                key to SwipeSymbolEntry(
                    fullWidth = cols.getOrElse(0) { "" },
                    upper = cols.getOrElse(1) { "" }
                )
            }
            ?.toMap()
            ?: DefaultSwipeSymbolMapping
        cached = map
        return map
    }

    fun invalidate() {
        cached = null
    }
}
