/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fxboomk.fcitx5.android.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionCandidateSnapshotTest {
    private fun page(text: String) = FcitxEvent.PagedCandidateEvent.Data(
        arrayOf(CandidateWord("1", text, "", false)), 0,
        FcitxEvent.PagedCandidateEvent.LayoutHint.NotSet, false, false,
    )

    @Test
    fun repeatedKeysCannotSelectTheSameRenderedSnapshotTwice() {
        val state = PredictionCandidateSnapshot()
        val visible = page("你好")
        state.update(visible)
        assertTrue(state.consume(visible))
        assertFalse(state.consume(visible))
    }

    @Test
    fun oldVisiblePageCannotSelectANewerUnseenPageEvenWithIdenticalWords() {
        val state = PredictionCandidateSnapshot()
        val visible = page("你好")
        state.update(visible)
        val unseen = page("你好")
        state.update(unseen)
        assertFalse(state.consume(visible))
        assertTrue(state.consume(unseen))
    }

    @Test
    fun resetInvalidatesTheVisiblePage() {
        val state = PredictionCandidateSnapshot()
        val visible = page("你好")
        state.update(visible)
        state.update(FcitxEvent.PagedCandidateEvent.Data.Empty)
        assertFalse(state.consume(visible))
        assertFalse(state.consume(FcitxEvent.PagedCandidateEvent.Data.Empty))
    }
}
