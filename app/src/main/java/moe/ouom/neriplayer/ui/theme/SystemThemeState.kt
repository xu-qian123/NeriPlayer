package moe.ouom.neriplayer.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

fun isActualSystemDarkTheme(context: Context? = null): Boolean {
    // 优先读取系统底层全局配置，不受应用内部 AppCompatDelegate 覆写影响，
    // 能够在开启“自动/定时深色模式”时准确获取当前屏幕的真实明暗状态。
    val systemNightMode = runCatching {
        Resources.getSystem()?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)
    }.getOrNull()
    if (systemNightMode == Configuration.UI_MODE_NIGHT_YES) {
        return true
    }
    if (systemNightMode == Configuration.UI_MODE_NIGHT_NO) {
        return false
    }

    // 兜底读取传入 Context 的配置（如 Activity 或 Application）
    if (context != null) {
        val contextNightMode = runCatching {
            context.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)
        }.getOrNull()
        if (contextNightMode == Configuration.UI_MODE_NIGHT_YES) {
            return true
        }
        if (contextNightMode == Configuration.UI_MODE_NIGHT_NO) {
            return false
        }
    }

    return false
}

@Composable
fun rememberActualSystemDarkTheme(): Boolean {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // LocalConfiguration 随 Activity 的配置变更自动重组
    val isDarkFromConfig = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val isDarkFromSystem = isActualSystemDarkTheme(context)

    var systemDark by remember(isDarkFromConfig, isDarkFromSystem) {
        mutableStateOf(isDarkFromSystem)
    }

    DisposableEffect(context) {
        val appContext = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                systemDark = isActualSystemDarkTheme(context)
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            runCatching {
                appContext.unregisterReceiver(receiver)
            }
        }
    }

    return systemDark
}
