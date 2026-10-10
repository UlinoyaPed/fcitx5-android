package org.fxboomk.fcitx5.android.ui.main

import org.fxboomk.fcitx5.android.core.data.PluginDescriptor
import org.fxboomk.fcitx5.android.data.isCompatibleUserDataPackage
import org.fxboomk.fcitx5.android.common.clearurls.ClearUrlsPluginRuntime
import org.junit.Assert.*
import org.junit.Test

class PersonalPackageCompatibilityTest {
    @Test fun ownAndUpstreamPluginsKeepStableNames() {
        for (prefix in listOf("org.ulinoyaped.fcitx5.android.plugin.", "org.fxboomk.fcitx5.android.plugin.", "org.fcitx.fcitx5.android.plugin.")) {
            assertEquals("clipboard-filter", PluginDescriptor.normalizePackageName(prefix + "clipboard_filter.debug"))
        }
    }
    @Test fun backupImportAcceptsBothOldAndNewAppsButRejectsUnrelatedNames() {
        for (base in listOf("org.ulinoyaped.fcitx5.android", "org.fxboomk.fcitx5.android", "org.fcitx.fcitx5.android")) {
            for (suffix in listOf("", ".debug", ".fx")) assertEquals(true, isCompatibleUserDataPackage(base + suffix))
        }
        assertEquals(false, isCompatibleUserDataPackage("org.ulinoyaped.fcitx5.androidother"))
        assertEquals(false, isCompatibleUserDataPackage("com.example.other"))
    }
    @Test fun clearUrlsPrefersOwnPackageAndRetainsOlderFallbacks() {
        val own = ClearUrlsPluginRuntime.CURRENT_PACKAGE
        val old = ClearUrlsPluginRuntime.UPSTREAM_PACKAGE
        assertEquals(own, ClearUrlsPluginRuntime.resolvePackageName(setOf(own, old)))
        assertEquals(old, ClearUrlsPluginRuntime.resolvePackageName(setOf(old)))
        assertNull(ClearUrlsPluginRuntime.resolvePackageName(emptySet()))
    }
}
