package dev.osify.app.device

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import dev.osify.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

data class DeviceSnapshot(val profile: DeviceProfile, val used: Long, val lowMemory: Boolean, val battery: Int, val thermal: Int?, val graphics: String, val kvm: String="Not verified")
class DeviceProfiler(private val context: Context) {
    private val manager=context.getSystemService(ActivityManager::class.java)
    val architecture=when { "arm64-v8a" in Build.SUPPORTED_ABIS->Architecture.ARM64; "x86_64" in Build.SUPPORTED_ABIS->Architecture.X86_64; else->Architecture.UNKNOWN }
    fun snapshot(): DeviceSnapshot {
        val memory=ActivityManager.MemoryInfo(); manager.getMemoryInfo(memory)
        val battery=context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val thermal=if(Build.VERSION.SDK_INT>=29) context.getSystemService(PowerManager::class.java).currentThermalStatus else null
        val gl=manager.deviceConfigurationInfo.glEsVersion
        val vulkan=context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        return DeviceSnapshot(DeviceProfile(architecture,Runtime.getRuntime().availableProcessors(),memory.totalMem,memory.availMem,context.filesDir.usableSpace,Build.VERSION.SDK_INT),memory.totalMem-memory.availMem,memory.lowMemory,battery,thermal,"OpenGL ES $gl · Vulkan ${if(vulkan) "advertised" else "not advertised"}")
    }
    val live=flow { while(true) { emit(snapshot()); delay(2500) } }
}
