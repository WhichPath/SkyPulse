package com.ridgecast.weather.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ridgecast.weather.ui.components.LocalGlassBackdrop
import com.ridgecast.weather.ui.components.LocalGlassLight
import com.ridgecast.weather.ui.components.rememberGlassLight
import com.ridgecast.weather.ui.components.LucideIcon
import com.ridgecast.weather.ui.components.liquidGlass
import com.ridgecast.weather.ui.glass.GlassStyles
import com.ridgecast.weather.ui.theme.SkyPulseDesignSystem
import com.ridgecast.weather.ui.theme.SunnyGradient
import com.ridgecast.weather.ui.theme.TextPrimary
import com.ridgecast.weather.ui.theme.TextSecondary

@Composable
fun PermissionOnboardingScreen(
    onFinished: () -> Unit,
    onPermissionDenied: () -> Unit = {}
) {
    val context = LocalContext.current

    // 检查定位权限是否已授予
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // 处理定位权限回调：授权则进入主页面，拒绝则退出 APP
    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        if (granted) onFinished() else onPermissionDenied()
    }

    // 处理通知权限回调，完成后检查并请求定位权限
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // 不管用户是否授权，都继续请求定位权限
        if (hasLocationPermission()) {
            onFinished()
        } else {
            locationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(SunnyGradient.first(), SunnyGradient.last())
    )
    val backdrop = rememberLayerBackdrop()
    val glassLight = rememberGlassLight(null)

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景源层：引导页也纳入统一的实时玻璃背景体系
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush = gradientBrush)
            )
        }

        CompositionLocalProvider(
            LocalGlassBackdrop provides backdrop,
            LocalGlassLight provides glassLight
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "权限设置",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "为了提供准确天气和定位服务，请先开启以下权限",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OnboardingGlassRow(
                        icon = "bell",
                        title = "通知权限（可选）",
                        description = "用于推送天气预警与提醒"
                    )
                    OnboardingGlassRow(
                        icon = "map-pin",
                        title = "定位权限（必须）",
                        description = "用于获取当前位置天气信息"
                    )
                    OnboardingGlassRow(
                        icon = "crosshair",
                        title = "小组件定位（建议）",
                        description = "请在系统设置中将定位权限设为「始终允许」，以确保小组件正常更新"
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .liquidGlass(
                            radius = SkyPulseDesignSystem.Radius.large,
                            isDark = false,
                            backgroundColor = Color.White,
                            style = GlassStyles.OnboardingAction
                        )
                        .clickable {
                            // 先检查是否需要请求通知权限：Android 13+ 才是运行时权限
                            val needNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED

                            if (needNotification) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else if (hasLocationPermission()) {
                                onFinished()
                            } else {
                                locationLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "开启权限并继续",
                        style = MaterialTheme.typography.titleMedium,
                        color = SunnyGradient.first()
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingGlassRow(
    icon: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                radius = SkyPulseDesignSystem.Radius.large,
                isDark = false,
                backgroundColor = Color.White,
                style = GlassStyles.Onboarding
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LucideIcon(
            name = icon,
            contentDescription = null,
            tint = TextPrimary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
