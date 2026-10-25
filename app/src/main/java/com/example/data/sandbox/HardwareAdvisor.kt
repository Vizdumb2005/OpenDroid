package com.example.data.sandbox

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.example.data.model.DeviceHardwareInfo
import com.example.data.model.VmConfig

object HardwareAdvisor {

  fun getDeviceHardwareInfo(context: Context): DeviceHardwareInfo {
    val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    actManager.getMemoryInfo(memInfo)

    val totalRamMb = (memInfo.totalMem / (1024 * 1024)).toInt()
    val availRamMb = (memInfo.availMem / (1024 * 1024)).toInt()

    val statFs = StatFs(Environment.getDataDirectory().path)
    val totalStorageGb = ((statFs.blockCountLong * statFs.blockSizeLong) / (1024L * 1024 * 1024)).toInt()
    val availStorageGb = ((statFs.availableBlocksLong * statFs.blockSizeLong) / (1024L * 1024 * 1024)).toInt()

    val cpuCores = Runtime.getRuntime().availableProcessors()
    val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    return DeviceHardwareInfo(
      totalRamMb = totalRamMb,
      availableRamMb = availRamMb,
      totalStorageGb = totalStorageGb,
      availableStorageGb = availStorageGb,
      cpuCores = cpuCores,
      architecture = arch
    )
  }

  fun computeOptimalVmConfig(info: DeviceHardwareInfo): VmConfig {
    // Recommend safe RAM without starving Android OS
    val recommendedRam = when {
      info.totalRamMb >= 11000 -> 4096 // 12GB+ RAM
      info.totalRamMb >= 7000 -> 3072  // 8GB RAM
      info.totalRamMb >= 5000 -> 2048  // 6GB RAM
      info.totalRamMb >= 3500 -> 1536  // 4GB RAM
      else -> 1024                     // <=3GB RAM
    }

    // Recommend safe Disk storage
    val recommendedDisk = when {
      info.availableStorageGb >= 64 -> 16
      info.availableStorageGb >= 32 -> 12
      info.availableStorageGb >= 16 -> 8
      info.availableStorageGb >= 8 -> 4
      else -> 2
    }

    return VmConfig(
      allocatedRamMb = recommendedRam,
      allocatedDiskGb = recommendedDisk,
      isCustom = false,
      recommendedRamMb = recommendedRam,
      recommendedDiskGb = recommendedDisk
    )
  }
}
