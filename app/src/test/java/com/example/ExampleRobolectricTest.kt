package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.memory.Mem0Engine
import com.example.data.sandbox.UbuntuSandbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("OpenCode", appName)
  }

  @Test
  fun `test Mem0Engine persistence and semantic search`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val mem0 = Mem0Engine(context)

    // Verify initial memories
    val initial = mem0.memories.value
    assertTrue("Mem0 should have initial bootstrap memories", initial.isNotEmpty())
    assertTrue(initial.any { it.memory.contains("Ubuntu Server 26.04.1 LTS") })

    // Add new memory
    val added = mem0.add("User always uses Jetpack Compose with M3 color schemes", category = "preference")
    assertNotNull(added.id)
    assertEquals("preference", added.category)
    assertTrue(mem0.memories.value.any { it.id == added.id })

    // Test search
    val results = mem0.search("Compose")
    assertTrue(results.isNotEmpty())
    assertTrue(results.first().memory.contains("Compose"))

    // Test formatted context
    val contextText = mem0.getFormattedContextForQuery("How do I structure Compose UI?")
    assertTrue(contextText.contains("Mem0 Long-Term Memory Context"))
    assertTrue(contextText.contains("PREFERENCE"))

    // Test delete
    mem0.delete(added.id)
    assertFalse(mem0.memories.value.any { it.id == added.id })
  }

  @Test
  fun `test UbuntuSandbox with Mem0 integration`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val mem0 = Mem0Engine(context)
    val sandbox = UbuntuSandbox(mem0Engine = mem0)

    // Test mem0 list in terminal
    val listEntry = sandbox.executeCommand("mem0 list")
    assertFalse(listEntry.isError)
    assertTrue(listEntry.output.contains("Mem0 Long-Term Memory Store"))

    // Test mem0 add
    val addEntry = sandbox.executeCommand("mem0 add \"Kotlin 2.0 with Compose Compiler\"")
    assertFalse(addEntry.isError)
    assertTrue(addEntry.output.contains("Successfully stored memory"))

    // Test mem0 search
    val searchEntry = sandbox.executeCommand("mem0 search \"Kotlin\"")
    assertFalse(searchEntry.isError)
    assertTrue(searchEntry.output.contains("Kotlin 2.0"))

    // Test mem0 stats
    val statsEntry = sandbox.executeCommand("mem0 stats")
    assertFalse(statsEntry.isError)
    assertTrue(statsEntry.output.contains("Mem0 Engine"))
  }
}
