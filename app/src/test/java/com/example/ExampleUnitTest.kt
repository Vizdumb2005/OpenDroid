package com.example

import com.example.data.model.DeviceHardwareInfo
import com.example.data.model.VmConfig
import com.example.data.repository.WorkspaceRepository
import com.example.data.sandbox.HardwareAdvisor
import com.example.data.sandbox.UbuntuSandbox
import com.example.data.skills.GraphifyEngine
import com.example.data.skills.PonytailEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun testWorkspaceInitialization() {
    val repo = WorkspaceRepository()
    val files = repo.files.value
    assertTrue("Initial workspace should contain package.json", files.containsKey("package.json"))
    assertTrue("Initial workspace should contain src/index.ts", files.containsKey("src/index.ts"))
    assertTrue("Initial workspace should contain src/auth.ts", files.containsKey("src/auth.ts"))
    assertNotNull(repo.readFile("src/auth.ts"))
  }

  @Test
  fun testUbuntuSandboxBootAndCommands() {
    val sandbox = UbuntuSandbox(VmConfig(allocatedRamMb = 2048, allocatedDiskGb = 8))
    val files = sandbox.files.value
    assertTrue("Sandbox should have /etc/os-release", files.containsKey("/etc/os-release"))
    assertTrue("Sandbox should have /home/ubuntu/.bashrc", files.containsKey("/home/ubuntu/.bashrc"))
    assertTrue("Sandbox should have /mnt/android_share", files.containsKey("/mnt/android_share"))

    // Test neofetch
    val neofetch = sandbox.executeCommand("neofetch")
    assertTrue(neofetch.output.contains("Ubuntu 24.04 LTS"))
    assertTrue(neofetch.output.contains("2048MiB"))

    // Test free -h
    val free = sandbox.executeCommand("free -h")
    assertTrue(free.output.contains("2048M"))

    // Test apt update
    val apt = sandbox.executeCommand("apt update")
    assertTrue(apt.output.contains("noble InRelease"))

    // Test whoami and sudo
    val whoami = sandbox.executeCommand("whoami")
    assertEquals("ubuntu", whoami.output)

    val sudoWhoami = sandbox.executeCommand("sudo whoami")
    assertEquals("root", sudoWhoami.output)
  }

  @Test
  fun testGraphifySkillAnalysis() {
    val repo = WorkspaceRepository()
    val graph = GraphifyEngine.analyzeProject(repo.files.value)
    assertTrue("Graphify should find multiple nodes", graph.nodes.isNotEmpty())
    assertEquals(0, graph.circularDependencies.size)
    assertTrue(graph.architectureSummary.contains("Graphify"))
  }

  @Test
  fun testPonytailSkillReview() {
    val repo = WorkspaceRepository()
    val report = PonytailEngine.reviewCodebase(repo.files.value)
    assertTrue("Health score should be populated", report.score in 0..100)
    assertTrue("Report summary should mention Ponytail", report.summary.contains("Ponytail"))
  }

  @Test
  fun testHardwareAdvisorRecommendation() {
    val mockInfo = DeviceHardwareInfo(
      totalRamMb = 8192,
      availableRamMb = 4096,
      totalStorageGb = 128,
      availableStorageGb = 32,
      cpuCores = 8,
      architecture = "arm64-v8a"
    )
    val config = HardwareAdvisor.computeOptimalVmConfig(mockInfo)
    assertTrue("Recommended RAM should be positive", config.recommendedRamMb > 0)
    assertTrue("Recommended Disk should be positive", config.recommendedDiskGb > 0)
    assertEquals(config.recommendedRamMb, config.allocatedRamMb)
  }

  @Test
  fun testGitStatusAndCommit() {
    val repo = WorkspaceRepository()
    val statusClean = repo.executeTerminalCommand("git status")
    assertTrue(statusClean.output.contains("working tree clean"))

    repo.writeFile("src/new.ts", "export const x = 1;")
    val statusDirty = repo.executeTerminalCommand("git status")
    assertTrue(statusDirty.output.contains("Changes not staged for commit"))

    val commit = repo.gitCommit("feat: add new file")
    assertNotNull(commit)
    assertEquals("feat: add new file", commit?.message)

    val statusCleanAfter = repo.executeTerminalCommand("git status")
    assertTrue(statusCleanAfter.output.contains("working tree clean"))
  }
}
