package com.example

import com.example.errors.AppError
import com.example.providers.gemini.GeminiStreamParser
import com.example.providers.model.StreamEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GeminiStreamParserTest {

    private lateinit var parser: GeminiStreamParser

    @Before
    fun setUp() {
        parser = GeminiStreamParser()
    }

    @Test
    fun testParseStandardSseChunks() {
        val sseChunk1 = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Olá \"}]}}]}\n\n"
        val sseChunk2 = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Mundo!\"}]}}],\"usageMetadata\":{\"promptTokenCount\":5,\"candidatesTokenCount\":2,\"totalTokenCount\":7}}\n\n"

        val events1 = parser.processChunk(sseChunk1, "test_1")
        assertEquals(1, events1.size)
        assertTrue(events1[0] is StreamEvent.TextDelta)
        assertEquals("Olá ", (events1[0] as StreamEvent.TextDelta).text)

        val events2 = parser.processChunk(sseChunk2, "test_1")
        assertEquals(2, events2.size)
        assertTrue(events2[0] is StreamEvent.TextDelta)
        assertEquals("Mundo!", (events2[0] as StreamEvent.TextDelta).text)
        assertTrue(events2[1] is StreamEvent.Usage)
        assertEquals(7, (events2[1] as StreamEvent.Usage).totalTokens)
    }

    @Test
    fun testPartialLineAndByteBoundarySplittingAcrossPackets() {
        // Chunk split in the middle of a JSON line
        val part1 = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Texto divi"
        val part2 = "dido\"}]}}]}\n"

        val events1 = parser.processChunk(part1, "test_2")
        assertEquals(0, events1.size) // Line incomplete, nothing emitted yet

        val events2 = parser.processChunk(part2, "test_2")
        assertEquals(1, events2.size)
        assertTrue(events2[0] is StreamEvent.TextDelta)
        assertEquals("Texto dividido", (events2[0] as StreamEvent.TextDelta).text)
    }

    @Test
    fun testMidStreamErrorDetection() {
        val errorChunk = "data: {\"error\":{\"code\":429,\"message\":\"Resource has been exhausted (rate limit)\"}}\n"

        val events = parser.processChunk(errorChunk, "test_3")
        assertEquals(1, events.size)
        assertTrue(events[0] is StreamEvent.Failed)
        val failure = events[0] as StreamEvent.Failed
        assertTrue(failure.error is AppError.InvalidResponse)
        assertTrue(failure.error.technicalCause.contains("429"))
    }

    @Test
    fun testSafetyBlockedFinishReason() {
        val safetyChunk = "data: {\"candidates\":[{\"finishReason\":\"SAFETY\"}]}\n"

        val events = parser.processChunk(safetyChunk, "test_4")
        assertEquals(1, events.size)
        assertTrue(events[0] is StreamEvent.Failed)
        val failure = events[0] as StreamEvent.Failed
        assertTrue(failure.error is AppError.SafetyBlocked)
        assertEquals("ERR_SAFETY_BLOCKED", failure.error.code)
    }

    @Test
    fun testRecitationBlockedFinishReason() {
        val recitationChunk = "data: {\"candidates\":[{\"finishReason\":\"RECITATION\"}]}\n"

        val events = parser.processChunk(recitationChunk, "test_5")
        assertEquals(1, events.size)
        assertTrue(events[0] is StreamEvent.Failed)
        val failure = events[0] as StreamEvent.Failed
        assertTrue(failure.error is AppError.SafetyBlocked)
        assertEquals("ERR_RECITATION_BLOCKED", failure.error.code)
    }

    @Test
    fun testFunctionCallExtraction() {
        val toolChunk = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"buscar_noticias\",\"args\":{\"query\":\"tecnologia\"}}}]}}]}\n"

        val events = parser.processChunk(toolChunk, "test_6")
        assertEquals(1, events.size)
        assertTrue(events[0] is StreamEvent.ToolCall)
        val toolCall = events[0] as StreamEvent.ToolCall
        assertEquals("buscar_noticias", toolCall.toolName)
        assertTrue(toolCall.argumentsJson.contains("tecnologia"))
    }
}
