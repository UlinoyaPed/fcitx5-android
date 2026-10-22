package org.fxboomk.fcitx5.android.input.predict

import org.json.JSONObject

internal enum class RequestAugmentation {
    None,
    ThinkFalse,
    ThinkTrue,
    ThinkingDisabled,
    ThinkingEnabled,
    ReasoningEffortNone,
    ReasoningEffortLow,
    ReasoningEffortMedium,
    ReasoningEffortHigh,
}

internal data class RequestVariant(
    val baseProtocol: String,
    val protocol: String,
    val augmentation: RequestAugmentation,
)

internal object LlmRequestPolicy {
    fun isAnthropicEndpoint(endpoint: String): Boolean = endpoint.endsWith("/messages")

    fun variants(
        config: LlmPrefs.Config,
        endpoint: String,
        enableThinking: Boolean,
        thinkingEffort: LlmPrefs.ThinkingEffort =
            if (enableThinking) LlmPrefs.ThinkingEffort.High else LlmPrefs.ThinkingEffort.Off,
        isSuppressed: (baseProtocol: String, augmentation: RequestAugmentation) -> Boolean = { _, _ -> false },
    ): List<RequestVariant> {
        val baseProtocol = if (isAnthropicEndpoint(endpoint)) {
            "anthropic_messages"
        } else {
            "openai_chat_completions"
        }
        val augmentation = preferredAugmentation(config, thinkingEffort)
        return buildList {
            if (augmentation != RequestAugmentation.None && !isSuppressed(baseProtocol, augmentation)) {
                add(
                    RequestVariant(
                        baseProtocol = baseProtocol,
                        protocol = when (augmentation) {
                            RequestAugmentation.None -> baseProtocol
                            RequestAugmentation.ThinkFalse -> "${baseProtocol}_think_false"
                            RequestAugmentation.ThinkTrue -> "${baseProtocol}_think_true"
                            RequestAugmentation.ThinkingDisabled -> "${baseProtocol}_thinking_disabled"
                            RequestAugmentation.ThinkingEnabled -> "${baseProtocol}_thinking_enabled"
                            RequestAugmentation.ReasoningEffortNone -> "${baseProtocol}_reasoning_none"
                            RequestAugmentation.ReasoningEffortLow -> "${baseProtocol}_reasoning_low"
                            RequestAugmentation.ReasoningEffortMedium -> "${baseProtocol}_reasoning_medium"
                            RequestAugmentation.ReasoningEffortHigh -> "${baseProtocol}_reasoning_high"
                        },
                        augmentation = augmentation,
                    )
                )
            }
            add(
                RequestVariant(
                    baseProtocol = baseProtocol,
                    protocol = baseProtocol,
                    augmentation = RequestAugmentation.None,
                )
            )
        }
    }

    fun applyAugmentation(
        config: LlmPrefs.Config,
        payload: JSONObject,
        augmentation: RequestAugmentation,
        thinkingBudgetTokens: Int? = null,
        thinkingEffort: LlmPrefs.ThinkingEffort = LlmPrefs.ThinkingEffort.High,
    ): JSONObject {
        val result = JSONObject(payload.toString())
        when (augmentation) {
            RequestAugmentation.None -> Unit
            RequestAugmentation.ThinkFalse -> result.put("think", false)
            RequestAugmentation.ThinkTrue -> result.put("think", true)
            RequestAugmentation.ThinkingDisabled ->
                result.put("thinking", JSONObject().put("type", "disabled"))
            RequestAugmentation.ThinkingEnabled -> {
                if (config.provider == LlmPrefs.Provider.DeepSeek &&
                    config.compatApi == LlmPrefs.CompatApi.OpenAI
                ) {
                    result.put("thinking", JSONObject().put("type", "enabled"))
                    if (!result.has("reasoning_effort")) {
                        result.put("reasoning_effort", thinkingEffort.reasoningEffort)
                    }
                } else {
                    val budget = thinkingBudgetTokens
                        ?: thinkingEffort.budgetTokens.coerceAtLeast(1024)
                    result.put(
                        "thinking",
                        JSONObject()
                            .put("type", "enabled")
                            .put("budget_tokens", budget)
                    )
                }
            }
            RequestAugmentation.ReasoningEffortNone -> result.put("reasoning_effort", "none")
            RequestAugmentation.ReasoningEffortLow -> result.put("reasoning_effort", "low")
            RequestAugmentation.ReasoningEffortMedium -> result.put("reasoning_effort", "medium")
            RequestAugmentation.ReasoningEffortHigh -> result.put("reasoning_effort", "high")
        }
        return result
    }

    fun shouldPersistThinkingDisabledSuppression(
        statusCode: Int,
        responseBody: String,
        augmentation: RequestAugmentation,
    ): Boolean {
        if (augmentation != RequestAugmentation.ThinkingDisabled) return false
        if (statusCode !in listOf(400, 404, 405, 422, 501)) return false
        val body = responseBody.lowercase()
        return "unsupported parameter" in body ||
            "unknown parameter" in body ||
            "invalid value" in body
    }

    private fun preferredAugmentation(
        config: LlmPrefs.Config,
        thinkingEffort: LlmPrefs.ThinkingEffort,
    ): RequestAugmentation {
        val enableThinking = thinkingEffort.isEnabled
        return if (config.compatApi == LlmPrefs.CompatApi.Anthropic) {
            when (config.provider) {
                LlmPrefs.Provider.Anthropic,
                LlmPrefs.Provider.MiniMax,
                -> if (enableThinking) {
                    RequestAugmentation.ThinkingEnabled
                } else {
                    RequestAugmentation.ThinkingDisabled
                }

                else -> RequestAugmentation.None
            }
        } else {
            when (config.provider) {
                LlmPrefs.Provider.Custom -> if (enableThinking) {
                    RequestAugmentation.ThinkTrue
                } else {
                    RequestAugmentation.ThinkFalse
                }
                LlmPrefs.Provider.OpenAI,
                LlmPrefs.Provider.Gemini,
                -> when (thinkingEffort) {
                    LlmPrefs.ThinkingEffort.Off -> RequestAugmentation.ReasoningEffortNone
                    LlmPrefs.ThinkingEffort.Low -> RequestAugmentation.ReasoningEffortLow
                    LlmPrefs.ThinkingEffort.Medium -> RequestAugmentation.ReasoningEffortMedium
                    LlmPrefs.ThinkingEffort.High,
                    LlmPrefs.ThinkingEffort.Max,
                    -> RequestAugmentation.ReasoningEffortHigh
                }

                LlmPrefs.Provider.DeepSeek,
                LlmPrefs.Provider.Zhipu,
                -> if (enableThinking) {
                    RequestAugmentation.ThinkingEnabled
                } else {
                    RequestAugmentation.ThinkingDisabled
                }

                else -> RequestAugmentation.None
            }
        }
    }
}
