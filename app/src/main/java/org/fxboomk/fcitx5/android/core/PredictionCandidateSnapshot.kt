/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fxboomk.fcitx5.android.core

/** Used on the fcitx thread: a rendered page can authorize at most one prediction commit. */
internal class PredictionCandidateSnapshot {
    private var current: FcitxEvent.PagedCandidateEvent.Data? = null

    fun update(data: FcitxEvent.PagedCandidateEvent.Data) {
        current = data
    }

    fun consume(expected: FcitxEvent.PagedCandidateEvent.Data): Boolean {
        if (current !== expected || expected.candidates.isEmpty()) return false
        current = null
        return true
    }
}
