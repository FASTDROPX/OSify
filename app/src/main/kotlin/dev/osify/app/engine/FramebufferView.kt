package dev.osify.app.engine

import android.content.Context
import android.graphics.*
import android.view.*
import android.view.inputmethod.*
import dev.osify.core.RfbClient
import kotlin.math.abs
import kotlin.math.hypot

/** Guest pixels and input only; Material controls live in GuestConsoleScreen. */
class FramebufferView(context: Context,private val onStatus: (String)->Unit): View(context),RfbClient.Listener {
    var client: RfbClient?=null;private set
    private var bitmap: Bitmap?=null
    private val lock=Any()
    private val paint=Paint(Paint.FILTER_BITMAP_FLAG)
    private val cursorPaint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val target=RectF()
    private var guestWidth=1;private var guestHeight=1
    val guestAspect get()=guestWidth.toFloat()/guestHeight
    var presentationOnly=false
        set(value) {if(value) releaseInput();field=value;invalidate()}
    private var pointerX=0f;private var pointerY=0f
    var pointerMode=false;private set
    var dragging=false;private set
    private var touchPressed=false
    private var multiTouch=false;private var longPressed=false;private var moved=false;private var activeTouch=false
    private var lastX=0f;private var lastY=0f;private var startX=0f;private var startY=0f
    private var scrollY=0f
    private var zoom=1f;private var panX=0f;private var panY=0f
    private val modifiers=linkedSetOf<Int>()
    private val hardwareKeys=linkedMapOf<Int,Int>()
    internal var inputObserver: ((Int,Int,Int)->Unit)?=null
    var onDragState: (Boolean)->Unit={}
    private val scaleGesture=ScaleGestureDetector(context,object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {zoom=(zoom*detector.scaleFactor).coerceIn(1f,5f);invalidate();return true}
    })
    private val gestures=GestureDetector(context,object: GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent)=true
        override fun onLongPress(event: MotionEvent) {
            if(!multiTouch && !dragging) {touchPressed=false;sendPointer(0);rightClick();longPressed=true}
        }
    })
    init {isFocusable=true;isFocusableInTouchMode=true;setBackgroundColor(Color.BLACK);contentDescription="Guest display"}
    fun connect(port: Int,password: String) {releaseInput();client?.closeAfterInput();client=RfbClient(port,password,this).also {it.start()}}
    fun disconnect() {releaseInput();client?.closeAfterInput();client=null}
    fun setPointerMode(enabled: Boolean) {releaseButtons();pointerMode=enabled;invalidate()}
    fun setDragging(enabled: Boolean) {dragging=enabled;onDragState(enabled);touchPressed=false;sendPointer(if(enabled) 1 else 0);invalidate()}
    fun leftClick() {sendPointer(1);sendPointer(if(dragging) 1 else 0)}
    fun rightClick() {releaseButtons();sendPointer(4);sendPointer(0)}
    fun scroll(up: Boolean) {sendPointer(if(up) 8 else 16);sendPointer(if(dragging) 1 else 0)}
    fun stroke(symbol: Int) {client?.key(symbol,true);client?.key(symbol,false)}
    fun modifier(symbol: Int,down: Boolean) {
        if(down && modifiers.add(symbol)) client?.key(symbol,true)
        else if(!down && modifiers.remove(symbol)) client?.key(symbol,false)
    }
    fun releaseModifiers() {modifiers.toList().forEach {client?.key(it,false)};modifiers.clear()}
    fun releaseButtons() {touchPressed=false;dragging=false;onDragState(false);sendPointer(0)}
    fun releaseInput() {releaseButtons();releaseModifiers();hardwareKeys.values.toSet().forEach {client?.key(it,false)};hardwareKeys.clear()}
    var autoFit=true
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {super.onSizeChanged(w,h,oldw,oldh);if(autoFit)fit()}
    fun fit() {zoom=1f;panX=0f;panY=0f;invalidate()}
    fun zoomBy(factor: Float) {zoom=(zoom*factor).coerceIn(1f,5f);invalidate()}
    fun sendText(text: String) {
        val normalized=text.replace("\r\n","\n").replace('\r','\n')
        var start=0
        while(start<normalized.length) {
            var end=(start+8192).coerceAtMost(normalized.length)
            if(end<normalized.length && Character.isHighSurrogate(normalized[end-1]) && Character.isLowSurrogate(normalized[end])) end--
            client?.text(normalized.substring(start,end));start=end
        }
    }
    private fun sendPointer(mask: Int) {
        val x=pointerX.toInt().coerceIn(0,guestWidth-1);val y=pointerY.toInt().coerceIn(0,guestHeight-1)
        inputObserver?.invoke(x,y,mask);client?.pointer(x,y,mask)
    }
    override fun resize(width: Int,height: Int) {
        synchronized(lock) {bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)}
        post {guestWidth=width;guestHeight=height;pointerX=width/2f;pointerY=height/2f;fit()}
    }
    override fun pixels(x: Int,y: Int,width: Int,height: Int,argb: IntArray) {
        synchronized(lock) {bitmap?.setPixels(argb,0,width,x,y,width,height)};postInvalidateOnAnimation()
    }
    override fun status(message: String) {post {onStatus(message)}}
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(lock) {bitmap?.let {b->
            val scale=minOf(width.toFloat()/b.width,height.toFloat()/b.height)*zoom
            val w=b.width*scale;val h=b.height*scale
            panX=panX.coerceIn(-((w-width)/2).coerceAtLeast(0f),((w-width)/2).coerceAtLeast(0f))
            panY=panY.coerceIn(-((h-height)/2).coerceAtLeast(0f),((h-height)/2).coerceAtLeast(0f))
            target.set((width-w)/2+panX,(height-h)/2+panY,(width+w)/2+panX,(height+h)/2+panY)
            canvas.drawBitmap(b,null,target,paint)
            if(pointerMode && !presentationOnly) {
                val cx=target.left+pointerX*scale;val cy=target.top+pointerY*scale
                canvas.save();canvas.translate(cx,cy);canvas.scale(resources.displayMetrics.density,resources.displayMetrics.density)
                val arrow=Path().apply {moveTo(0f,0f);lineTo(0f,21f);lineTo(5f,16f);lineTo(9f,24f);lineTo(13f,22f);lineTo(9f,14f);lineTo(16f,14f);close()}
                cursorPaint.style=Paint.Style.STROKE;cursorPaint.strokeWidth=3.5f;cursorPaint.color=Color.BLACK;canvas.drawPath(arrow,cursorPaint)
                cursorPaint.strokeWidth=1.5f;cursorPaint.color=Color.WHITE;canvas.drawPath(arrow,cursorPaint)
                cursorPaint.style=Paint.Style.FILL;cursorPaint.color=if(dragging) Color.rgb(255,209,112) else Color.rgb(145,200,255)
                canvas.drawPath(arrow,cursorPaint);canvas.restore()
            }
        }}
    }
    private fun absolute(x: Float,y: Float) {
        if(target.width()<=0 || target.height()<=0) return
        pointerX=((x-target.left)/target.width()*guestWidth).coerceIn(0f,(guestWidth-1).toFloat())
        pointerY=((y-target.top)/target.height()*guestHeight).coerceIn(0f,(guestHeight-1).toFloat())
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(presentationOnly) return false
        if(bitmap==null || target.isEmpty) return true
        requestFocus()
        if(event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            absolute(event.x,event.y)
            val mask=(if(event.buttonState and MotionEvent.BUTTON_PRIMARY!=0) 1 else 0) or (if(event.buttonState and MotionEvent.BUTTON_SECONDARY!=0) 4 else 0) or (if(event.buttonState and MotionEvent.BUTTON_TERTIARY!=0) 2 else 0)
            sendPointer(if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL) 0 else mask)
            invalidate();return true
        }
        if(event.actionMasked==MotionEvent.ACTION_DOWN) {
            activeTouch=pointerMode || target.contains(event.x,event.y)
            multiTouch=false;longPressed=false;moved=false;scrollY=0f
            startX=event.x;startY=event.y;lastX=event.x;lastY=event.y
            if(!pointerMode && target.contains(event.x,event.y)) {absolute(event.x,event.y);touchPressed=true;sendPointer(1)}
        }
        if(!activeTouch && event.actionMasked!=MotionEvent.ACTION_CANCEL) return true
        if(event.actionMasked==MotionEvent.ACTION_POINTER_DOWN) {
            multiTouch=true;releaseButtons()
            lastX=(0 until event.pointerCount).map {event.getX(it)}.average().toFloat()
            lastY=(0 until event.pointerCount).map {event.getY(it)}.average().toFloat()
        }
        scaleGesture.onTouchEvent(event);gestures.onTouchEvent(event)
        if(event.actionMasked==MotionEvent.ACTION_MOVE) {
            if(event.pointerCount>1) {
                val cx=(0 until event.pointerCount).map {event.getX(it)}.average().toFloat()
                val cy=(0 until event.pointerCount).map {event.getY(it)}.average().toFloat()
                if(!scaleGesture.isInProgress) {
                    if(!pointerMode && zoom>1.01f) {panX+=cx-lastX;panY+=cy-lastY}
                    else {
                        scrollY+=cy-lastY
                        val threshold=24*resources.displayMetrics.density
                        while(abs(scrollY)>=threshold) {scroll(scrollY>0);scrollY+=if(scrollY>0) -threshold else threshold}
                    }
                }
                lastX=cx;lastY=cy
            } else if(!multiTouch && !longPressed) {
                if(hypot(event.x-startX,event.y-startY)>ViewConfiguration.get(context).scaledTouchSlop) moved=true
                if(pointerMode) {
                    val scale=(target.width()/guestWidth).coerceAtLeast(.1f)
                    pointerX=(pointerX+(event.x-lastX)/scale).coerceIn(0f,(guestWidth-1).toFloat())
                    pointerY=(pointerY+(event.y-lastY)/scale).coerceIn(0f,(guestHeight-1).toFloat())
                    val screenX=target.left+pointerX*scale;val screenY=target.top+pointerY*scale
                    val margin=24*resources.displayMetrics.density
                    panX+=when {screenX<margin->margin-screenX;screenX>width-margin->width-margin-screenX;else->0f}
                    panY+=when {screenY<margin->margin-screenY;screenY>height-margin->height-margin-screenY;else->0f}
                    sendPointer(if(dragging) 1 else 0)
                } else if(touchPressed) {absolute(event.x,event.y);sendPointer(1)}
                lastX=event.x;lastY=event.y
            }
        }
        if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL) {
            if(pointerMode && !multiTouch && !longPressed && !moved && event.actionMasked==MotionEvent.ACTION_UP && !dragging) leftClick()
            if(touchPressed) {touchPressed=false;sendPointer(0)}
            if(event.actionMasked==MotionEvent.ACTION_CANCEL) releaseButtons()
            performClick()
        }
        invalidate();return true
    }
    override fun onHoverEvent(event: MotionEvent): Boolean {
        if(event.isFromSource(InputDevice.SOURCE_MOUSE)) {absolute(event.x,event.y);sendPointer(0);invalidate();return true}
        return super.onHoverEvent(event)
    }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if(event.actionMasked==MotionEvent.ACTION_BUTTON_PRESS || event.actionMasked==MotionEvent.ACTION_BUTTON_RELEASE) {
            absolute(event.x,event.y)
            sendPointer((if(event.buttonState and MotionEvent.BUTTON_PRIMARY!=0) 1 else 0) or (if(event.buttonState and MotionEvent.BUTTON_SECONDARY!=0) 4 else 0) or (if(event.buttonState and MotionEvent.BUTTON_TERTIARY!=0) 2 else 0))
            invalidate();return true
        }
        if(event.actionMasked==MotionEvent.ACTION_SCROLL) {
            absolute(event.x,event.y)
            val amount=event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            repeat(abs(amount).toInt().coerceIn(1,20)) {scroll(amount>0)}
            return true
        }
        return super.onGenericMotionEvent(event)
    }
    override fun performClick(): Boolean {super.performClick();return true}
    override fun onCheckIsTextEditor()=true
    override fun onCreateInputConnection(info: EditorInfo): InputConnection {
        info.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        info.imeOptions=EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
        return object: BaseInputConnection(this,false) {
            override fun commitText(text: CharSequence?,newCursorPosition: Int): Boolean {sendText(text?.toString().orEmpty());return true}
            override fun deleteSurroundingText(beforeLength: Int,afterLength: Int): Boolean {
                repeat(beforeLength.coerceIn(0,100)) {stroke(0xff08)};repeat(afterLength.coerceIn(0,100)) {stroke(0xffff)};return true
            }
            override fun sendKeyEvent(event: KeyEvent)=dispatchKeyEvent(event)
        }
    }
    override fun onKeyDown(code: Int,event: KeyEvent): Boolean {
        val symbol=symbol(code,event)?:return super.onKeyDown(code,event);hardwareKeys.putIfAbsent(code,symbol);client?.key(symbol,true);return true
    }
    override fun onKeyUp(code: Int,event: KeyEvent): Boolean {
        val symbol=hardwareKeys.remove(code)?:symbol(code,event)?:return super.onKeyUp(code,event);client?.key(symbol,false);return true
    }
    private fun symbol(code: Int,event: KeyEvent): Int?=when(code) {
        KeyEvent.KEYCODE_ENTER->0xff0d;KeyEvent.KEYCODE_DEL->0xff08;KeyEvent.KEYCODE_ESCAPE->0xff1b;KeyEvent.KEYCODE_TAB->0xff09
        KeyEvent.KEYCODE_FORWARD_DEL->0xffff;KeyEvent.KEYCODE_INSERT->0xff63
        KeyEvent.KEYCODE_MOVE_HOME->0xff50;KeyEvent.KEYCODE_MOVE_END->0xff57;KeyEvent.KEYCODE_PAGE_UP->0xff55;KeyEvent.KEYCODE_PAGE_DOWN->0xff56
        KeyEvent.KEYCODE_CTRL_LEFT->0xffe3;KeyEvent.KEYCODE_CTRL_RIGHT->0xffe4;KeyEvent.KEYCODE_ALT_LEFT->0xffe9;KeyEvent.KEYCODE_ALT_RIGHT->0xffea
        KeyEvent.KEYCODE_SHIFT_LEFT->0xffe1;KeyEvent.KEYCODE_SHIFT_RIGHT->0xffe2;KeyEvent.KEYCODE_META_LEFT->0xffeb;KeyEvent.KEYCODE_META_RIGHT->0xffec
        KeyEvent.KEYCODE_DPAD_LEFT->0xff51;KeyEvent.KEYCODE_DPAD_UP->0xff52;KeyEvent.KEYCODE_DPAD_RIGHT->0xff53;KeyEvent.KEYCODE_DPAD_DOWN->0xff54
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12->0xffbe+code-KeyEvent.KEYCODE_F1
        else->event.getUnicodeChar(event.metaState and KeyEvent.META_CTRL_MASK.inv() and KeyEvent.META_ALT_MASK.inv()).takeIf {it>0}?.let {if(it<=255) it else 0x01000000 or it}
    }
}
