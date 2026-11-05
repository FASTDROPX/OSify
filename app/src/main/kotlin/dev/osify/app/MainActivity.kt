package dev.osify.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import dev.osify.app.ui.OsifyApp

class MainActivity: ComponentActivity() {
    private var metricsThread: android.os.HandlerThread?=null
    private var metricsListener: android.view.Window.OnFrameMetricsAvailableListener?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideNavigation()
        preferSmoothDisplay()

        val parent=window.decorView.createLifecycleAwareWindowRecomposer((application as OsifyApplication).motionScale,lifecycle)
        setContent(parent=parent) { OsifyApp(viewModel()) }
        val worker=android.os.HandlerThread("osify-frame-metrics",android.os.Process.THREAD_PRIORITY_BACKGROUND).apply {start()}
        metricsThread=worker
        metricsListener=android.view.Window.OnFrameMetricsAvailableListener {_,metrics,dropped->
            if(metrics.getMetric(android.view.FrameMetrics.FIRST_DRAW_FRAME)==0L) {
                (application as OsifyApplication).frameStats.record(metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION),if(android.os.Build.VERSION.SDK_INT>=31) metrics.getMetric(android.view.FrameMetrics.DEADLINE) else 0L,dropped)
            }
        }.also {window.addOnFrameMetricsAvailableListener(it,android.os.Handler(worker.looper))}
    }
    override fun onDestroy() {metricsListener?.let {window.removeOnFrameMetricsAvailableListener(it)};metricsThread?.quitSafely();super.onDestroy()}
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus) hideNavigation()
    }
    override fun onResume() {
        super.onResume()
        preferSmoothDisplay()
    }
    private fun preferSmoothDisplay() {
        val saver=getSystemService(android.os.PowerManager::class.java).isPowerSaveMode
        if(android.os.Build.VERSION.SDK_INT>=36) {
            window.setFrameRateBoostOnTouchEnabled(true)
            window.decorView.requestedFrameRate=if(saver) android.view.View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT else android.view.View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        }
        @Suppress("DEPRECATION")
        val display=windowManager.defaultDisplay
        val current=display.mode
        val best=display.supportedModes.filter {it.physicalWidth==current.physicalWidth && it.physicalHeight==current.physicalHeight}.maxOfOrNull {it.refreshRate}?:0f
        window.attributes=window.attributes.apply {preferredRefreshRate=if(saver) 0f else best}
    }
    private fun hideNavigation() {
        androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply {
            systemBarsBehavior=androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        }
    }
}
