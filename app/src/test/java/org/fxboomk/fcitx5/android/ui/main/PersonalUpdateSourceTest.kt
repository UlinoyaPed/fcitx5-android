package org.fxboomk.fcitx5.android.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalUpdateSourceTest {
    @Test fun stableAndNightlyHaveSeparateFeedsInThePersonalRepository() {
        assertEquals("https://api.github.com/repos/UlinoyaPed/fcitx5-android/releases/latest", AppUpdateManager.releaseApi(false))
        assertEquals("https://api.github.com/repos/UlinoyaPed/fcitx5-android/releases/tags/latest", AppUpdateManager.releaseApi(true))
    }
}
