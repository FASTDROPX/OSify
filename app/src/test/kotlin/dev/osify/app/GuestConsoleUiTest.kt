package dev.osify.app

import android.app.Activity
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import dev.osify.app.engine.FramebufferView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=TestOsifyApplication::class)
class GuestConsoleUiTest {
    @Test fun framebufferStatusIsDeliveredOnceRatherThanRepostedRecursively() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val messages=mutableListOf<String>()
            val framebuffer=FramebufferView(controller.get()) {messages+=it}
            controller.get().setContentView(framebuffer)
            val looper=shadowOf(Looper.getMainLooper())
            looper.idle()
            framebuffer.status("Framebuffer connected")
            looper.runOneTask()
            assertEquals(listOf("Framebuffer connected"),messages)
        } finally {controller.pause().stop().destroy()}
    }
    private fun withFrame(width: Int=640,height: Int=480,block: (FramebufferView)->Unit) {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val frame=FramebufferView(controller.get()) {}
            controller.get().setContentView(frame)
            frame.resize(640,480)
            shadowOf(Looper.getMainLooper()).idle()
            frame.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
            frame.layout(0,0,width,height)
            frame.draw(Canvas(Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)))
            block(frame)
        } finally {controller.pause().stop().destroy()}
    }
    private fun touch(frame: FramebufferView,action: Int,x: Float,y: Float,time: Long) {
        MotionEvent.obtain(0,time,action,x,y,0).also {frame.onTouchEvent(it);it.recycle()}
    }
    @Test fun trackpadMovesRelativelyWithoutClickingAndTapReleasesButton()=withFrame {frame->
        frame.setPointerMode(true)
        val events=mutableListOf<Triple<Int,Int,Int>>()
        frame.inputObserver={x,y,mask->events+=Triple(x,y,mask)}
        touch(frame,MotionEvent.ACTION_DOWN,100f,100f,0)
        touch(frame,MotionEvent.ACTION_MOVE,130f,150f,50)
        touch(frame,MotionEvent.ACTION_UP,130f,150f,100)
        assertEquals(Triple(350,290,0),events.last())
        assertFalse(events.any {it.third!=0})
        events.clear()
        touch(frame,MotionEvent.ACTION_DOWN,100f,100f,200)
        touch(frame,MotionEvent.ACTION_UP,100f,100f,230)
        assertEquals(listOf(1,0),events.map {it.third})
    }
    @Test fun touchesInLetterboxDoNotClickTheEdgeOfGuest()=withFrame(height=800) {frame->
        val events=mutableListOf<Int>();frame.inputObserver={_,_,mask->events+=mask}
        touch(frame,MotionEvent.ACTION_DOWN,20f,20f,0)
        touch(frame,MotionEvent.ACTION_UP,20f,20f,30)
        assertTrue(events.isEmpty())
    }
    @Test fun cancellationAndModeSwitchReleaseHeldMouseButton()=withFrame {frame->
        frame.setPointerMode(true)
        val events=mutableListOf<Int>();frame.inputObserver={_,_,mask->events+=mask}
        frame.setDragging(true)
        touch(frame,MotionEvent.ACTION_CANCEL,100f,100f,50)
        assertEquals(listOf(1,0),events)
        assertFalse(frame.dragging)
        frame.setDragging(true)
        frame.setPointerMode(false)
        assertEquals(0,events.last())
        assertFalse(frame.dragging)
    }
}
