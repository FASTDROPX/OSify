package dev.osify.app.download

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A process-local lease cannot leave stale RUNNING rows blocking all work after process death. */
object DownloadSlots {
    private val mutex=Mutex()
    private var active=0
    suspend fun <T> withPermit(maximum: Int,block: suspend ()->T): T {
        while(true) {
            if(mutex.withLock {if(active<maximum) {active++;true} else false}) break
            delay(300)
        }
        try {return block()} finally {mutex.withLock {active--}}
    }
}
