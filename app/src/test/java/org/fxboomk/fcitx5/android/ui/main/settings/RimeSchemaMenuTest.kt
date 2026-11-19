/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.ui.main.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class RimeSchemaMenuTest {
    @Test fun initialMenuKeepsSelectedBundledSchemasInOrder() {
        assertEquals(listOf("bundled-b", "bundled-a", "user"),
            visibleSchemaIds(null, listOf("user"), listOf("bundled-a", "bundled-b", "user"),
                listOf("bundled-b", "bundled-a")))
    }

    @Test fun explicitVisibilityDoesNotDiscardHiddenEnabledSchemasOnSave() {
        assertEquals(listOf("user"),
            visibleSchemaIds(listOf("user"), listOf("user"), listOf("bundled", "user"), listOf("bundled")))
        assertEquals(listOf("user", "bundled", "temporarily-unavailable"),
            schemaIdsForSave(listOf("user"), listOf("bundled", "temporarily-unavailable", "user")))
        assertEquals(listOf("user"), schemaIdsForSave(listOf("user"), emptyList()))
    }

    @Test
    fun `new menu shows only user schemas`() {
        assertEquals(
            listOf("user-a", "user-b"),
            visibleSchemaIds(null, listOf("user-a", "user-b"), listOf("bundled", "user-a", "user-b"))
        )
    }

    @Test
    fun `saved order retains added and unchecked schemas while appending new user schemas`() {
        assertEquals(
            listOf("bundled", "user-b", "user-a", "user-new"),
            visibleSchemaIds(
                listOf("bundled", "user-b", "user-a", "removed"),
                listOf("user-a", "user-b", "user-new"),
                listOf("user-a", "user-new", "bundled", "user-b")
            )
        )
    }
}
