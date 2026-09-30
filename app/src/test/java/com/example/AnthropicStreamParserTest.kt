package com.example

import com.example.errors.AppError
import com.example.providers.anthropic.AnthropicStreamParser
import com.example.providers.model.StreamEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AnthropicStreamParserTest {

    private lateinit var parser: AnthropicStreamParser

    @Before
    fun setUp() {
        parser = AnthropicStreamParser()
    }

    @Test
    fun testClaudeTextAndThinkingStreamEvents() {
        val sse = """
            event: message_start
            data: {"type":"message_start","message":{"id":"msg_123","type":"message","role":"assistant","usage":{"input_tokens":25}}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"Pensando no problema..."}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"Resposta final do Claude."}}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":15}}

            event: message_stop
            data: {"type":"message_stop"}
        """.trimIndent() + "\n\n"

        val events = parser.processChunk(sse, "claude_corr_1")
        assertTrue(events.isNotEmpty())

        val thinkingEvent = events.filterIsInstance<StreamEvent.ReasoningDelta>().firstOrNull()
        assertNotNull(thinkingEvent)
        assertEquals("Pensando no problema...", thinkingEvent!!.reasoning)

        val textEvent = events.filterIsInstance<StreamEvent.TextDelta>().firstOrNull()
        assertNotNull(textEvent)
        assertEquals("Resposta final do Claude.", textEvent!!.text)

        val usageEvent = events.filterIsInstance<StreamEvent.Usage>().firstOrNull()
        assertNotNull(usageEvent)
        assertEquals(25, usageEvent!!.promptTokens)
        assertEquals(15, usageEvent.completionTokens)
        assertEquals(40, usageEvent.totalTokens)
    }

    @Test
    fun testClaudeOverloadErrorMapping() {
        val errorSse = """
            event: error
            data: {"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}
        """.trimIndent() + "\n\n"

        val events = parser.processChunk(errorSse, "claude_corr_2")
        assertEquals(1, events.size)
        assertTrue(events[0] is StreamEvent.Failed)
        val failed = events[0] as StreamEvent.Failed
        assertTrue(failed.error is AppError.ProviderUnavailable)
    }

    @Test
    fun testClaudePingKeepAliveIgnored() {
        val pingSse = """
            event: ping
            data: {"type": "ping"}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"OK"}}
        """.trimIndent() + "\n\n"

        val events = parser.processChunk(pingSse, "claude_corr_3")
        assertEquals(1, events.size)
        assertTrue(events[0] is StreamEvent.TextDelta)
        assertEquals("OK", (events[0] as StreamEvent.TextDelta).text)
    }
}
