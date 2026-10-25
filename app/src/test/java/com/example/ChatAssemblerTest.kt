package com.example

import com.example.data.opencode.ChatAssembler
import com.example.data.opencode.ChatStreamEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAssemblerTest {

  @Test
  fun testConsumeContentStream() {
    val assembler = ChatAssembler()

    val event1 = assembler.consumeSse("message", "{\"type\":\"content\",\"text\":\"Hello \"}")
    assertTrue(event1 is ChatStreamEvent.Content)
    assertEquals("Hello ", (event1 as ChatStreamEvent.Content).text)

    val event2 = assembler.consumeSse("message", "{\"type\":\"content\",\"text\":\"World!\"}")
    assertTrue(event2 is ChatStreamEvent.Content)
    assertEquals("World!", (event2 as ChatStreamEvent.Content).text)

    val (text, tools, status) = assembler.snapshot()
    assertEquals("Hello World!", text)
    assertTrue(tools.isEmpty())
  }

  @Test
  fun testConsumeToolCalls() {
    val assembler = ChatAssembler()

    assembler.consumeSse("message", "{\"type\":\"tool_call\",\"id\":\"call_1\",\"tool\":\"bash\",\"args\":{\"command\":\"ls -la\"}}")
    val (_, toolsRunning, _) = assembler.snapshot()
    assertEquals(1, toolsRunning.size)
    assertEquals("bash", toolsRunning[0].toolName)
    assertEquals("ls -la", toolsRunning[0].inputSnippet)
    assertTrue(toolsRunning[0].isRunning)

    assembler.consumeSse("message", "{\"type\":\"tool_result\",\"id\":\"call_1\",\"tool\":\"bash\",\"result\":\"total 0\"}")
    val (_, toolsFinished, _) = assembler.snapshot()
    assertEquals(1, toolsFinished.size)
    assertEquals("total 0", toolsFinished[0].outputSnippet)
    assertTrue(toolsFinished[0].isSuccess)
    assertTrue(!toolsFinished[0].isRunning)
  }

  @Test
  fun testStatusUpdate() {
    val assembler = ChatAssembler()

    val event = assembler.consumeSse("status", "{\"status\":\"Compiling rootfs...\"}")
    assertNotNull(event)
    assertTrue(event is ChatStreamEvent.Status)
    assertEquals("Compiling rootfs...", (event as ChatStreamEvent.Status).status)

    val (_, _, status) = assembler.snapshot()
    assertEquals("Compiling rootfs...", status)
  }
}
