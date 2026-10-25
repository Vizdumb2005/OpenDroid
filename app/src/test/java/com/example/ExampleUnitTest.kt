package com.example

import com.example.data.ai.OpenCodeModelCatalog
import com.example.data.composio.ComposioManager
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
  fun testUbuntuServerArm64SandboxBootAndCommands() {
    val composio = ComposioManager()
    val sandbox = UbuntuSandbox(VmConfig(allocatedRamMb = 2048, allocatedDiskGb = 8), composioManager = composio)
    val files = sandbox.files.value
    assertTrue("Sandbox should have /etc/os-release", files.containsKey("/etc/os-release"))
    assertTrue("Sandbox should have /home/ubuntu/.bashrc", files.containsKey("/home/ubuntu/.bashrc"))
    assertTrue("Sandbox should have /mnt/android_share", files.containsKey("/mnt/android_share"))
    assertTrue("Sandbox should have /usr/local/bin/opencode", files.containsKey("/usr/local/bin/opencode"))

    // Test neofetch for Ubuntu Server 26.04.1 LTS ARM64
    val neofetch = sandbox.executeCommand("neofetch")
    assertTrue(neofetch.output.contains("Ubuntu 26.04.1 LTS"))
    assertTrue(neofetch.output.contains("aarch64 Server"))
    assertTrue(neofetch.output.contains("2048MiB"))

    // Test uname -a
    val uname = sandbox.executeCommand("uname -a")
    assertTrue(uname.output.contains("aarch64"))
    assertTrue(uname.output.contains("ubuntu-server-arm64"))

    // Test free -h
    val free = sandbox.executeCommand("free -h")
    assertTrue(free.output.contains("2048M"))

    // Test apt update
    val apt = sandbox.executeCommand("apt update")
    assertTrue(apt.output.contains("resolute InRelease"))

    // Test whoami and sudo
    val whoami = sandbox.executeCommand("whoami")
    assertEquals("ubuntu", whoami.output)

    val sudoWhoami = sandbox.executeCommand("sudo whoami")
    assertEquals("root", sudoWhoami.output)
  }

  @Test
  fun testPingCommandExecution() {
    val sandbox = UbuntuSandbox(VmConfig(allocatedRamMb = 2048, allocatedDiskGb = 8))
    val pingEntry = sandbox.executeCommand("ping 8.8.8.8")
    assertFalse(pingEntry.isError)
    assertTrue(pingEntry.output.contains("PING 8.8.8.8"))
    assertTrue(pingEntry.output.contains("icmp_seq="))
    assertTrue(pingEntry.output.contains("ping statistics"))
  }

  @Test
  fun testComposioCliExecution() {
    val composio = ComposioManager()
    val sandbox = UbuntuSandbox(composioManager = composio)
    val statusEntry = sandbox.executeCommand("composio status")
    assertFalse(statusEntry.isError)
    assertTrue(statusEntry.output.contains("Composio Tooling Engine"))

    val appsEntry = sandbox.executeCommand("composio apps")
    assertFalse(appsEntry.isError)
    assertTrue(appsEntry.output.contains("GitHub"))
    assertTrue(appsEntry.output.contains("Linux Shell Tool"))
  }

  @Test
  fun testModelCatalogBigPickleDefault() {
    val catalog = OpenCodeModelCatalog()
    val models = catalog.models.value
    assertTrue("Catalog should contain models", models.isNotEmpty())

    val bigpickle = models.find { it.id == "bigpickle" }
    assertNotNull("BigPickle must exist in catalog", bigpickle)
    assertTrue("BigPickle must be free", bigpickle!!.isFree)
    assertTrue("BigPickle must be default", bigpickle.isDefault)

    // Verify other OpenCode free models from screenshot
    assertTrue("Catalog should contain beam-free", models.any { it.id == "beam-free" })
    assertTrue("Catalog should contain deepseek-v4.1-flash-free", models.any { it.id == "deepseek-v4.1-flash-free" })
    assertTrue("Catalog should contain flint-40b-free", models.any { it.id == "flint-40b-free" })
  }

  @Test
  fun testWorkspaceRunFileInSandbox() {
    val repo = WorkspaceRepository()
    val sandbox = UbuntuSandbox()
    val output = repo.runFileInSandbox("src/index.ts", sandbox)
    assertTrue("Should execute script cleanly", output.contains("Server") || output.contains("Executed"))
  }

  @Test
  fun testOpenCodeDebDownloadAndInstall() {
    val sandbox = UbuntuSandbox(VmConfig(allocatedRamMb = 2048, allocatedDiskGb = 8))

    // 1. Download official .deb package
    val curlEntry = sandbox.executeCommand("curl https://opencode.ai/download/stable/linux-x64-deb")
    assertFalse(curlEntry.isError)
    assertTrue(curlEntry.output.contains("opencode.deb"))
    assertTrue("Package should be saved in /tmp/opencode.deb", sandbox.files.value.containsKey("/tmp/opencode.deb"))

    // 2. Install package via dpkg -i
    val dpkgEntry = sandbox.executeCommand("dpkg -i /tmp/opencode.deb")
    assertFalse(dpkgEntry.isError)
    assertTrue(dpkgEntry.output.contains("OpenCode installed successfully"))

    // 3. Verify opencode binary is runnable
    val opencodeVersion = sandbox.executeCommand("opencode --version")
    assertFalse(opencodeVersion.isError)
    assertTrue(opencodeVersion.output.contains("OpenCode CLI v2.4.0"))
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
