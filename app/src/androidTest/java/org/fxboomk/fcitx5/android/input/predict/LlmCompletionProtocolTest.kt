package org.fxboomk.fcitx5.android.input.predict

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Use Android's real JSON classes to validate requests without contacting a provider. */
class LlmCompletionProtocolTest {
    @Test fun shortPredictionsUseInsertionJsonAndNeverSeedTypedText() {
        val payload = payload(LlmClient.PredictionRequest(config(), "hel"))
        val messages = payload.getJSONArray("messages")
        assertTrue(messages.getJSONObject(0).getString("content").contains("insert_text"))
        assertEquals("hel", JSONObject(messages.getJSONObject(1).getString("content")).getString("before_cursor"))
        val prefill = messages.getJSONObject(2).getString("content")
        assertFalse(prefill.contains("hel"))
        assertFalse((0 until payload.getJSONArray("stop").length()).any {
            payload.getJSONArray("stop").getString(it) == "<"
        })
    }

    @Test fun thinkingEnabledDoesNotSendAnEmptyAssistantMessage() {
        val payload = payload(LlmClient.PredictionRequest(config(), "hel", enableThinking = true))
        val messages = payload.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertFalse((0 until messages.length()).any { messages.getJSONObject(it).getString("content").isBlank() })
    }

    @Test fun longFormKeepsItsTextPrefillAndPlainTextContract() {
        val payload = payload(LlmClient.PredictionRequest(config(), "Please send", outputMode = LlmOutputMode.LongForm))
        val messages = payload.getJSONArray("messages")
        assertFalse(messages.getJSONObject(0).getString("content").contains("insert_text"))
        assertTrue(messages.getJSONObject(2).getString("content").endsWith("Please send"))
    }

    private fun payload(request: LlmClient.PredictionRequest): JSONObject {
        val plans = LlmClient::class.java.getDeclaredMethod("buildCompletionPlans", LlmClient.PredictionRequest::class.java)
            .apply { isAccessible = true }.invoke(LlmClient(), request) as List<*>
        return plans.map { plan ->
            requireNotNull(plan).javaClass.getDeclaredMethod("getPayload").apply { isAccessible = true }
                .invoke(plan) as JSONObject
        }.first { it.has("messages") && it.has("stop") }
    }

    private fun config() = LlmPrefs.Config(enabled = true, backend = LlmPrefs.Backend.Completion,
        provider = LlmPrefs.Provider.Custom, baseUrl = "http://127.0.0.1:11434/v1", model = "fixture-model",
        apiKey = "", debounceMs = 450, sampleCount = 1, maxContextChars = 64, preferLastCommit = true)
}
