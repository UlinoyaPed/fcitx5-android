/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */

package org.fxboomk.fcitx5.android.input.predict

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmPromptTest {

    @Test
    fun translationCorrectionInstructionRejectsSourceEcho() {
        val prompt = LlmPrompt.translationCorrectionInstruction()

        assertTrue(prompt.contains("完全相同"))
        assertTrue(prompt.contains("invalid"))
        assertTrue(prompt.contains("do not copy"))
    }

    @Test
    fun systemPromptReflectsConfiguredCandidateLimit() {
        val prompt = LlmPrompt.systemPrompt(maxPredictionCandidates = 7)

        assertTrue(prompt.contains("insert_text"))
        assertTrue(prompt.contains("最多 7 个不同的候选"))
        assertTrue(prompt.contains("最多一条更长的候选"))
        assertTrue(prompt.contains("不要重复已有标点"))
        assertTrue(prompt.contains("无法自然续写时输出 {\"suggestions\":[]}"))
    }

    @Test
    fun systemPromptUsesEnglishGuidanceForEnglishPrefix() {
        val prompt = LlmPrompt.systemPrompt(
            maxPredictionCandidates = 4,
            beforeCursor = "Let's meet at",
        )

        assertTrue(prompt.contains("IME continuation engine"))
        assertTrue(prompt.contains("one JSON object"))
        assertTrue(prompt.contains("within 30 English characters"))
        assertTrue(prompt.contains("include a space when starting the next word"))
        assertTrue(prompt.contains("at most 4 distinct candidates"))
        assertTrue(prompt.contains("insert_text"))
    }

    @Test
    fun systemPromptIncludesConfiguredPersonaStyleHint() {
        val prompt = LlmPrompt.systemPrompt(
            maxPredictionCandidates = 4,
            beforeCursor = "今晚吃点什么",
            personaPreset = LlmPrefs.PersonaPreset.SocialStar,
        )

        assertTrue(prompt.contains("\"style_guidance\""))
        assertTrue(prompt.contains("情商在线，幽默风趣的社交达人。"))
    }

    @Test
    fun systemPromptSwitchesToQuestionAnswerMode() {
        val prompt = LlmPrompt.systemPrompt(
            maxPredictionCandidates = 5,
            beforeCursor = "今天吃什么？",
            taskMode = LlmTaskMode.QuestionAnswer,
        )

        assertTrue(prompt.contains("输入法应答助手"))
        assertTrue(prompt.contains("<task>\n问答回复候选\n</task>"))
        assertTrue(prompt.contains("只输出 1 条最终回答文本本身"))
        assertTrue(prompt.contains("将 INSTRUCTION 视为完整的问题或请求本身"))
        assertTrue(prompt.contains("不要为了简短而省略关键信息"))
        assertTrue(!prompt.contains("中文续写助手"))
    }

    @Test
    fun systemPromptUsesLongFormAnswerLimitWhenQuestionAnswerAndLongFormEnabled() {
        val prompt = LlmPrompt.systemPrompt(
            maxPredictionCandidates = 5,
            beforeCursor = "帮我回个稍微详细一点的答复",
            outputMode = LlmOutputMode.LongForm,
            taskMode = LlmTaskMode.QuestionAnswer,
        )

        assertTrue(prompt.contains("输入法应答助手"))
        assertTrue(prompt.contains("<task>\n问答短文回复\n</task>"))
        assertTrue(prompt.contains("如果问题需要展开说明"))
        assertTrue(prompt.contains("尽量完整回答"))
        assertTrue(!prompt.contains("中文续写助手"))
    }

    @Test
    fun systemPromptSwitchesToTranslateMode() {
        val prompt = LlmPrompt.systemPrompt(
            maxPredictionCandidates = 5,
            beforeCursor = "今天晚上一起吃饭吗？",
            taskMode = LlmTaskMode.Translate,
        )

        assertTrue(prompt.contains("输入法翻译助手"))
        assertTrue(prompt.contains("<task>\n整段文本翻译\n</task>"))
        assertTrue(prompt.contains("只输出最终译文文本本身"))
        assertTrue(prompt.contains("将 INSTRUCTION 视为完整待翻译文本"))
        assertTrue(prompt.contains("英文译文中的单词之间必须保留正常空格"))
        assertTrue(!prompt.contains("中文续写助手"))
    }

    @Test
    fun completionPromptWithoutRecentBiasBuildsInstructionProtocolPrompt() {
        val prompt = LlmPrompt.completionPrompt(
            beforeCursor = "光标前文本内容",
            recentCommittedText = "最近上屏",
            historyText = "历史内容",
            useRecentCommitBias = false,
            maxPredictionCandidates = 4,
        )

        assertTrue(prompt.startsWith("<|im_start|>system"))
        assertTrue(prompt.contains("\"history\":\"历史内容\""))
        assertTrue(prompt.contains("\"recent_commit\":\"\""))
        assertTrue(prompt.contains("\"before_cursor\":\"光标前文本内容\""))
        assertTrue(prompt.endsWith("</think>"))
    }

    @Test
    fun completionPromptWithRecentBiasIncludesRecentCommitMemory() {
        val prompt = LlmPrompt.completionPrompt(
            beforeCursor = "继续写",
            recentCommittedText = "上一句",
            historyText = "更早内容",
            useRecentCommitBias = true,
            maxPredictionCandidates = 4,
        )

        assertTrue(prompt.contains("\"history\":\"更早内容\""))
        assertTrue(prompt.contains("\"recent_commit\":\"上一句\""))
        assertTrue(prompt.endsWith("</think>"))
    }

    @Test
    fun completionPromptPartsExposeStructuredMessagesForCompatibleChatApis() {
        val system = LlmPrompt.completionSystemPrompt(maxPredictionCandidates = 4)
        val user = LlmPrompt.completionUserPrompt(
            beforeCursor = "今晚一起",
            recentCommittedText = "要不要",
            historyText = "我们在约饭",
            useRecentCommitBias = true,
        )
        val assistant = LlmPrompt.completionAssistantPrefill("今晚一起")

        assertTrue(system.contains("你是输入法续写器"))
        assertTrue(system.contains("insert_text"))
        val data = Json.parseToJsonElement(user).jsonObject
        assertEquals("我们在约饭", data["history"]!!.jsonPrimitive.content)
        assertEquals("要不要", data["recent_commit"]!!.jsonPrimitive.content)
        assertTrue(!user.contains("<persona>"))
        assertEquals("今晚一起", data["before_cursor"]!!.jsonPrimitive.content)
        assertEquals("<think>\n\n</think>\n\n", assistant)
        assertTrue(system.contains("最多 4 个不同的候选"))
    }

    @Test
    fun completionPromptPartsSwitchToEnglishWhenPrefixLooksEnglish() {
        val system = LlmPrompt.completionSystemPrompt(
            maxPredictionCandidates = 3,
            beforeCursor = "Let's grab",
        )
        val user = LlmPrompt.completionUserPrompt(
            beforeCursor = "Let's grab",
            recentCommittedText = "Dinner?",
            historyText = "We are planning tonight",
            useRecentCommitBias = true,
        )

        assertTrue(system.contains("IME continuation engine"))
        assertTrue(system.contains("at most 3 distinct candidates"))
        assertTrue(system.contains("allow at most one longer candidate"))
        assertTrue(system.contains("include a space when starting the next word"))
        assertTrue(!user.contains("<persona>"))
        val data = Json.parseToJsonElement(user).jsonObject
        assertEquals("We are planning tonight", data["history"]!!.jsonPrimitive.content)
        assertEquals("Let's grab", data["before_cursor"]!!.jsonPrimitive.content)
    }

    @Test
    fun completionPromptPartsSwitchToQuestionAnswerMode() {
        val system = LlmPrompt.completionSystemPrompt(
            maxPredictionCandidates = 5,
            beforeCursor = "今天午饭吃什么？",
            taskMode = LlmTaskMode.QuestionAnswer,
        )
        val user = LlmPrompt.completionUserPrompt(
            beforeCursor = "今天午饭吃什么？",
            recentCommittedText = "早上刚开完会",
            historyText = "同事在群里讨论吃饭",
            useRecentCommitBias = true,
            taskMode = LlmTaskMode.QuestionAnswer,
        )
        val assistant = LlmPrompt.completionAssistantPrefill(
            beforeCursor = "今天午饭吃什么？",
            taskMode = LlmTaskMode.QuestionAnswer,
        )

        assertTrue(system.contains("输入法应答助手"))
        assertTrue(system.contains("<task>\n问答回复候选\n</task>"))
        assertTrue(system.contains("将 INSTRUCTION 视为完整的问题或请求本身"))
        assertTrue(!user.contains("<persona>"))
        assertTrue(user.contains("问题或请求"))
        assertTrue(user.contains("不要把它当成待续写前缀"))
        assertTrue(user.contains("不要为了简短而省略关键信息"))
        assertTrue(!user.contains("中文续写助手"))
        assertEquals("<think>\n\n</think>\n\n", assistant)
    }

    @Test
    fun completionPromptPartsUseLongFormLimitsWhenQuestionAnswerAndLongFormEnabled() {
        val system = LlmPrompt.completionSystemPrompt(
            maxPredictionCandidates = 5,
            beforeCursor = "帮我回一句稍微详细一点的话",
            outputMode = LlmOutputMode.LongForm,
            taskMode = LlmTaskMode.QuestionAnswer,
        )
        val user = LlmPrompt.completionUserPrompt(
            beforeCursor = "帮我回一句稍微详细一点的话",
            recentCommittedText = "对方刚发来邀请",
            historyText = "朋友在约周末活动",
            useRecentCommitBias = true,
            outputMode = LlmOutputMode.LongForm,
            taskMode = LlmTaskMode.QuestionAnswer,
        )

        assertTrue(system.contains("输入法应答助手"))
        assertTrue(system.contains("<task>\n问答短文回复\n</task>"))
        assertTrue(system.contains("如果问题需要展开说明"))
        assertTrue(user.contains("更展开回答"))
        assertTrue(!user.contains("中文续写助手"))
    }

    @Test
    fun completionPromptPartsSwitchToTranslateMode() {
        val system = LlmPrompt.completionSystemPrompt(
            maxPredictionCandidates = 5,
            beforeCursor = "Let's meet after lunch.",
            taskMode = LlmTaskMode.Translate,
        )
        val user = LlmPrompt.completionUserPrompt(
            beforeCursor = "Let's meet after lunch.",
            recentCommittedText = "",
            historyText = "",
            useRecentCommitBias = false,
            taskMode = LlmTaskMode.Translate,
        )
        val assistant = LlmPrompt.completionAssistantPrefill(
            beforeCursor = "Let's meet after lunch.",
            taskMode = LlmTaskMode.Translate,
        )

        assertTrue(system.contains("<task>\nfull-text translation\n</task>"))
        assertTrue(system.contains("Treat INSTRUCTION as the complete source text to translate"))
        assertTrue(system.contains("Use [[BR]] instead of actual line breaks."))
        assertTrue(system.contains("Keep the item order fixed as: 释义 -> 音标 -> n. -> v. -> adj. -> adv. -> prep. -> phr."))
        assertTrue(system.contains("never reorder the remaining items"))
        assertTrue(user.contains("First identify the language of the complete input"))
        assertTrue(user.contains("every other language, including Traditional Chinese, Japanese, Korean, English, and other languages"))
        assertTrue(user.contains("literal separator `[[BR]]`"))
        assertTrue(user.contains("Do not use real line breaks"))
        assertTrue(!user.contains("<persona>"))
        assertTrue(!user.contains("English continuation assistant"))
        assertEquals("<think>\n\n</think>\n\n", assistant)
    }

    @Test
    fun completionAssistantPrefillSkipsForcedThinkBlockWhenThinkingEnabled() {
        val assistant = LlmPrompt.completionAssistantPrefill(
            beforeCursor = "今晚一起",
            enableThinking = true,
        )

        assertEquals("", assistant)
        assertEquals("今晚一起", LlmPrompt.completionAssistantPrefill(
            beforeCursor = "今晚一起", enableThinking = true, outputMode = LlmOutputMode.LongForm))
    }

    @Test
    fun localOnDevicePromptIsShorterThanStructuredCompletionPrompt() {
        val compact = LlmPrompt.localOnDevicePrompt(
            beforeCursor = "今天天气真好",
            recentCommittedText = "",
            historyText = "",
            maxPredictionCandidates = 1,
        )
        val structured = LlmPrompt.completionPrompt(
            beforeCursor = "今天天气真好",
            recentCommittedText = "",
            historyText = "",
            useRecentCommitBias = false,
            maxPredictionCandidates = 1,
        )

        assertTrue(compact.length < structured.length)
        assertTrue(compact.contains("中文输入法续写助手"))
        assertTrue(!compact.contains("HISTORY:"))
        assertTrue(!compact.contains("<|im_start|>user"))
        assertTrue(!compact.contains("<think>"))
        assertTrue(!compact.contains("</think>"))
        assertTrue(compact.endsWith("<|im_start|>assistant\n今天天气真好"))
    }

    @Test
    fun localOnDeviceQuestionAnswerPromptAlsoOmitsThinkTags() {
        val prompt = LlmPrompt.localOnDevicePrompt(
            beforeCursor = "今晚吃什么？",
            recentCommittedText = "中午刚吃了面",
            historyText = "同事在讨论晚饭",
            maxPredictionCandidates = 1,
            taskMode = LlmTaskMode.QuestionAnswer,
        )

        assertTrue(!prompt.contains("<think>"))
        assertTrue(!prompt.contains("</think>"))
        assertTrue(!prompt.contains("<|im_start|>assistant"))
        assertTrue(!prompt.contains("<|im_start|>user"))
        assertTrue(!prompt.contains("历史："))
        assertTrue(!prompt.contains("最近上屏："))
        assertTrue(prompt.endsWith("今晚吃什么？"))
    }

    @Test
    fun userPromptUsesTranslatorPersonaAndEnglishSpacingInstructionForChineseTranslateMode() {
        val prompt = LlmPrompt.userPrompt(
            beforeCursor = "今天晚上一起吃饭吗？",
            recentCommittedText = "",
            historyText = "",
            useRecentCommitBias = false,
            taskMode = LlmTaskMode.Translate,
        )

        assertTrue(!prompt.contains("<persona>"))
        assertTrue(prompt.contains("英文单词之间必须保留正常空格"))
        assertTrue(!prompt.contains("中文续写助手"))
    }

    @Test
    fun userPromptAsksModelToDetectLanguageForTraditionalChineseTranslateMode() {
        val prompt = LlmPrompt.userPrompt(
            beforeCursor = "臨時切換",
            recentCommittedText = "",
            historyText = "",
            useRecentCommitBias = false,
            taskMode = LlmTaskMode.Translate,
        )

        assertTrue(prompt.contains("只有确认整段文本是简体中文时才翻译成"))
        assertTrue(prompt.contains("任何非简体中文语言，包括繁体中文、日文、韩文、英文及其他语言"))
        assertTrue(prompt.contains("不要因为包含相似汉字就把繁体中文或日文误判为简体中文"))
        assertTrue(prompt.contains("繁体中文 `臨時切換` 应译为简体中文 `临时切换`"))
        assertTrue(prompt.contains("不得原样返回"))
    }

    @Test
    fun userPromptTargetsSimplifiedChineseForJapaneseTranslateMode() {
        val prompt = LlmPrompt.userPrompt(
            beforeCursor = "今晩ご飯を一緒に食べませんか。",
            recentCommittedText = "",
            historyText = "",
            useRecentCommitBias = false,
            taskMode = LlmTaskMode.Translate,
        )

        assertTrue(prompt.contains("任何非简体中文语言，包括繁体中文、日文、韩文、英文及其他语言"))
        assertTrue(prompt.contains("自然、准确、可直接发送的简体中文"))
    }

    @Test
    fun localOnDevicePromptTargetsSimplifiedChineseForTraditionalChineseTranslateMode() {
        val prompt = LlmPrompt.localOnDevicePrompt(
            beforeCursor = "這是我們的對話紀錄，請確認。",
            recentCommittedText = "",
            historyText = "",
            maxPredictionCandidates = 1,
            taskMode = LlmTaskMode.Translate,
        )

        assertTrue(prompt.contains("请先识别完整输入的语言"))
        assertTrue(prompt.contains("任何非简体中文语言，包括繁体中文、日文、韩文、英文及其他语言"))
        assertTrue(prompt.contains("繁体中文 `臨時切換` 应译为简体中文 `临时切换`"))
    }

    @Test
    fun systemPromptTargetsSimplifiedChineseForTraditionalChineseTranslateMode() {
        val prompt = LlmPrompt.systemPrompt(
            maxPredictionCandidates = 5,
            beforeCursor = "這是我們的對話紀錄，請確認。",
            taskMode = LlmTaskMode.Translate,
        )

        assertTrue(prompt.contains("输入法翻译助手"))
        assertTrue(prompt.contains("先判断 INSTRUCTION 的语言"))
        assertTrue(prompt.contains("任何非简体中文语言，包括繁体中文、日文、韩文、英文及其他语言"))
        assertTrue(prompt.contains("繁体中文 `臨時切換` 的最终译文是简体中文 `临时切换`"))
    }

    @Test
    fun localOnDevicePromptAsksModelToDetectLanguageForChineseTranslateMode() {
        val prompt = LlmPrompt.localOnDevicePrompt(
            beforeCursor = "今天晚上一起吃饭吗？",
            recentCommittedText = "",
            historyText = "",
            maxPredictionCandidates = 1,
            taskMode = LlmTaskMode.Translate,
        )

        assertTrue(prompt.contains("请先识别完整输入的语言"))
        assertTrue(prompt.contains("只有确认整段输入是简体中文时才翻译成自然英文"))
        assertTrue(!prompt.contains("中文续写助手"))
    }

    @Test
    fun localOnDevicePromptAppendsCustomPersonaGuidance() {
        val prompt = LlmPrompt.localOnDevicePrompt(
            beforeCursor = "今天晚上一起吃饭吗",
            recentCommittedText = "",
            historyText = "",
            maxPredictionCandidates = 1,
            personaPreset = LlmPrefs.PersonaPreset.Custom,
            customPersona = "语气温柔一点，像很会照顾人。",
        )

        assertTrue(prompt.contains("额外口吻要求"))
        assertTrue(prompt.contains("语气温柔一点，像很会照顾人。"))
    }

    @Test
    fun systemPromptMergesBuiltInPersonaAndDetailedDescription() {
        val prompt = LlmPrompt.systemPrompt(
            maxPredictionCandidates = 4,
            beforeCursor = "今晚吃点什么",
            personaPreset = LlmPrefs.PersonaPreset.SocialStar,
            customPersona = "说话再俏皮一点，但别太油。",
        )

        assertTrue(prompt.contains("情商在线，幽默风趣的社交达人。"))
        assertTrue(prompt.contains("说话再俏皮一点，但别太油。"))
    }

    @Test
    fun userPromptUsesStructuredContinuationContextForChatBackend() {
        val prompt = LlmPrompt.userPrompt(
            beforeCursor = "今晚一起去",
            recentCommittedText = "上条刚发完",
            historyText = "我们在约饭",
            useRecentCommitBias = true,
        )

        assertTrue(!prompt.contains("<persona>"))
        val data = Json.parseToJsonElement(prompt).jsonObject
        assertEquals("我们在约饭", data["history"]!!.jsonPrimitive.content)
        assertEquals("上条刚发完", data["recent_commit"]!!.jsonPrimitive.content)
        assertEquals("今晚一起去", data["before_cursor"]!!.jsonPrimitive.content)
    }

    @Test
    fun completionSystemPromptMentionsChineseLeadingPunctuationDecision() {
        val system = LlmPrompt.completionSystemPrompt(
            maxPredictionCandidates = 4,
            beforeCursor = "翩若惊鸿",
        )

        assertTrue(system.contains("你是输入法续写器"))
        assertTrue(system.contains("不要重复已有标点"))
        assertTrue(system.contains("自然判断是否需要以中文全角标点开头"))
    }

    @Test
    fun userPromptPreservesChinesePunctuationAsData() {
        val prompt = LlmPrompt.userPrompt(
            beforeCursor = "翩若惊鸿",
            recentCommittedText = "",
            historyText = "洛神赋",
            useRecentCommitBias = false,
        )

        val data = Json.parseToJsonElement(prompt).jsonObject
        assertEquals("翩若惊鸿", data["before_cursor"]!!.jsonPrimitive.content)
        assertEquals("洛神赋", data["history"]!!.jsonPrimitive.content)
    }

    @Test
    fun userPromptPreservesEnglishPrefixAsData() {
        val prompt = LlmPrompt.userPrompt(
            beforeCursor = "Please send me",
            recentCommittedText = "the draft",
            historyText = "Need a quick follow-up",
            useRecentCommitBias = true,
        )

        val data = Json.parseToJsonElement(prompt).jsonObject
        assertEquals("Please send me", data["before_cursor"]!!.jsonPrimitive.content)
        assertEquals("the draft", data["recent_commit"]!!.jsonPrimitive.content)
    }

    @Test
    fun userPromptUsesQuestionAnswerInstructionWhenEnabled() {
        val prompt = LlmPrompt.userPrompt(
            beforeCursor = "帮我回一句礼貌一点的话",
            recentCommittedText = "对方刚发来邀请",
            historyText = "朋友约我周末见面",
            useRecentCommitBias = true,
            taskMode = LlmTaskMode.QuestionAnswer,
        )

        assertTrue(!prompt.contains("<persona>"))
        assertTrue(prompt.contains("问题或请求"))
        assertTrue(prompt.contains("给出一条自然、完整、可直接发送的回答"))
        assertTrue(!prompt.contains("中文续写助手"))
        assertTrue(prompt.endsWith("帮我回一句礼貌一点的话\n</instruction>"))
    }
}
