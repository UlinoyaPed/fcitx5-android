package org.fxboomk.fcitx5.android.input.candidates

import org.junit.Assert.*
import org.junit.Test

class HighlightedCandidatePolicyTest {
    @Test fun configuredPinyinCanUseTheUiHighlight() {
        assertFalse(shouldSyncCandidateCursorWithHighlight("pinyin", true))
    }
    @Test fun unreadFailedOrUnboundPinyinBindingsKeepNativeCursorSynchronization() {
        assertTrue(shouldSyncCandidateCursorWithHighlight("pinyin", false))
    }
    @Test fun retainedTableRimeAndOtherEnginesKeepNativeCursorSynchronization() {
        for (addon in listOf("table", "rime", "keyboard", "", "custom")) {
            for (verified in listOf(true, false)) {
                assertTrue("$addon / $verified", shouldSyncCandidateCursorWithHighlight(addon, verified))
            }
        }
    }
}
