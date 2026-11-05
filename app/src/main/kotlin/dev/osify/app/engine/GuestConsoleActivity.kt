package dev.osify.app.engine

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import dev.osify.app.OsifyApplication
import dev.osify.app.data.AppPreferences
import dev.osify.app.ui.OsifyTheme
import kotlinx.coroutines.flow.map

class GuestConsoleActivity: ComponentActivity() {
    private var machineId by mutableStateOf("")
    private var pip by mutableStateOf(false)
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        machineId=intent.getStringExtra("vm")?:run {finish();return}
        val app=application as OsifyApplication
        setContent {
            val preferences by app.preferences.flow.collectAsState(initial=AppPreferences())
            val machine by remember(machineId) {app.database.dao().virtualMachines().map {rows->rows.firstOrNull {it.id==machineId}}}.collectAsState(initial=null)
            OsifyTheme(preferences) {machine?.let {vm->key(vm.id) {
                GuestConsoleScreen(vm,onLeave={finish()},pip=pip,
                    pipAvailable=packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE),onPip={view->enterPip(view)})
            }}}
        }
    }
    private fun enterPip(view: FramebufferView): Boolean {
        if(!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        view.releaseInput()
        val bounds=Rect();view.getGlobalVisibleRect(bounds)
        val ratio=view.guestAspect.coerceIn(1f/2.39f,2.39f)
        val builder=PictureInPictureParams.Builder().setAspectRatio(Rational((ratio*1000).toInt(),1000))
        if(!bounds.isEmpty) builder.setSourceRectHint(bounds)
        if(Build.VERSION.SDK_INT>=31) builder.setAutoEnterEnabled(false).setSeamlessResizeEnabled(false)
        pip=true
        return runCatching {enterPictureInPictureMode(builder.build())}.getOrDefault(false).also {if(!it) pip=false}
    }
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);setIntent(intent);intent.getStringExtra("vm")?.let {machineId=it}}
    override fun onPictureInPictureModeChanged(inPictureInPictureMode: Boolean,newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPictureInPictureMode,newConfig);pip=inPictureInPictureMode
    }
}
