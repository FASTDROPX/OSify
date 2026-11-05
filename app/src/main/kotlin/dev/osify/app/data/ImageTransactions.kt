package dev.osify.app.data

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/** Serializes file mutation with a worker still draining a paused socket. */
object ImageTransactions {
    private val locks=ConcurrentHashMap<String,Mutex>()
    fun mutex(id: String): Mutex=locks.getOrPut(id) { Mutex() }
}
