package com.skypulse.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.model.DailyAstro
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import java.util.Calendar
import java.util.Locale

@Composable
fun MoonriseMoonsetCard(
    astro: List<DailyAstro>?,
    modifier: Modifier = Modifier
) {
    val now = Calendar.getInstance()
    val todayStr = String.format(
        Locale.US, "%04d-%02d-%02d",
        now.get(Calendar.YEAR),
        now.get(Calendar.MONTH) + 1,
        now.get(Calendar.DAY_OF_MONTH)
    )

    val todayAstro = astro?.find { entry ->
        val d = entry.date ?: return@find false
        val datePart = if (d.contains("T")) d.substringBefore('T') else d
        datePart == todayStr
    } ?: astro?.firstOrNull()

    val moonriseTime = todayAstro?.moonrise?.time ?: "--:--"
    val moonsetTime = todayAstro?.moonset?.time ?: "--:--"
    val moonPhase = todayAstro?.moonPhase
    val phaseInfo = getMoonPhaseInfo(moonPhase)
    val progress = calculateMoonProgress(moonriseTime, moonsetTime)

    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // 顶部栏: 月升标签 (带向上箭头)、居中文相名称 (中文)、月落标签 (带向下箭头)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧: 月升 (moonrise)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LucideIcon(
                        name = "moonrise",
                        contentDescription = "月升",
                        tint = TextSecondary,
                        size = 20.dp
                    )
                    Text(
                        text = "月升",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                        color = TextSecondary
                    )
                }

                // 居中: 月相文字 (标准中文，如 "峨眉月", "满月", "盈凸月")
                Text(
                    text = phaseInfo.chineseName,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                    color = TextSecondary
                )

                // 右侧: 月落 (moonset)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "月落",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                        color = TextSecondary
                    )
                    LucideIcon(
                        name = "moonset",
                        contentDescription = "月落",
                        tint = TextSecondary,
                        size = 20.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 水平进度条与随月相形态动态变换的月亮图标指示器
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            ) {
                HorizontalMoonProgress(
                    progress = progress,
                    moonIcon = phaseInfo.iconName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 底部栏: 月升时刻与月落时刻
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = moonriseTime,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextPrimary
                )
                Text(
                    text = moonsetTime,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun HorizontalMoonProgress(
    progress: Float,
    moonIcon: String,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconSizePx = remember(density) { with(density) { 20.dp.roundToPx() } }
    val iconBitmap = remember(context, moonIcon, iconSizePx) {
        WeatherSvgRenderer.renderBitmap(context, moonIcon, iconSizePx)
            ?.asImageBitmap()
            ?: LucideSvgRenderer.renderBitmap(context, "moon", iconSizePx, "#FFFFFF")
                ?.asImageBitmap()
    }

    Canvas(modifier = modifier) {
        val barY = size.height / 2
        val barHeight = 3.dp.toPx()
        val cornerRadius = barHeight / 2
        val barWidth = size.width
        val indicatorX = barWidth * clampedProgress

        val gapRadius = 15.dp.toPx()
        val leftLineEnd = (indicatorX - gapRadius).coerceAtLeast(0f)
        val rightLineStart = (indicatorX + gapRadius).coerceAtMost(barWidth)

        // 已过时段
        if (leftLineEnd > 0f) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.3f),
                topLeft = Offset(0f, barY - barHeight / 2),
                size = Size(leftLineEnd, barHeight),
                cornerRadius = CornerRadius(cornerRadius)
            )
        }

        // 未过时段
        if (rightLineStart < barWidth) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(rightLineStart, barY - barHeight / 2),
                size = Size(barWidth - rightLineStart, barHeight),
                cornerRadius = CornerRadius(cornerRadius)
            )
        }

        // 外层微光光晕 (极简发光)
        drawCircle(
            color = Color.White.copy(alpha = 0.15f),
            radius = iconSizePx * 0.65f,
            center = Offset(indicatorX, barY)
        )

        // 绘制矢量月相图标 (保留 Meteocons 月相原生细腻光泽与形态)
        if (iconBitmap != null) {
            val iconOffset = Offset(indicatorX - iconSizePx / 2, barY - iconSizePx / 2)
            translate(left = iconOffset.x, top = iconOffset.y) {
                drawImage(image = iconBitmap)
            }
        }
    }
}

data class MoonPhaseInfo(
    val chineseName: String,
    val iconName: String
)

private fun getMoonPhaseInfo(rawPhase: String?): MoonPhaseInfo {
    if (rawPhase.isNullOrBlank()) {
        return MoonPhaseInfo("满月", "moon-full")
    }
    val normalized = rawPhase.trim().lowercase(Locale.US)
    return when {
        normalized.contains("new") || normalized == "新月" ->
            MoonPhaseInfo("新月", "moon-new")
        normalized.contains("waxing crescent") || normalized.contains("娥眉") || normalized.contains("蛾眉") ->
            MoonPhaseInfo("峨眉月", "moon-waxing-crescent")
        normalized.contains("first quarter") || normalized.contains("上弦") ->
            MoonPhaseInfo("上弦月", "moon-first-quarter")
        normalized.contains("waxing gibbous") || normalized.contains("盈凸") ->
            MoonPhaseInfo("盈凸月", "moon-waxing-gibbous")
        normalized.contains("full") || normalized == "满月" ->
            MoonPhaseInfo("满月", "moon-full")
        normalized.contains("waning gibbous") || normalized.contains("亏凸") ->
            MoonPhaseInfo("亏凸月", "moon-waning-gibbous")
        normalized.contains("last quarter") || normalized.contains("third quarter") || normalized.contains("下弦") ->
            MoonPhaseInfo("下弦月", "moon-last-quarter")
        normalized.contains("waning crescent") || normalized.contains("残月") ->
            MoonPhaseInfo("残月", "moon-waning-crescent")
        else -> {
            MoonPhaseInfo(rawPhase, "moon-full")
        }
    }
}

/**
 * 计算月升月落之间的当前周期进度 (0.0 ~ 1.0)
 */
private fun calculateMoonProgress(moonriseTime: String, moonsetTime: String): Float {
    if (moonriseTime == "--:--" || moonsetTime == "--:--") return 0.5f
    return try {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val riseParts = moonriseTime.split(":")
        val riseMinutes = riseParts[0].toInt() * 60 + riseParts[1].toInt()

        val setParts = moonsetTime.split(":")
        val setMinutes = setParts[0].toInt() * 60 + setParts[1].toInt()

        if (riseMinutes < setMinutes) {
            val total = setMinutes - riseMinutes
            if (total <= 0) return 0.5f
            val elapsed = currentMinutes - riseMinutes
            (elapsed.toFloat() / total).coerceIn(0f, 1f)
        } else {
            val total = (24 * 60 - riseMinutes) + setMinutes
            if (total <= 0) return 0.5f
            val elapsed = if (currentMinutes >= riseMinutes) {
                currentMinutes - riseMinutes
            } else if (currentMinutes <= setMinutes) {
                (24 * 60 - riseMinutes) + currentMinutes
            } else {
                if (currentMinutes - setMinutes < riseMinutes - currentMinutes) 1f else 0f
            }
            (elapsed.toFloat() / total).coerceIn(0f, 1f)
        }
    } catch (e: Exception) {
        0.5f
    }
}
