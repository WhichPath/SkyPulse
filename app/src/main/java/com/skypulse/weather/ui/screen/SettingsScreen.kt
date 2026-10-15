package com.skypulse.weather.ui.screen

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.skypulse.weather.ui.components.LucideIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.skypulse.weather.BuildConfig
import com.skypulse.weather.data.ThemeMode
import com.skypulse.weather.data.WeatherSettings
import com.skypulse.weather.data.remote.qweather.QWeatherConnectionResult
import com.skypulse.weather.data.remote.qweather.QWeatherCredentials
import com.skypulse.weather.ui.components.QWeatherApiSettingsSection
import com.skypulse.weather.ui.components.secondaryGlassSurface
import com.skypulse.weather.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    settings: WeatherSettings,
    onRainAlertChange: (Boolean) -> Unit,
    onWarningAlertChange: (Boolean) -> Unit,
    onTempChangeAlertChange: (Boolean) -> Unit,
    onWindAlertChange: (Boolean) -> Unit,
    onTyphoonAlertChange: (Boolean) -> Unit,
    onShowHourlyAqiChange: (Boolean) -> Unit,
    onShowHourlyUvChange: (Boolean) -> Unit,
    onShowHourlyWindChange: (Boolean) -> Unit,
    onShowHourlyWindGustChange: (Boolean) -> Unit,
    onShowCardDetailChange: (Boolean) -> Unit,
    onShowCardSunriseSunsetChange: (Boolean) -> Unit,
    onShowCardMinutelyChange: (Boolean) -> Unit,
    onShowCardTyphoonChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    qWeatherCredentials: QWeatherCredentials,
    hasBuildInjectedCredentials: Boolean,
    onSaveQWeatherCredentials: (String, String, String, String) -> Unit,
    onClearQWeatherCredentials: () -> Unit,
    onTestQWeatherCredentials: suspend (QWeatherCredentials) -> QWeatherConnectionResult
) {
    val context = LocalContext.current
    val page = LocalSecondaryPageTheme.current

    // 背景由 WeatherScreen 的天气着色渐变统一提供，这里必须透明，
    // 否则会盖住底色，卡片半透玻璃就失去了"底部有颜色"的来源
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("设置", color = page.textPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        LucideIcon(
                            name = "arrow-left",
                            contentDescription = "返回",
                            tint = page.backArrow
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Appearance settings（仅城市管理/预警详情/设置三页生效，主页与小组件不变）
                SectionHeader("外观")
                IosCard {
                    RadioItem(
                        title = "浅色",
                        selected = settings.themeMode == ThemeMode.LIGHT,
                        onClick = { onThemeModeChange(ThemeMode.LIGHT) }
                    )
                    IosDivider()
                    RadioItem(
                        title = "深色",
                        selected = settings.themeMode == ThemeMode.DARK,
                        onClick = { onThemeModeChange(ThemeMode.DARK) }
                    )
                    IosDivider()
                    RadioItem(
                        title = "跟随系统",
                        selected = settings.themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeModeChange(ThemeMode.SYSTEM) }
                    )
                }

                // Notification settings
                SectionHeader("通知设置")
                IosCard {
                    ToggleItem(
                        title = "短临降水提醒",
                        checked = settings.rainAlert,
                        onCheckedChange = onRainAlertChange,

                    )
                    IosDivider()
                    ToggleItem(
                        title = "气象预警",
                        checked = settings.warningAlert,
                        onCheckedChange = onWarningAlertChange,

                    )
                    IosDivider()
                    ToggleItem("变温提醒", settings.tempChangeAlert, onTempChangeAlertChange)
                    IosDivider()
                    ToggleItem("大风提醒", settings.windAlert, onWindAlertChange)
                    IosDivider()
                    ToggleItem(
                        title = "极端天气",
                        checked = settings.typhoonAlert,
                        onCheckedChange = onTyphoonAlertChange,

                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Hourly display settings
                SectionHeader("逐小时显示")
                IosCard {
                    ToggleItem(
                        title = "空气质量",
                        checked = settings.showHourlyAqi,
                        onCheckedChange = onShowHourlyAqiChange,

                    )
                    IosDivider()
                    ToggleItem(
                        title = "紫外线",
                        checked = settings.showHourlyUv,
                        onCheckedChange = onShowHourlyUvChange,

                    )
                    IosDivider()
                    ToggleItem(
                        title = "风力",
                        checked = settings.showHourlyWind,
                        onCheckedChange = onShowHourlyWindChange,

                    )
                    IosDivider()
                    ToggleItem(
                        title = "阵风",
                        checked = settings.showHourlyWindGust,
                        onCheckedChange = onShowHourlyWindGustChange,

                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Card display settings
                SectionHeader("卡片显示")
                IosCard {
                    ToggleItem(
                        title = "分钟级降水",
                        checked = settings.showCardMinutely,
                        onCheckedChange = onShowCardMinutelyChange,

                    )
                    IosDivider()
                    ToggleItem(
                        title = "气象详情",
                        checked = settings.showCardDetail,
                        onCheckedChange = onShowCardDetailChange,

                    )
                    IosDivider()
                    ToggleItem(
                        title = "日出日落与月相",
                        checked = settings.showCardSunriseSunset,
                        onCheckedChange = onShowCardSunriseSunsetChange,

                    )
                    IosDivider()
                    ToggleItem(
                        title = "台风雷达图",
                        checked = settings.showCardTyphoon,
                        onCheckedChange = onShowCardTyphoonChange
                        // 免费用户和会员均解锁
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 数据源设置：和风天气 API（v4.0.2 密钥与编译解耦）
                SectionHeader("数据源")
                QWeatherApiSettingsSection(
                    credentials = qWeatherCredentials,
                    buildInjectedAvailable = hasBuildInjectedCredentials,
                    onSave = onSaveQWeatherCredentials,
                    onClear = onClearQWeatherCredentials,
                    onTest = onTestQWeatherCredentials
                )

                Spacer(modifier = Modifier.height(8.dp))

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = page.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 22.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun IosCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)
            .secondaryGlassSurface(SkyPulseDesignSystem.Radius.settingsCard),
        content = content
    )
}


@Composable
private fun IosDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = SkyPulseDesignSystem.Border.hairline,
        color = LocalSecondaryPageTheme.current.divider
    )
}
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = LocalSecondaryPageTheme.current.textSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun ToggleItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    locked: Boolean = false,
    onLockedClick: (() -> Unit)? = null
) {
    val page = LocalSecondaryPageTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SkyPulseDesignSystem.TouchTarget.listRow)
            .clickable {
                if (locked) onLockedClick?.invoke()
                else onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (locked) page.textSecondary else page.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (locked) {
            LucideIcon(
                name = "lock",
                contentDescription = "会员功能",
                size = 18.dp,
                tint = page.textSecondary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Switch(
            checked = if (locked) false else checked,
            onCheckedChange = if (locked) null else onCheckedChange,
            enabled = !locked,
            modifier = Modifier.scale(0.8f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = page.accentBlue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = page.switchOffTrack,
                uncheckedBorderColor = Color.Transparent,
                disabledCheckedThumbColor = Color.White,
                disabledCheckedTrackColor = page.switchOffTrack,
                disabledUncheckedThumbColor = Color.White,
                disabledUncheckedTrackColor = page.switchOffTrack
            )
        )
    }
}


@Composable
private fun RadioItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val page = LocalSecondaryPageTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SkyPulseDesignSystem.TouchTarget.listRow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = page.textPrimary,
            modifier = Modifier.weight(1f)
        )
        // 圆形单选勾选框
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (selected) page.accentBlue else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // 外圈描边
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                drawCircle(
                    color = if (selected) page.accentBlue else page.textSecondary.copy(alpha = 0.6f),
                    radius = radius,
                    center = this.center,
                    style = Stroke(width = strokeWidth)
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}


@Composable
private fun SimpleItem(
    title: String,
    subtitle: AnnotatedString? = null,
    titleColor: Color = Color.Unspecified,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val page = LocalSecondaryPageTheme.current
    val resolvedTitleColor = if (titleColor == Color.Unspecified) page.textPrimary else titleColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = SkyPulseDesignSystem.TouchTarget.listRow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (subtitle != null) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = resolvedTitleColor
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = page.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}
