package dev.osify.app.engine

import androidx.compose.animation.togetherWith

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import dev.osify.app.ui.GuestToolsDialog
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.osify.app.data.VirtualMachine
import dev.osify.app.ui.LocalMotionMillis
import dev.osify.app.ui.BodyFont
import dev.osify.app.ui.SingleLineLabel
import dev.osify.app.ui.tr
import dev.osify.app.ui.translator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import dev.osify.app.OsifyApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun GuestConsoleScreen(machine: VirtualMachine,onLeave: ()->Unit,pip: Boolean=false,pipAvailable: Boolean=false,onPip: (FramebufferView)->Boolean={false}) {
    val context=LocalContext.current
    val activity=context as Activity
    val translate=translator()
    val scope=rememberCoroutineScope()
    val snackbar=remember {SnackbarHostState()}
    val controls=remember {context.getSharedPreferences("guest-controls",Context.MODE_PRIVATE)}
    var pointer by rememberSaveable {mutableStateOf(controls.getBoolean("pointer",false))}
    var fullscreen by rememberSaveable {mutableStateOf(false)}
    var sheet by rememberSaveable {mutableStateOf("")}
    var leave by remember {mutableStateOf(false)}
    var menu by remember {mutableStateOf(false)}
    var tools by remember {mutableStateOf(false)}
    var viewport by remember {mutableStateOf(IntSize.Zero)}
    var autoFit by remember {mutableStateOf(controls.getBoolean("auto-fit",true))}
    var logs by remember {mutableStateOf(false)}
    var stopping by remember {mutableStateOf(false)}
    val stopDuration=if(dev.osify.app.ui.LocalMotion.current=="off") 0 else 500
    var stopError by remember {mutableStateOf("")}
    val landscape=LocalConfiguration.current.orientation==Configuration.ORIENTATION_LANDSCAPE
    var connected by remember {mutableStateOf(false)}
    var connecting by remember {mutableStateOf(true)}
    var draft by rememberSaveable {mutableStateOf("")}
    var dragging by remember {mutableStateOf(false)}
    var held by remember {mutableStateOf(emptySet<Int>())}
    val framebuffer=remember(machine.id) {FramebufferView(context) {
        connected=it=="Framebuffer connected";connecting=false
    }}
    val density=LocalDensity.current
    val keyboardOpen=WindowInsets.ime.getBottom(density)>0
    val ime=remember {context.getSystemService(InputMethodManager::class.java)}
    val configuration=LocalConfiguration.current
    val motion=LocalMotionMillis.current
    val colors=MaterialTheme.colorScheme
    val lifecycleOwner=LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner,framebuffer) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_PAUSE) {framebuffer.releaseInput();held=emptySet();dragging=false}}
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {lifecycleOwner.lifecycle.removeObserver(observer)}
    }
    DisposableEffect(framebuffer) {
        framebuffer.onDragState={dragging=it}
        framebuffer.connect(machine.port,machine.password)
        onDispose {framebuffer.disconnect()}
    }
    LaunchedEffect(autoFit) {framebuffer.autoFit=autoFit;if(autoFit)framebuffer.fit();controls.edit().putBoolean("auto-fit",autoFit).apply()}
    LaunchedEffect(viewport,keyboardOpen,pip) {
        if(!pip && !keyboardOpen && viewport.width>0 && viewport.height>0) {delay(600);withContext(Dispatchers.IO) {(context.applicationContext as OsifyApplication).guestTools.desiredDisplay(machine.id,viewport.width,viewport.height)}}
    }
    LaunchedEffect(pointer) {framebuffer.setPointerMode(pointer);dragging=false;controls.edit().putBoolean("pointer",pointer).apply()}
    LaunchedEffect(fullscreen,sheet,pip,leave,colors.surface) {
        val bars=WindowCompat.getInsetsController(activity.window,activity.window.decorView)
        bars.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.isAppearanceLightStatusBars=!fullscreen && colors.surface.luminance()>.5f
        bars.isAppearanceLightNavigationBars=!fullscreen && colors.surface.luminance()>.5f
        if(fullscreen) snackbar.currentSnackbarData?.dismiss()
        if((fullscreen || pip) && (sheet.isEmpty() || sheet=="keys") && !leave) bars.hide(WindowInsetsCompat.Type.systemBars()) else bars.show(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(Unit) {
        onDispose {WindowCompat.getInsetsController(activity.window,activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())}
    }
    fun releaseKeys() {held=emptySet();framebuffer.releaseModifiers()}
    fun closeSheet() {sheet="";releaseKeys();ime.hideSoftInputFromWindow(framebuffer.windowToken,0)}
    fun message(en: String,ru: String) {scope.launch {snackbar.showSnackbar(translate(en,ru))}}
    fun openSheet(name: String) {framebuffer.releaseInput();held=emptySet();dragging=false;ime.hideSoftInputFromWindow(framebuffer.windowToken,0);sheet=name}
    LaunchedEffect(leave) {if(leave) {framebuffer.releaseInput();held=emptySet();dragging=false}}
    LaunchedEffect(pip) {framebuffer.presentationOnly=pip;if(pip) {closeSheet();leave=false;menu=false;logs=false}}
    BackHandler(enabled=!pip) {
        when {
            sheet.isNotEmpty()->closeSheet()
            keyboardOpen->ime.hideSoftInputFromWindow(framebuffer.windowToken,0)
            else->leave=true
        }
    }
    @Composable fun MoreControls() {
        Box(if(landscape) Modifier.fillMaxWidth() else Modifier) {
            if(landscape) DockAction(Icons.Default.MoreVert,tr("Menu","Меню"),"console-menu",compact=true) {menu=true}
            else IconButton(onClick={menu=true},modifier=Modifier.testTag("console-menu")) {Icon(Icons.Default.MoreVert,tr("More controls","Ещё управление"),tint=colors.onSurface)}
            DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                DropdownMenuItem(text={Text(tr("Files, drivers & display","Файлы, драйверы и экран"))},leadingIcon={Icon(Icons.Default.FolderOpen,null)},onClick={menu=false;tools=true})
                DropdownMenuItem(text={Text(tr("Auto-fit on rotation","Подгонять при повороте"))},trailingIcon={Checkbox(autoFit,null)},onClick={autoFit=!autoFit})
                DropdownMenuItem(text={Text(tr("Fit screen","Вписать экран"))},leadingIcon={Icon(Icons.Default.FitScreen,null)},onClick={menu=false;framebuffer.fit()})
                DropdownMenuItem(text={Text(tr("Zoom in","Увеличить"))},leadingIcon={Icon(Icons.Default.ZoomIn,null)},onClick={framebuffer.zoomBy(1.25f)})
                DropdownMenuItem(text={Text(tr("Zoom out","Уменьшить"))},leadingIcon={Icon(Icons.Default.ZoomOut,null)},onClick={framebuffer.zoomBy(.8f)})
                DropdownMenuItem(text={Text(tr("Rotate screen","Повернуть экран"))},leadingIcon={Icon(Icons.Default.ScreenRotation,null)},onClick={menu=false;activity.requestedOrientation=if(landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE})
                DropdownMenuItem(text={Text(tr("Mouse buttons","Кнопки мыши"))},leadingIcon={Icon(Icons.Default.Mouse,null)},onClick={menu=false;openSheet("mouse")})
                if(pipAvailable) DropdownMenuItem(text={Text(tr("Picture-in-picture","Картинка в картинке"))},leadingIcon={Icon(Icons.Default.PictureInPictureAlt,null)},onClick={menu=false;if(!onPip(framebuffer)) message("PiP is unavailable","Не удалось открыть PiP")})
                DropdownMenuItem(text={Text(tr("Machine logs · TXT","Логи машины · TXT"))},leadingIcon={Icon(Icons.Default.Article,null)},onClick={menu=false;logs=true})
                DropdownMenuItem(text={Text(tr("Reconnect display","Переподключить экран"))},leadingIcon={Icon(Icons.Default.Refresh,null)},onClick={menu=false;releaseKeys();connected=false;connecting=true;framebuffer.connect(machine.port,machine.password)})
                DropdownMenuItem(text={Text(tr("How to control","Как управлять"))},leadingIcon={Icon(Icons.Default.HelpOutline,null)},onClick={menu=false;openSheet("help")})
                DropdownMenuItem(text={Text(tr("Leave screen…","Выйти из экрана…"))},leadingIcon={Icon(Icons.AutoMirrored.Filled.ArrowBack,null)},onClick={menu=false;leave=true})
            }
        }
    }
    @Composable fun MainActions() {
        DockAction(if(keyboardOpen) Icons.Default.KeyboardHide else Icons.Default.Keyboard,tr("Keyboard","Ввод"),"console-keyboard",selected=keyboardOpen,enabled=connected,compact=landscape) {
            if(keyboardOpen) ime.hideSoftInputFromWindow(framebuffer.windowToken,0) else {if(sheet=="keys") closeSheet();framebuffer.requestFocus();ime.showSoftInput(framebuffer,InputMethodManager.SHOW_IMPLICIT)}
        }
        DockAction(Icons.Default.ContentPaste,tr("Text","Текст"),"console-text",enabled=connected,compact=landscape) {openSheet("text")}
        DockAction(Icons.Default.Mouse,tr("Mouse","Мышь"),"console-pointer",selected=pointer,enabled=connected,compact=landscape) {pointer=!pointer}
        DockAction(Icons.Default.KeyboardCommandKey,tr("Keys","Клавиши"),"console-keys",selected=sheet=="keys",enabled=connected,compact=landscape) {if(sheet=="keys") closeSheet() else openSheet("keys")}
    }
    @Composable fun FullscreenToggle(tag: String) {
        if(landscape) DockAction(if(fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,if(fullscreen) tr("Window","Окно") else tr("Fullscreen","Весь экран"),tag,compact=true) {fullscreen=!fullscreen}
        else IconButton(onClick={fullscreen=!fullscreen},modifier=Modifier.testTag(tag)) {Icon(if(fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,tr("Toggle fullscreen","Переключить полный экран"),tint=colors.onSurface)}
    }
    val insets=if(pip) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).imePadding()
    Box(Modifier.fillMaxSize().background(Color.Black).then(insets)) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(!fullscreen && !landscape && !pip,enter=fadeIn(tween(motion))+expandVertically(tween(motion)),exit=fadeOut(tween(motion))+shrinkVertically(tween(motion))) {
                Surface(color=colors.surface,modifier=Modifier.testTag("console-header")) {
                    Row(Modifier.statusBarsPadding().fillMaxWidth().heightIn(min=52.dp).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={leave=true},modifier=Modifier.testTag("console-back")) {Icon(Icons.AutoMirrored.Filled.ArrowBack,tr("Back to Launcher","В Launcher"))}
                        Column(Modifier.weight(1f).padding(horizontal=4.dp)) {
                            Text(machine.name,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                            AnimatedContent(Triple(connected,connecting,pointer),label="console connection") {(ready,busy,mouse)->
                                Text(if(ready) {if(mouse) tr("Mouse pointer","Указатель мыши") else tr("Touch control","Касания экрана")} else if(busy) tr("Connecting…","Подключение…") else tr("Disconnected","Нет соединения"),style=MaterialTheme.typography.labelSmall.copy(fontFamily=BodyFont,fontWeight=FontWeight.Bold),color=if(ready) colors.primary else colors.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick={fullscreen=true},modifier=Modifier.testTag("console-fullscreen")) {Icon(Icons.Default.Fullscreen,tr("Fullscreen","На весь экран"))}
                        MoreControls()
                    }
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth().testTag("console-viewport")) {
                        AndroidView(factory={framebuffer},modifier=Modifier.fillMaxSize().onSizeChanged {viewport=it}.testTag("guest-display"),update={it.contentDescription=translate("Guest display","Экран гостевой ОС")})
                        if(!connected && !pip) {
                            Surface(shape=RoundedCornerShape(24.dp),color=colors.surfaceContainer.copy(alpha=.95f),modifier=Modifier.align(Alignment.Center).padding(24.dp)) {
                                Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                    if(connecting) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp) else Icon(Icons.Default.LinkOff,null)
                                    Text(if(connecting) tr("Opening display…","Открываем экран…") else tr("Display disconnected","Экран отключён"),style=MaterialTheme.typography.bodySmall)
                                    if(!connecting) IconButton(onClick={connecting=true;framebuffer.connect(machine.port,machine.password)},modifier=Modifier.testTag("console-reconnect")) {Icon(Icons.Default.Refresh,tr("Reconnect","Подключить"))}
                                }
                            }
                        }
                    }
                    AnimatedVisibility(sheet=="keys" && !pip,enter=fadeIn(tween(motion))+expandVertically(tween(motion)),exit=fadeOut(tween(motion))+shrinkVertically(tween(motion))) {
                        CompactGuestKeysPanel(landscape,held,
                            onModifier={key->val down=key !in held;held=if(down) held+key else held-key;framebuffer.modifier(key,down)},
                            onKey={framebuffer.stroke(it)},
                            onCtrlAltDel={releaseKeys();framebuffer.modifier(0xffe3,true);framebuffer.modifier(0xffe9,true);framebuffer.stroke(0xffff);framebuffer.releaseModifiers()},
                            onClose={closeSheet()})
                    }
                }
                if(landscape && !pip) Surface(color=colors.surfaceContainer,shape=RoundedCornerShape(topStart=24.dp,bottomStart=24.dp),modifier=Modifier.width(144.dp).fillMaxHeight().testTag("console-dock")) {
                    Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)).verticalScroll(rememberScrollState()).padding(4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(1.dp)) {
                        MainActions()
                        HorizontalDivider(Modifier.padding(vertical=2.dp))
                        FullscreenToggle(if(fullscreen) "console-restore" else "console-fullscreen")
                        MoreControls()
                    }
                }
            }
            if(!landscape && !pip) Surface(color=colors.surfaceContainer,shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp),modifier=Modifier.testTag("console-dock")) {
                Column(Modifier.navigationBarsPadding().fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)) {
                    AnimatedVisibility(pointer && !keyboardOpen) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {MouseControls(framebuffer,connected,dragging) {framebuffer.setDragging(!dragging)}}
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {MainActions()}
                }
            }
        }
        if(fullscreen && !landscape && !pip) Surface(color=colors.surfaceContainer.copy(alpha=.85f),shape=RoundedCornerShape(24.dp),modifier=Modifier.align(Alignment.TopEnd).displayCutoutPadding().padding(8.dp)) {
            Row {FullscreenToggle("console-restore");MoreControls()}
        }
        if(!pip) SnackbarHost(snackbar,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=if(landscape) 8.dp else if(pointer) 132.dp else 76.dp))
    }
    if(leave && !pip) ModalBottomSheet(onDismissRequest={if(!stopping) leave=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(machine.name,style=MaterialTheme.typography.titleLarge)
            Text(tr("Hide the screen, keep it in a floating window, or stop the machine.","Скройте экран, оставьте его в плавающем окне или остановите машину."),style=MaterialTheme.typography.bodyMedium)
            Button(onClick={
                stopping=true;stopError=""
                val stopStarted=android.os.SystemClock.uptimeMillis()
                scope.launch {
                    runCatching {
                        val app=context.applicationContext as OsifyApplication
                        withContext(Dispatchers.IO) {
                            val latest=app.database.dao().virtualMachine(machine.id)
                            if(latest!=null && latest.state !in setOf("STOPPED","FAILED")) app.vmEngine.control(latest,"force")
                            withTimeout(15000) {app.database.dao().virtualMachines().first {rows->rows.none {it.id==machine.id && it.state !in setOf("STOPPED","FAILED")}}}
                        }
                        delay((stopDuration-(android.os.SystemClock.uptimeMillis()-stopStarted)).coerceAtLeast(0))
                        onLeave()
                    }.onFailure {stopError=it.message.orEmpty()}
                    stopping=false
                }
            },enabled=!stopping,colors=ButtonDefaults.buttonColors(containerColor=colors.errorContainer,contentColor=colors.onErrorContainer),modifier=Modifier.fillMaxWidth().testTag("console-stop")) {Icon(Icons.Default.Stop,null);Spacer(Modifier.width(8.dp));androidx.compose.animation.AnimatedContent(stopping,transitionSpec={androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(stopDuration)) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(stopDuration))},label="console stop state") {value->Text(if(value) tr("Stopping…","Останавливаем…") else tr("Stop machine","Остановить машину"),maxLines=1)}}
            if(stopping) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(stopError.isNotEmpty()) Text(stopError,color=colors.error)
            OutlinedButton(onClick={leave=false;if(!onPip(framebuffer)) message("PiP is unavailable","Не удалось открыть PiP")},enabled=pipAvailable && !stopping,modifier=Modifier.fillMaxWidth().testTag("console-pip")) {Icon(Icons.Default.PictureInPictureAlt,null);Spacer(Modifier.width(8.dp));Text(tr("Picture-in-picture","Картинка в картинке"))}
            FilledTonalButton(onClick={framebuffer.releaseInput();onLeave()},enabled=!stopping,modifier=Modifier.fillMaxWidth().testTag("console-leave-confirm")) {Icon(Icons.Default.VisibilityOff,null);Spacer(Modifier.width(8.dp));Text(tr("Hide screen","Скрыть экран"))}
            TextButton(onClick={leave=false},enabled=!stopping,modifier=Modifier.fillMaxWidth().testTag("console-stay")) {Text(tr("Cancel","Отмена"))}
        }
    }
    if(tools && !pip) GuestToolsDialog(context.applicationContext as OsifyApplication,machine,onDismiss={tools=false})
    if(logs && !pip) VmLogsDialog(machine,onDismiss={logs=false},onDiagnosticCommand={draft=it;logs=false;openSheet("text")})
    if(sheet.isNotEmpty() && sheet!="keys" && !pip) {
        val sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)
        ModalBottomSheet(onDismissRequest={closeSheet()},sheetState=sheetState,containerColor=colors.surfaceContainer,modifier=Modifier.testTag("console-sheet")) {
            Column(Modifier.fillMaxWidth().heightIn(max=520.dp).verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(when(sheet) {"text"->tr("Text & clipboard","Текст и буфер");"keys"->tr("Guest keyboard","Клавиши ОС");"mouse"->tr("Mouse buttons","Кнопки мыши");else->tr("How to control","Как управлять")},style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                    IconButton(onClick={closeSheet()},modifier=Modifier.testTag("console-sheet-close")) {Icon(Icons.Default.Close,tr("Close panel","Закрыть панель"))}
                }
                when(sheet) {
                    "mouse"->{
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {MouseControls(framebuffer,connected,dragging) {framebuffer.setDragging(!dragging)}}
                        Text(tr("Close this panel to move the pointer. Drag keeps the left button pressed until you turn it off.","Закройте панель, чтобы двигать указатель. «Тянуть» удерживает левую кнопку до повторного нажатия."),style=MaterialTheme.typography.bodySmall)
                    }
                    "text"->{
                        val clipboard=remember {context.getSystemService(ClipboardManager::class.java)}
                        val focus=remember {FocusRequester()}
                        LaunchedEffect(Unit) {focus.requestFocus()}
                        OutlinedTextField(value=draft,onValueChange={draft=it.take(8192)},label={Text(tr("Text to insert","Текст для вставки"))},placeholder={Text(tr("Type with your usual keyboard","Введите обычной клавиатурой"))},modifier=Modifier.fillMaxWidth().focusRequester(focus).testTag("console-text-input"),minLines=3,maxLines=6,keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.None),supportingText={Text(if(draft.length==8192) tr("Limit: 8192 characters per insert.","Лимит: 8192 символа за одну вставку.") else tr("Inserted as keystrokes. Guest input language applies.","Вводится как клавиши. Язык ввода задаётся в самой ОС."))})
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick={
                                val item=clipboard.primaryClip?.takeIf {it.itemCount>0}?.getItemAt(0)
                                if(item!=null) {
                                    val text=item.coerceToText(context).toString();draft=text.take(8192)
                                    if(text.length>8192) message("Read the first 8192 characters","Взяты первые 8192 символа")
                                }
                                else message("Clipboard is empty","Буфер обмена пуст")
                            },modifier=Modifier.weight(1f).testTag("console-read-clipboard"),contentPadding=PaddingValues(horizontal=8.dp,vertical=10.dp)) {Icon(Icons.Default.ContentPaste,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text(tr("From clipboard","Из буфера"),maxLines=1,modifier=Modifier.testTag("console-read-clipboard-label"))}
                            if(density.fontScale>1.2f) {
                                OutlinedIconButton(onClick={clipboard.setPrimaryClip(ClipData.newPlainText("OSify",draft));message("Copied","Скопировано")},enabled=draft.isNotEmpty(),modifier=Modifier.size(48.dp).testTag("console-copy-text")) {Icon(Icons.Default.ContentCopy,tr("Copy text","Копировать текст"),Modifier.size(20.dp))}
                            } else {
                                OutlinedButton(onClick={clipboard.setPrimaryClip(ClipData.newPlainText("OSify",draft));message("Copied","Скопировано")},enabled=draft.isNotEmpty(),modifier=Modifier.weight(1f).testTag("console-copy-text"),contentPadding=PaddingValues(horizontal=8.dp,vertical=10.dp)) {Icon(Icons.Default.ContentCopy,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text(tr("Copy","Копировать"),maxLines=1)}
                            }
                        }
                        Button(onClick={
                            clipboard.setPrimaryClip(ClipData.newPlainText("OSify",draft));framebuffer.sendText(draft);closeSheet();framebuffer.requestFocus()
                        },enabled=connected && draft.isNotEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("console-paste")) {Icon(Icons.Default.KeyboardReturn,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(tr("Insert into OS","Вставить в ОС"))}
                    }
                    else->{
                        HelpLine(Icons.Default.TouchApp,tr("Touch","Касания"),tr("Tap the guest directly. Hold for a right click.","Нажимайте прямо на экран ОС. Удержание — правый клик."))
                        HelpLine(Icons.Default.Mouse,tr("Mouse pointer","Мышь с указателем"),tr("Move one finger like a trackpad. Tap to click, two fingers to scroll. Drag holds the left button until tapped again.","Ведите пальцем как по тачпаду. Касание — клик, два пальца — прокрутка. «Тянуть» удерживает ЛКМ до повторного нажатия."))
                        HelpLine(Icons.Default.ZoomIn,tr("Zoom & rotate","Масштаб и поворот"),tr("Pinch to zoom. In touch mode, move two fingers to pan a zoomed view. Fit and rotate are in the menu.","Щипок меняет масштаб. В режиме касаний два пальца двигают увеличенный экран. Вписать и повернуть — в меню."))
                        HelpLine(Icons.Default.ContentPaste,tr("Text","Текст"),tr("Type with your normal keyboard, read or copy the phone clipboard, then insert explicitly. A multiline insert also sends Enter.","Пишите обычной клавиатурой, копируйте или читайте буфер телефона, затем вставляйте кнопкой. Перенос строки также отправляет Enter."))
                        HelpLine(Icons.Default.Fullscreen,tr("Fullscreen","На весь экран"),tr("Hides the header and system bars; controls remain available. Landscape uses the right rail. Back offers hide, PiP or stop.","Скрывает заголовок и системные строки, управление остаётся доступным. В альбомной ориентации панель справа. «Назад» предлагает скрыть экран, PiP или остановку."))
                    }
                }
            }
        }
    }
}

@Composable private fun DockAction(icon: ImageVector,label: String,tag: String,selected: Boolean=false,enabled: Boolean=true,compact: Boolean=false,onClick: ()->Unit) {
    val colors=MaterialTheme.colorScheme
    val contentColor=(if(selected) colors.onPrimaryContainer else colors.onSurfaceVariant).copy(alpha=if(enabled) 1f else .38f)
    val image: @Composable ()->Unit={Box(Modifier.size(if(compact) 32.dp else 30.dp),contentAlignment=Alignment.Center) {Icon(icon,null,Modifier.size(22.dp),tint=contentColor)}}
    val text: @Composable ()->Unit={SingleLineLabel(label,Modifier.testTag("$tag-label"),style=MaterialTheme.typography.labelSmall.copy(fontFamily=BodyFont,fontWeight=FontWeight.Bold,color=contentColor))}
    Surface(onClick=onClick,enabled=enabled,color=if(selected) colors.primaryContainer else Color.Transparent,shape=RoundedCornerShape(20.dp),modifier=(if(compact) Modifier.fillMaxWidth() else Modifier).testTag(tag).semantics {this.selected=selected}) {
        if(compact) Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically) {image();text()}
        else Column(Modifier.widthIn(min=64.dp).heightIn(min=48.dp).padding(horizontal=4.dp,vertical=2.dp),horizontalAlignment=Alignment.CenterHorizontally) {image();text()}
    }
}
@Composable private fun MouseControls(framebuffer: FramebufferView,connected: Boolean,dragging: Boolean,onDrag: ()->Unit) {
    ConsoleKey(tr("Left click","ЛКМ"),onClick={framebuffer.leftClick()},enabled=connected,tag="mouse-left")
    ConsoleKey(tr("Right click","ПКМ"),onClick={framebuffer.rightClick()},enabled=connected,tag="mouse-right")
    FilterChip(selected=dragging,onClick=onDrag,label={Text(tr("Drag","Тянуть"))},leadingIcon={Icon(Icons.Default.OpenWith,null,Modifier.size(16.dp))},enabled=connected,modifier=Modifier.padding(horizontal=4.dp).testTag("mouse-drag"))
    IconButton(onClick={framebuffer.scroll(true)},enabled=connected) {Icon(Icons.Default.KeyboardArrowUp,tr("Scroll up","Прокрутить вверх"))}
    IconButton(onClick={framebuffer.scroll(false)},enabled=connected) {Icon(Icons.Default.KeyboardArrowDown,tr("Scroll down","Прокрутить вниз"))}
}
@Composable private fun ConsoleKey(label: String,onClick: ()->Unit,enabled: Boolean=true,tag: String="") {
    TextButton(onClick=onClick,enabled=enabled,modifier=Modifier.heightIn(min=48.dp).testTag(tag),contentPadding=PaddingValues(horizontal=12.dp)) {Text(label,maxLines=1)}
}
@Composable private fun HelpLine(icon: ImageVector,title: String,body: String) {
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.padding(vertical=4.dp)) {
        Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(24.dp))
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(title,style=MaterialTheme.typography.titleSmall);Text(body,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
