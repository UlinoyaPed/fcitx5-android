/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.ui.main.settings.behavior

import org.fxboomk.fcitx5.android.core.Action
import org.fxboomk.fcitx5.android.core.InputMethodEntry

/** Active identity is independent of whether its base or schema layout is customized. */
internal fun formatActiveLayoutChain(
    profileName: String,
    ime: InputMethodEntry?,
    statusActions: Array<Action>
): String? {
    ime ?: return null
    val chain = "$profileName-${ime.displayName}"
    if (ime.addon != "rime") return chain
    // subMode.label can be a single character; in ASCII mode even subMode.name is
    // a mode indicator, not a schema. The Rime IM action retains the full schema name.
    val schema = statusActions.firstOrNull { it.name == "fcitx-rime-im" }
        ?.longText?.trim().orEmpty()
    return if (schema.isEmpty()) chain else "$chain-$schema"
}
