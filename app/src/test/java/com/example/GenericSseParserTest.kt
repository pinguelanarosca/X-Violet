package com.example

import com.example.errors.AppError
import com.example.providers.common.GenericSseParser
import com.example.providers.model.StreamEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GenericSseParserTest {

    private lateinit var parser: GenericSseParser

    @Before
    fun setUp() {
        parser = GenericSseParser("OpenAI")
    }

    @Test
    fun testOpenAiStandardTextChunksAndUsage() {
        val chunk1 = "data: {\"choices\":[{\"delta\":{\"content\":\"Kotlin \"}}]}\n\n"
        val chunk2 = "data: {\"choices\":[{\"delta\":{\"content\":\"Compose\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2,\"total_tokens\":12}}\n\n"
        val chunk3 = "data: [DONE]\n\n"

        val events1 = parser.processChunk(chunk1, "corr_1")
        assertEquals(1, events1.size)
        assertTrue(events1[0] is StreamEvent.TextDelta)
        assertEquals("Kotlin ", (events1[0] as StreamEvent.TextDelta).text)

        val events2 = parser.processChunk(chunk2, "corr_1")
        assertEquals(2, events2.size)
        assertTrue(events2[0] is StreamEvent.TextDelta)
        assertEquals("Compose", (events2[0] as StreamEvent.TextDelta).text)
        assertTrue(events2[1] is StreamEvent.Usage)
        assertEquals(12, (events2[1] as StreamEvent.Usage).totalTokens)

        val events3 = parser.processChunk(chunk3, "corr_1")
        assertEquals(0, events3.size)
    }

    @Test
    fun testDeepSeekOrO1ReasoningTokens() {
        val chunk = "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"Analisando requisitos...\"}}]}\n\n"
        val events = parser.processChunk(chunk, "corr_2")
        assertEquals(1, events.size)
        assertTrue(events[0] is StreamEvent.ReasoningDelta)
        assertEquals("Analisando requisitos...", (events[0] as StreamEvent.ReasoningDelta).reasoning)
    }

    @Test
    fun testToolCallsDelta() {
        val chunk = "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"id\":\"call_abc\",\"function\":{\"name\":\"consultar_cotacao\",\"arguments\":\"{\\\"moeda\\\":\\\"USD\\\"}\"}}]}}]}\n\n"
        val events = parser.processChunk(chunk, "corr_3")
        assertEquals(1, events.size)
        assertTrue(events[0] is StreamEvent.ToolCall)
        val call = events[0] as StreamEvent.ToolCall
        assertEquals("consultar_cotacao", call.toolName)
        assertTrue(call.argumentsJson.contains("USD"))
    }

    @Test
    fun testCommentsAndKeepAliveIgnored() {
        val chunk = ": keep-alive\n: ping\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"Ok\"}}]}\n\n"
        val events = parser.processChunk(chunk, "corr_4")
        assertEquals(1, events.size)
        assertTrue(events[0] is StreamEvent.TextDelta)
        assertEquals("Ok", (events[0] as StreamEvent.TextDelta).text)
    }

    @Test
    fun testMidStreamErrorDetection() {
        val errorChunk = "data: {\"error\":{\"message\":\"Rate limit reached\",\"type\":\"rate_limit_error\",\"code\":429}}\n\n"
        val events = parser.processChunk(errorChunk, "corr_5")
        assertEquals(1, events.size)
        assertTrue(events[0] is StreamEvent.Failed)
        val failed = events[0] as StreamEvent.Failed
        assertTrue(failed.error is AppError.InvalidResponse)
        assertTrue(failed.error.technicalCause.contains("429"))
    }

    @Test
    fun testPacketFragmentationAndUtf8Split() {
        val part1 = "data: {\"choices\":[{\"delta\":{\"content\":\"Texto par"
        val part2 = "cial fragmentado\"}}]}\n\n"

        val events1 = parser.processChunk(part1, "corr_6")
        assertEquals(0, events1.size)

        val events2 = parser.processChunk(part2, "corr_6")
        assertEquals(1, events2.size)
        assertTrue(events2[0] is StreamEvent.TextDelta)
        assertEquals("Texto parcial fragmentado", (events2[0] as StreamEvent.TextDelta).text)
    }
}
