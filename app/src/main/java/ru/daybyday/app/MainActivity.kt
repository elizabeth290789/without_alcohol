package ru.daybyday.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import ru.daybyday.app.ui.AppActions
import ru.daybyday.app.ui.DayByDayApp
import ru.daybyday.app.ui.UiNotificationSettings
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ru"))
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    private val model: TrackerViewModel by viewModels()
    private var pendingNotifications: UiNotificationSettings? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        model.setPermission(allowed && NotificationManagerCompat.from(this).areNotificationsEnabled())
        pendingNotifications?.let { desired ->
            model.setNotifications(if (allowed) desired else desired.copy(dailyEnabled = false, achievementEnabled = false, recordEnabled = false))
        }
        pendingNotifications = null
    }
    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { model.refreshDate() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            SideEffect {
                if (Build.VERSION.SDK_INT < 35) {
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = if (state.darkTheme) 0xFF101119.toInt() else 0xFFF8F7FC.toInt()
                }
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !state.darkTheme
                    isAppearanceLightNavigationBars = !state.darkTheme
                }
            }
            DayByDayApp(state, AppActions(
                onStart = model::start,
                onEditStart = model::editStart,
                onNewPeriod = model::newPeriod,
                onNotificationsChange = ::changeNotifications,
                onThemeChange = model::setTheme,
                onClearAll = model::clearAll,
                onDismissError = model::dismissError,
                onDismissCelebration = model::dismissCelebration,
                onOpenNotificationSettings = {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                },
            ))
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, clockReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        model.setPermission(NotificationManagerCompat.from(this).areNotificationsEnabled())
        model.refreshDate()
    }
    override fun onDestroy() {
        unregisterReceiver(clockReceiver)
        super.onDestroy()
    }

    private fun changeNotifications(desired: UiNotificationSettings) {
        val current = model.state.value.notifications
        val newlyEnabled = desired.dailyEnabled && !current.dailyEnabled ||
            desired.achievementEnabled && !current.achievementEnabled || desired.recordEnabled && !current.recordEnabled
        if (newlyEnabled && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingNotifications = desired
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            model.setNotifications(desired)
            model.setPermission(NotificationManagerCompat.from(this).areNotificationsEnabled())
        }
    }
}
