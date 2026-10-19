package com.ridgecast.weather.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kyant.backdrop.backdrops.LayerBackdrop

/**
 * 全局液态玻璃背景源。
 *
 * 由页面背景层通过 `Modifier.layerBackdrop(...)` 录制，玻璃面板只消费这个
 * 实时图层；面板移动/页面滚动时由 Backdrop 库按 `LayoutCoordinates` 重新
 * 对齐采样坐标，不会把背景纹理“粘”在面板上。
 *
 * 为 null 时（省电模式 / 预览 / 无背景源）玻璃自动降级为半透明表面。
 */
val LocalGlassBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/**
 * 监听系统省电模式（粒子引擎与玻璃降级共用）。
 * 系统保护广播，无需 EXPORTED/NOT_EXPORTED 标志。
 */
@Composable
internal fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val appContext = remember { context.applicationContext }
    val powerManager = remember {
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    var powerSave by remember { mutableStateOf(powerManager.isPowerSaveMode) }
    DisposableEffect(appContext) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                powerSave = powerManager.isPowerSaveMode
            }
        }
        appContext.registerReceiver(
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        )
        onDispose { appContext.unregisterReceiver(receiver) }
    }
    return powerSave
}
