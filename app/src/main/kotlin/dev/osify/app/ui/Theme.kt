package dev.osify.app.ui

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import dev.osify.app.data.AppPreferences
import java.util.Locale

val LocalRussian=staticCompositionLocalOf { false }
val LocalMotion=staticCompositionLocalOf { "full" }
val LocalMotionMillis=staticCompositionLocalOf { 350 }
@Composable fun tr(en: String,ru: String)=if(LocalRussian.current) ru else en
@Composable fun translator(): (String,String)->String {
    val russian=LocalRussian.current
    return remember(russian) { { en,ru -> if(russian) ru else en } }
}
@Composable fun OsifyTheme(preferences: AppPreferences, content: @Composable () -> Unit) {
    val context=LocalContext.current
    var systemScale by remember { mutableFloatStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)) }
    DisposableEffect(context) {
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { systemScale=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val dark=when(preferences.theme) { "dark"->true; "light"->false; else->isSystemInDarkTheme() }
    val brand=remember(dark) {if(dark) darkColorScheme(
        primary=BrandBlue,onPrimary=Color(0xFF092D49),primaryContainer=Color(0xFF203344),onPrimaryContainer=Color(0xFFD4E9FF),
        secondary=Color(0xFFADCBE7),onSecondary=Color(0xFF16344B),secondaryContainer=Color(0xFF263644),onSecondaryContainer=Color(0xFFD4E9FF),
        tertiary=Color(0xFFC4DCF1),tertiaryContainer=Color(0xFF26323D),onTertiaryContainer=Color(0xFFD4E9FF),
        background=Color(0xFF101418),surface=Color(0xFF101418),onSurface=Color(0xFFE5EBF1),onSurfaceVariant=Color(0xFFA6B4C1),
        surfaceContainer=Color(0xFF1A222A),surfaceContainerHigh=Color(0xFF232E38),surfaceContainerHighest=Color(0xFF2C3945),
        outline=Color(0xFF728495),outlineVariant=Color(0xFF33414E)
    ) else lightColorScheme(
        primary=Color(0xFF18649B),onPrimary=Color.White,primaryContainer=Color(0xFFD7EAFF),onPrimaryContainer=Color(0xFF163A58),
        secondary=Color(0xFF48647A),onSecondary=Color.White,secondaryContainer=Color(0xFFE0EDFA),onSecondaryContainer=Color(0xFF28465D),
        tertiary=Color(0xFF496B88),tertiaryContainer=Color(0xFFE4EFF9),onTertiaryContainer=Color(0xFF28465D),
        background=Color(0xFFF6F9FC),surface=Color(0xFFF6F9FC),onSurface=Color(0xFF17212B),onSurfaceVariant=Color(0xFF526475),
        surfaceContainer=Color(0xFFEBF1F7),surfaceContainerHigh=Color(0xFFE2EBF3),surfaceContainerHighest=Color(0xFFD9E4EE),
        outline=Color(0xFF728495),outlineVariant=Color(0xFFCAD7E2)
    )
    }
    // Wallpaper color is opt-in; all default tones are derived from the blue brand.
    val configuration=LocalConfiguration.current
    val palette=remember(context,dark,preferences.dynamicColor,configuration) {
        if(preferences.dynamicColor && Build.VERSION.SDK_INT>=31) {
            if(dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else brand
    }
    val target=remember(palette,dark,preferences.amoled) {
        if(dark && preferences.amoled) amoledScheme(palette) else palette
    }
    val russian=preferences.language=="ru" || (preferences.language=="system" && Locale.getDefault().language=="ru")
    val motion=if(systemScale>0) preferences.motion else "off"
    val view=LocalView.current
    DisposableEffect(view) {
        if(Build.VERSION.SDK_INT>=36) view.requestedFrameRate=android.view.View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        onDispose {if(Build.VERSION.SDK_INT>=36) view.requestedFrameRate=android.view.View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT}
    }
    val color=animatedScheme(target,when(motion) {"off"->0;"reduced"->120;else->600})
    val lightBars=color.surface.luminance()>.5f
    LaunchedEffect(lightBars) {
        (context as? android.app.Activity)?.let {activity->
            androidx.core.view.WindowCompat.getInsetsController(activity.window,view).isAppearanceLightStatusBars=lightBars
        }
    }
    SideEffect { (context.applicationContext as? dev.osify.app.OsifyApplication)?.motionScale?.update(preferences.motion,systemScale) }
    CompositionLocalProvider(LocalRussian provides russian,LocalMotion provides motion,LocalMotionMillis provides when(motion) { "off"->0;"reduced"->100;else->350 }) {
        val typography=remember {
          val base=Typography()
          Typography(
            displayLarge=base.displayLarge.copy(fontFamily=HeadingFont),displayMedium=base.displayMedium.copy(fontFamily=HeadingFont),displaySmall=base.displaySmall.copy(fontFamily=HeadingFont),
            headlineLarge=base.headlineLarge.copy(fontFamily=HeadingFont),headlineMedium=base.headlineMedium.copy(fontFamily=HeadingFont),headlineSmall=base.headlineSmall.copy(fontFamily=HeadingFont),
            titleLarge=base.titleLarge.copy(fontFamily=BodyFont),titleMedium=base.titleMedium.copy(fontFamily=BodyFont),titleSmall=base.titleSmall.copy(fontFamily=BodyFont),
            bodyLarge=base.bodyLarge.copy(fontFamily=BodyFont),bodyMedium=base.bodyMedium.copy(fontFamily=BodyFont),bodySmall=base.bodySmall.copy(fontFamily=BodyFont),
            labelLarge=base.labelLarge.copy(fontFamily=BodyFont),labelMedium=base.labelMedium.copy(fontFamily=NumberFont),labelSmall=base.labelSmall.copy(fontFamily=NumberFont))
        }
        MaterialTheme(colorScheme=color,typography=typography,content=content)
    }
}

internal fun amoledScheme(base: ColorScheme)=base.copy(
    background=Color.Black,surface=Color.Black,surfaceDim=Color.Black,surfaceBright=Color(0xFF242424),surfaceTint=Color.Transparent,
    surfaceContainerLowest=Color.Black,surfaceContainerLow=Color(0xFF080808),surfaceContainer=Color(0xFF101010),
    surfaceContainerHigh=Color(0xFF181818),surfaceContainerHighest=Color(0xFF202020),surfaceVariant=Color(0xFF202020))

@Composable private fun animatedScheme(target: ColorScheme,duration: Int): ColorScheme {
    val transition=updateTransition(target,label="theme palette")
    return target.copy(
        primary=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="primary") {it.primary}.value,
        onPrimary=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="onPrimary") {it.onPrimary}.value,
        primaryContainer=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="primaryContainer") {it.primaryContainer}.value,
        onPrimaryContainer=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="onPrimaryContainer") {it.onPrimaryContainer}.value,
        inversePrimary=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="inversePrimary") {it.inversePrimary}.value,
        secondary=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="secondary") {it.secondary}.value,
        onSecondary=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="onSecondary") {it.onSecondary}.value,
        secondaryContainer=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="secondaryContainer") {it.secondaryContainer}.value,
        onSecondaryContainer=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="onSecondaryContainer") {it.onSecondaryContainer}.value,
        tertiary=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="tertiary") {it.tertiary}.value,
        onTertiary=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="onTertiary") {it.onTertiary}.value,
        tertiaryContainer=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="tertiaryContainer") {it.tertiaryContainer}.value,
        onTertiaryContainer=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="onTertiaryContainer") {it.onTertiaryContainer}.value,
        background=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="background") {it.background}.value,
        onBackground=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="onBackground") {it.onBackground}.value,
        surface=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="surface") {it.surface}.value,
        onSurface=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="onSurface") {it.onSurface}.value,
        surfaceVariant=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="surfaceVariant") {it.surfaceVariant}.value,
        onSurfaceVariant=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="onSurfaceVariant") {it.onSurfaceVariant}.value,
        surfaceTint=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="surfaceTint") {it.surfaceTint}.value,
        inverseSurface=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="inverseSurface") {it.inverseSurface}.value,
        inverseOnSurface=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="inverseOnSurface") {it.inverseOnSurface}.value,
        error=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="error") {it.error}.value,
        onError=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="onError") {it.onError}.value,
        errorContainer=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="errorContainer") {it.errorContainer}.value,
        onErrorContainer=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="onErrorContainer") {it.onErrorContainer}.value,
        outline=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="outline") {it.outline}.value,
        outlineVariant=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="outlineVariant") {it.outlineVariant}.value,
        scrim=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="scrim") {it.scrim}.value,
        surfaceBright=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="surfaceBright") {it.surfaceBright}.value,
        surfaceDim=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="surfaceDim") {it.surfaceDim}.value,
        surfaceContainer=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="surfaceContainer") {it.surfaceContainer}.value,
        surfaceContainerHigh=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="surfaceContainerHigh") {it.surfaceContainerHigh}.value,
        surfaceContainerHighest=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="surfaceContainerHighest") {it.surfaceContainerHighest}.value,
        surfaceContainerLow=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="surfaceContainerLow") {it.surfaceContainerLow}.value,
        surfaceContainerLowest=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="surfaceContainerLowest") {it.surfaceContainerLowest}.value,
        primaryFixed=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="primaryFixed") {it.primaryFixed}.value,
        primaryFixedDim=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="primaryFixedDim") {it.primaryFixedDim}.value,
        onPrimaryFixed=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="onPrimaryFixed") {it.onPrimaryFixed}.value,
        onPrimaryFixedVariant=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="onPrimaryFixedVariant") {it.onPrimaryFixedVariant}.value,
        secondaryFixed=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="secondaryFixed") {it.secondaryFixed}.value,
        secondaryFixedDim=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="secondaryFixedDim") {it.secondaryFixedDim}.value,
        onSecondaryFixed=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="onSecondaryFixed") {it.onSecondaryFixed}.value,
        onSecondaryFixedVariant=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="onSecondaryFixedVariant") {it.onSecondaryFixedVariant}.value,
        tertiaryFixed=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="tertiaryFixed") {it.tertiaryFixed}.value,
        tertiaryFixedDim=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="tertiaryFixedDim") {it.tertiaryFixedDim}.value,
        onTertiaryFixed=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="onTertiaryFixed") {it.onTertiaryFixed}.value,
        onTertiaryFixedVariant=transition.animateColor(transitionSpec={tween(duration,easing=FastOutSlowInEasing)},label="onTertiaryFixedVariant") {it.onTertiaryFixedVariant}.value
    )
}
