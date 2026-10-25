package com.example.data.sandbox

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.example.data.model.DeviceHardwareInfo
import com.example.data.model.VmConfig
import com.example.data.settings.AppSettings
import java.io.File

/**
 * Sizes and enforces the sandbox's resource budget.
 *
 * There is no hypervisor on stock Android, so the "VM" is a process group. Enforcement is done by
 * [SandboxRuntime]'s watchdog (RSS cap over the whole proot process tree), runtime heap caps
 * (NODE_OPTIONS) and back-off when Android signals memory pressure.
 */
class ResourceAllocator(private val context: Context) {

  fun getDeviceHardwareInfo(): DeviceHardwareInfo = deviceInfo(context)

  fun computeBudget(): VmConfig = effective(context, AppSettings(context))

  companion object {

  fun deviceInfo(context: Context): DeviceHardwareInfo {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val st = StatFs(Environment.getDataDirectory().path)
    return DeviceHardwareInfo(
      totalRamMb = (mi.totalMem / MB).toInt(),
      availableRamMb = (mi.availMem / MB).toInt(),
      totalStorageGb = ((st.blockCountLong * st.blockSizeLong) / GB).toInt(),
      availableStorageGb = ((st.availableBlocksLong * st.blockSizeLong) / GB).toInt(),
      cpuCores = Runtime.getRuntime().availableProcessors(),
      architecture = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
    )
  }

  fun recommend(info: DeviceHardwareInfo, lowRamDevice: Boolean = false): VmConfig {
    val share = if (lowRamDevice) 0.30 else 0.40
    val ram = ((info.totalRamMb * share).toInt() / 256 * 256).coerceIn(768, 6144)
    val disk = (info.availableStorageGb / 2).coerceIn(3, 32)
    return VmConfig(
      allocatedRamMb = ram,
      allocatedDiskGb = disk,
      isCustom = false,
      recommendedRamMb = ram,
      recommendedDiskGb = disk,
    )
  }

  /** The config in force: the stored manual choice, or the live recommendation in auto mode. */
  fun effective(context: Context, settings: AppSettings): VmConfig {
    val info = deviceInfo(context)
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val rec = recommend(info, am.isLowRamDevice)
    return if (settings.autoResources || settings.ramMb == 0) rec
    else rec.copy(allocatedRamMb = settings.ramMb, allocatedDiskGb = settings.diskGb.coerceAtLeast(2), isCustom = true)
  }

  /** On-disk size of the guest filesystem in bytes. */
  fun rootfsBytes(rootfs: File): Long =
    if (!rootfs.exists()) 0L else rootfs.walkTopDown().filter { it.isFile }.sumOf { it.length() }

  private const val MB = 1024L * 1024
  private const val GB = 1024L * 1024 * 1024
}
}
