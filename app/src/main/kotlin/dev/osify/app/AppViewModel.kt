package dev.osify.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.osify.app.data.*
import dev.osify.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class AppViewModel(application: Application): AndroidViewModel(application) {
    val app=application as OsifyApplication
    val dao=app.database.dao()
    val preferences: StateFlow<AppPreferences?> = app.preferences.flow.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    val images=dao.images().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val virtualMachines=dao.virtualMachines().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val runtime=dao.runtime().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    fun prepareRuntime()=run {app.vmEngine.prepare()}
    fun launchVm(vm: VirtualMachine,bootIso: Boolean)=run {app.vmEngine.launch(vm,bootIso)}
    fun resetPendingVm(id: String)=run {app.vmEngine.resetPending(id)}
    fun removeVm(vm: VirtualMachine)=run {app.vmEngine.remove(vm)}
    fun controlVm(vm: VirtualMachine,action: String)=run {app.vmEngine.control(vm,action)}
    fun createVm(image: ImageRecord,name: String,guest: String,ram: Int,cores: Int,disk: Int,firmware: String)=run {app.vmEngine.create(image,name,guest,ram,cores,disk,firmware)}
    val presets=dao.presets().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val device=app.profiler.live.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),app.profiler.snapshot())
    val engine=app.termux.status
    private val repository=ImageRepository(app,dao,app.preferences)
    val catalog=MutableStateFlow<CatalogResult?>(null)
    val catalogBusy=MutableStateFlow(false)
    val message=MutableStateFlow<String?>(null)
    val releaseReport=MutableStateFlow<Pair<Long,Long>?>(null)
    var catalogArchitecture=Architecture.X86_64
        private set
    init { refreshCatalog(false); run { repository.recoverFinishedWork() } }
    fun run(action: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { runCatching { action() }.onFailure { message.value=it.message?:it.javaClass.simpleName } } }
    fun refreshCatalog(refresh: Boolean,arch: Architecture=catalogArchitecture) {
        if(catalogBusy.value) return
        catalogArchitecture=arch;catalogBusy.value=true
        run { try { catalog.value=app.catalog.load(arch,refresh) } finally { catalogBusy.value=false } }
    }
    fun boolean(key: String,value: Boolean)=run { app.preferences.boolean(key,value) }
    fun string(key: String,value: String)=run { app.preferences.string(key,value) }
    fun concurrency(value: Int)=run { app.preferences.concurrency(value) }
    fun resetSettings()=run { app.preferences.reset() }
    fun queue(image: CatalogImage)=run { repository.queue(image) }
    fun toggleFavorite(key: String)=run {app.preferences.favorite(key)}
    fun cloneVm(machine: VirtualMachine)=run {app.vmEngine.clone(machine.id)}
    fun pause(id: String)=run { repository.pause(id) }
    fun resume(id: String)=run { repository.resume(id) }
    fun restart(id: String)=run { repository.restart(id) }
    fun delete(id: String)=run { repository.delete(id) }
    fun importImage(uri: Uri)=run { repository.import(uri) }
    fun savePreset(preset: Preset,id: String=UUID.randomUUID().toString())=run { dao.preset(SavedPreset(id,Presets.encode(preset))) }
    fun importPreset(text: String)=run { dao.preset(SavedPreset(UUID.randomUUID().toString(),Presets.encode(Presets.decode(text)))) }
    fun deletePreset(id: String)=run { dao.deletePreset(id) }
    fun installLinux()=run { app.termux.install().getOrThrow() }
    fun launchLinux()=run { app.termux.launch().getOrThrow() }
    fun resetEngine()=run { app.termux.resetTracking() }
    fun markEngineUnknown()=run { app.termux.markUnknown() }
    fun releaseResources()=run {
        val before=app.profiler.snapshot().profile.availableRam
        // Native engines are unavailable; never stop a process owned by another app.
        // Only OSify's disposable preview cache is eligible for release.
        app.cacheDir.listFiles()?.filter { it.name.startsWith("preview-") }?.forEach { it.deleteRecursively() }
        val after=app.profiler.snapshot().profile.availableRam
        releaseReport.value=before to after
    }
    fun diagnostics(): String = buildString {
        val s=device.value
        appendLine("OSify ${BuildConfig.VERSION_NAME} diagnostics (no device identifiers)")
        appendLine("Android API ${s.profile.api}; ABI ${s.profile.architecture}; cores ${s.profile.cores}")
        appendLine("RAM total=${s.profile.totalRam} available=${s.profile.availableRam}; lowMemory=${s.lowMemory}")
        appendLine("Free app storage=${s.profile.freeDisk}; thermal=${s.thermal}; battery=${s.battery}")
        appendLine(s.graphics)
        appendLine(app.frameStats.summary())
        val display=app.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)
        appendLine("Display mode refresh=${display?.refreshRate} Hz (display mode, not measured application FPS)")
        appendLine("KVM: disabled. Embedded Limbo QEMU 5.1; guest x64, ARM64/x86_64 host APK.")
        appendLine("External session=${engine.value.state}")
        appendLine(engine.value.log)
        images.value.forEach { appendLine("Image ${it.title}: ${it.state}; bytes=${it.bytes}/${it.expectedBytes}; ${it.error}") }
    }
}
