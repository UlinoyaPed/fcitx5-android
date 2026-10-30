/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.fxboomk.fcitx5.android.core.CapabilityFlags
import org.fxboomk.fcitx5.android.core.FcitxAPI
import org.fxboomk.fcitx5.android.core.FcitxEvent
import org.fxboomk.fcitx5.android.daemon.FcitxDaemon
import org.junit.Assert.*
import org.junit.Test

/** Real engine events, collected before input in an activated/focused context. */
class FcitxTest {
    private suspend fun withEngine(
        ime: String, block: suspend FcitxAPI.(Channel<FcitxEvent<*>>) -> Unit
    ) = coroutineScope {
        val name = "maintenance-event-fixture"
        val connection = FcitxDaemon.connect(name)
        try {
            withTimeout(60000) {
                connection.runOnReady {
                    val enabled = enabledIme().map { it.uniqueName }.toTypedArray()
                    val active = currentIme().uniqueName
                    val events = Channel<FcitxEvent<*>>(Channel.UNLIMITED)
                    val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                        eventFlow.collect { events.send(it) }
                    }
                    try {
                        setEnabledIme((enabled.toList() + ime).distinct().toTypedArray())
                        activate(12346, "org.ulinoyaped.fcitx5.android.debug")
                        setCapFlags(CapabilityFlags.DefaultFlags)
                        focus(true)
                        activateIme(ime)
                        reset()
                        while (events.tryReceive().isSuccess) { /* discard setup events */ }
                        block(events)
                    } finally {
                        reset()
                        setEnabledIme(enabled)
                        if (active.isNotBlank()) activateIme(active)
                        focus(false)
                        deactivate(12346)
                        collector.cancel()
                        events.close()
                    }
                }
            }
        } finally { FcitxDaemon.disconnect(name) }
    }

    private suspend inline fun <reified T : FcitxEvent<*>> Channel<FcitxEvent<*>>.awaitEvent(
        crossinline accept: (T) -> Boolean = { true }
    ): T = withTimeout(10000) {
        var result: T? = null
        while (result == null) {
            val event = receive()
            if (event is T && accept(event)) result = event
        }
        result
    }

    @Test fun testWbx(): Unit = runBlocking {
        withEngine("wbx") { events ->
            "wqvb".forEach { sendKey(it) }
            assertTrue(getCandidates(0, 10).isNotEmpty())
            select(0)
            assertEquals("你好", events.awaitEvent<FcitxEvent.CommitStringEvent>().data.text)
        }
    }

    @Test fun testPinyin(): Unit = runBlocking {
        withEngine("pinyin") { events ->
            "nihaoshijie".forEach { sendKey(it) }
            val candidates = getCandidates(0, 50)
            val target = candidates.indexOfFirst { it.text == "你好世界" }
            assertTrue("Expected sentence must be selectable: ${candidates.map { it.text }}", target >= 0)
            select(target)
            assertEquals("你好世界", events.awaitEvent<FcitxEvent.CommitStringEvent>().data.text)
        }
    }

    @Test fun testInputPanelStatus(): Unit = runBlocking {
        withEngine("pinyin") { events ->
            assertTrue(isEmpty())
            sendKey('a')
            events.awaitEvent<FcitxEvent.CandidateListEvent> { it.data.candidates.isNotEmpty() }
            assertFalse(isEmpty())
            while (events.tryReceive().isSuccess) { /* only inspect events after reset */ }
            reset()
            events.awaitEvent<FcitxEvent.CandidateListEvent> { it.data.candidates.isEmpty() }
            assertTrue(isEmpty())
        }
    }
}
