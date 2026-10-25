package com.example

import com.example.data.sandbox.Sandbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SandboxConfigTest {

  @Test
  fun testUbuntuRootfsConfig() {
    assertEquals(
      "5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd",
      Sandbox.EXPECTED_SHA256
    )
    assertTrue(Sandbox.UBUNTU_URL.contains("ubuntu-base-26.04.1-base-arm64.tar.gz"))
    assertTrue(Sandbox.UBUNTU_URL.startsWith("https://cdimage.ubuntu.com/"))
  }
}
