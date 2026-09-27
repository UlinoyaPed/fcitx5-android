/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.input.dialog

import android.content.Context
import android.view.inputmethod.InputMethodSubtype
import org.fxboomk.fcitx5.android.core.Action
import org.fxboomk.fcitx5.android.core.FcitxAPI
import org.fxboomk.fcitx5.android.core.InputMethodEntry
import org.fxboomk.fcitx5.android.input.keyboard.LangSwitchLongPressBehavior
import org.fxboomk.fcitx5.android.utils.inputMethodManager

data class InputMethodData(
    val uniqueName: String,
    val name: String,
    val ime: Boolean,
    val secondaryName: String? = null,
    val imeId: String? = null,
    val subtype: InputMethodSubtype? = null,
    val rimeSchemaActionId: Int? = null,
    val isGroupHeader: Boolean = false
) {
    companion object {
        suspend fun resolve(
            fcitx: FcitxAPI,
            context: Context,
            behavior: LangSwitchLongPressBehavior = LangSwitchLongPressBehavior.Default
        ): List<InputMethodData> {
            val enabled = resolveInternal(fcitx, behavior).toMutableList()
            if (behavior != LangSwitchLongPressBehavior.Default) return enabled

            val imm = context.inputMethodManager
            enabled += imm.enabledInputMethodList
                .filter { it.packageName != context.packageName }
                .flatMap { imi ->
                    val imeLabel = imi.loadLabel(context.packageManager).toString()
                    val subtypes = imm.getEnabledInputMethodSubtypeList(imi, true)
                    if (subtypes.isEmpty()) {
                        listOf(InputMethodData(imi.id, imeLabel, true, imeId = imi.id))
                    } else {
                        subtypes.map { subtype ->
                            val subtypeLabel = subtype.getDisplayName(
                                context,
                                imi.packageName,
                                imi.serviceInfo.applicationInfo
                            ).toString().ifBlank { imeLabel }
                            InputMethodData(
                                uniqueName = "${imi.id}:${subtype.hashCode()}",
                                name = subtypeLabel,
                                ime = true,
                                secondaryName = imeLabel,
                                imeId = imi.id,
                                subtype = subtype
                            )
                        }
                    }
                }
            return enabled.toList()
        }

        internal suspend fun resolveInternal(
            fcitx: FcitxAPI,
            behavior: LangSwitchLongPressBehavior
        ): List<InputMethodData> {
            val enabled = fcitx.enabledIme()
            val rime = if (behavior.includesRimeSchemas) {
                fcitx.availableIme().firstOrNull { it.addon == "rime" }
            } else null
            val schemas = if (rime != null) fcitx.rimeSchemaActions() else emptyArray()
            return internalEntries(enabled, schemas, behavior, rime)
        }

        internal fun internalEntries(
            enabled: Array<InputMethodEntry>,
            schemas: Array<Action>,
            behavior: LangSwitchLongPressBehavior,
            rime: InputMethodEntry? = enabled.firstOrNull { it.addon == "rime" }
        ): List<InputMethodData> = buildList {
            val schemaEntries = if (behavior.includesRimeSchemas && rime != null) {
                schemas.map {
                    InputMethodData(
                        uniqueName = "rime-schema:${it.id}",
                        name = it.shortText,
                        ime = false,
                        rimeSchemaActionId = it.id
                    )
                }
            } else emptyList()
            val enabledRime = enabled.firstOrNull { it.addon == "rime" }
            if (behavior != LangSwitchLongPressBehavior.RimeOnly) {
                enabled.forEach {
                    add(InputMethodData(it.uniqueName, it.displayName, false))
                    if (it == enabledRime) addAll(schemaEntries)
                }
            }
            if (rime != null && schemaEntries.isNotEmpty() &&
                (behavior == LangSwitchLongPressBehavior.RimeOnly || enabledRime == null)) {
                // A grouping row is not an extra selectable engine in schema-only mode.
                add(InputMethodData("rime-schemas", rime.displayName, false, isGroupHeader = true))
                addAll(schemaEntries)
            }
        }
    }
}
