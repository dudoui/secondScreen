package dev.secondscreen

import android.Manifest
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.secondscreen.ui.App
import dev.secondscreen.ui.theme.SecondScreenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        val container = (application as SecondScreenApp).container
        val askCalendar = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            container.phoneCalendar.refresh()
        }
        if (!container.phoneCalendar.hasPermission()) askCalendar.launch(Manifest.permission.READ_CALENDAR)
        setContent {
            SecondScreenTheme {
                App(container)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
