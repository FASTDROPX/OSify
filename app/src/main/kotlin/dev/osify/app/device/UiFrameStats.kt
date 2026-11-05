package dev.osify.app.device

/** Bounded real Window FrameMetrics sample, collected off the UI thread. No invented FPS. */
class UiFrameStats {
    private val duration=LongArray(512)
    private var count=0;private var position=0;private var missed=0L;private var observed=0L;private var callbackDrops=0L
    @Synchronized fun record(nanos: Long,deadline: Long,dropped: Int) {
        if(nanos<=0) return
        duration[position]=nanos;position=(position+1)%duration.size;count=minOf(count+1,duration.size)
        if(deadline>0) {observed++;if(nanos>deadline) missed++}
        callbackDrops+=dropped
    }
    @Synchronized fun summary(): String {
        if(count==0) return "UI FrameMetrics: no rendered frame samples yet"
        val sorted=duration.take(count).sorted()
        val p50=sorted[(count-1)/2]/1_000_000.0
        val p95=sorted[((count-1)*.95).toInt()]/1_000_000.0
        return "UI actual render durations: p50=%.2f ms, p95=%.2f ms, samples=%d; deadline misses=%d/%d; metric callbacks dropped=%d (not an FPS benchmark)".format(java.util.Locale.US,p50,p95,count,missed,observed,callbackDrops)
    }
}
