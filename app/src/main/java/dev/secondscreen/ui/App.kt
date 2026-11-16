package dev.secondscreen.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.secondscreen.AppContainer
import dev.secondscreen.settings.Settings
import dev.secondscreen.settings.SettingsScreen
import dev.secondscreen.ui.theme.Ink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("no container") }
val LocalSettings = compositionLocalOf { Settings() }
/** Wall clock, ticking once per second. */
val LocalNow = compositionLocalOf { ZonedDateTime.now() }

@Composable
fun App(container: AppContainer) {
    val settings by container.settingsStore.flow.collectAsState(initial = null)
    val pages by container.layoutStore.flow.collectAsState(initial = null)
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val now by produceState(ZonedDateTime.now()) {
        while (true) {
            value = ZonedDateTime.now()
            delay(1000 - System.currentTimeMillis() % 1000)
        }
    }

    val s = settings
    val p = pages
    if (s == null || p == null) {
        Box(Modifier.fillMaxSize().background(Ink.Bg))
        return
    }

    NightDimmer(s, now)

    CompositionLocalProvider(
        LocalContainer provides container,
        LocalSettings provides s,
        LocalNow provides now,
    ) {
        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsScreen(
                initial = s,
                initialPages = p,
                onSave = { newSettings, newPages ->
                    scope.launch {
                        container.settingsStore.save(newSettings)
                        container.layoutStore.save(newPages)
                    }
                    showSettings = false
                },
                onCancel = { showSettings = false },
            )
        } else {
            DashboardScreen(pages = p, onOpenSettings = { showSettings = true })
        }
    }
}

/** Drops screen brightness to near-minimum during the configured night hours. */
@Composable
private fun NightDimmer(s: Settings, now: ZonedDateTime) {
    val activity = LocalContext.current as? Activity ?: return
    val hour = now.hour
    val night = remember(hour, s.nightStartHour, s.nightEndHour) {
        val a = s.nightStartHour
        val b = s.nightEndHour
        when {
            a == b -> false
            a < b -> hour in a until b
            else -> hour >= a || hour < b
        }
    }
    LaunchedEffect(night) {
        val lp = activity.window.attributes
        lp.screenBrightness = if (night) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = lp
    }
}
