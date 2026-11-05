package dev.osify.core

import kotlinx.coroutines.flow.StateFlow

data class EngineCapabilities(val available: Boolean, val externallyManaged: Boolean, val graphicalDisplay: Boolean, val safeShutdown: Boolean, val reason: String)
data class EngineReport(val state: SessionState, val message: String, val log: String = "")
interface GuestEngine {
    val kind: EngineKind
    val status: StateFlow<EngineReport>
    fun capabilities(): EngineCapabilities
    suspend fun install(): Result<Unit>
    suspend fun launch(): Result<Unit>
    suspend fun stop(force: Boolean = false): Result<Unit>
}
