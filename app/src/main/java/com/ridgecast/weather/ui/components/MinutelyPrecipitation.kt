package com.ridgecast.weather.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import com.ridgecast.weather.model.MinutelyForecast
import com.ridgecast.weather.ui.theme.PrecipBarBottom
import com.ridgecast.weather.ui.theme.PrecipBarShadow
import com.ridgecast.weather.ui.theme.PrecipBarTop
import com.ridgecast.weather.ui.theme.SkyPulseDesignSystem
import com.ridgecast.weather.ui.theme.TextPrimary
import com.ridgecast.weather.ui.theme.TextSecondary
import com.ridgecast.weather.ui.screen.LocalSkipCardAnimation

private val minuteTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private const val BAR_COUNT = 24
private const val CHART_HEIGHT_DP = 60f

@Composable
fun MinutelyPrecipitationCard(
    minutely: MinutelyForecast?,
    modifier: Modifier = Modifier
) {
    val raw = minutely?.precipitation_2h
    if (raw.isNullOrEmpty()) return
    if (raw.all { it == 0.0 }) return

    // 和风 /v7/minutely/5m 提供未来 2 小时共 24 个 5 分钟数据点
    // 将 5 分钟累计降水量 (mm) 乘以 12 (60/5) 转换为小时降水率 (mm/h)
    val sampled = remember(raw) { sampleToBins(raw, BAR_COUNT).map { it * 12.0 } }
    if (sampled.all { it == 0.0 }) return

    val skipAnimation = LocalSkipCardAnimation.current
    var visible by remember { mutableStateOf(false) }
    val cardAlpha by animateFloatAsState(
        targetValue = if (skipAnimation || visible) 1f else 0f,
        animationSpec = if (skipAnimation) tween(0) else tween(
            SkyPulseDesignSystem.Motion.cardEnterMillis,
            delayMillis = SkyPulseDesignSystem.Motion.fastMillis
        ),
        label = "minutely_fade"
    )
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(skipAnimation) { if (skipAnimation) visible = true }

    GlassCard(modifier = modifier.alpha(cardAlpha)) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                // 头部用 API 返回的描述（和风 minutely summary，如「未来两小时有降水」），
                // 没有描述时退回通用标题。
                text = minutely?.description?.takeIf { it.isNotBlank() } ?: "分钟级降水",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            MinutelyBarChart(
                data = sampled,
                minutely = minutely,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun MinutelyBarChart(
    data: List<Double>,
    minutely: MinutelyForecast? = null,
    modifier: Modifier = Modifier
) {
    val chartHeightDp = CHART_HEIGHT_DP.dp
    val density = LocalDensity.current

    Column(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val containerWidthPx = with(density) { maxWidth.toPx() }
            // 自适应柱宽：柱和间隙各占一半，填满容器宽度
            val unitWidth = containerWidthPx / (data.size * 2 - 1)
            val barWidthPx = unitWidth
            val barGapPx = unitWidth

            Column {
                // Bar chart canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeightDp)
                ) {
                    val barCount = data.size
                    val barW = barWidthPx
                    val gapW = barGapPx
                    val step = barW + gapW
                    val chartH = size.height
                    val corner = CornerRadius(barW / 2f)

                    val grayFrame = Color.White.copy(alpha = 0.12f)

                    for (i in 0 until barCount) {
                        val value = data[i]
                        // 依据我国气象小时降水量标准(mm/h)平滑映射：
                        // 小雨(0-2.5mm/h) → 0-35%, 中雨(2.5-8mm/h) → 35-65%,
                        // 大雨(8-16mm/h) → 65-85%, 暴雨(16-30mm/h+) → 85-100%
                        val fillRatio = when {
                            value <= 0.0 -> 0f
                            value <= 2.5 -> (value / 2.5 * 0.35f).toFloat()
                            value <= 8.0 -> (0.35f + (value - 2.5) / 5.5 * 0.30f).toFloat()
                            value <= 16.0 -> (0.65f + (value - 8.0) / 8.0 * 0.20f).toFloat()
                            else -> (0.85f + (value - 16.0) / 14.0 * 0.15f).coerceIn(0.85, 1.0).toFloat()
                        }
                        val visualRatio = if (fillRatio > 0f) fillRatio.coerceAtLeast(0.04f) else 0f
                        val left = i * step
                        val fillH = chartH * visualRatio
                        val fillTop = chartH - fillH

                        // Gray frame
                        drawRoundRect(
                            color = grayFrame,
                            topLeft = Offset(left, 0f),
                            size = Size(barW, chartH),
                            cornerRadius = corner
                        )

                        // Soft halo + blue fill
                        if (visualRatio > 0f) {
                            val shadowAlpha = (0.08f + 0.14f * visualRatio).coerceIn(0.08f, 0.22f)
                            val shadowColor = PrecipBarShadow.copy(alpha = shadowAlpha)
                            for (s in 1..2) {
                                val expand = s * 0.6f
                                drawRoundRect(
                                    color = shadowColor,
                                    topLeft = Offset(left - expand, fillTop - expand * 0.5f),
                                    size = Size(barW + expand * 2, fillH + expand),
                                    cornerRadius = CornerRadius(barW / 2f + expand)
                                )
                            }

                            val topAlpha = (0.7f + 0.3f * visualRatio).coerceIn(0.7f, 1f)
                            val bottomAlpha = (0.4f + 0.6f * visualRatio).coerceIn(0.4f, 1f)
                            val gradientBrush = Brush.verticalGradient(
                                colors = listOf(
                                    PrecipBarTop.copy(alpha = topAlpha),
                                    PrecipBarBottom.copy(alpha = bottomAlpha)
                                ),
                                startY = fillTop,
                                endY = fillTop + fillH
                            )
                            drawRoundRect(
                                brush = gradientBrush,
                                topLeft = Offset(left, fillTop),
                                size = Size(barW, fillH),
                                cornerRadius = corner
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 时间刻度：优先使用 API 返回的 fxTime（5 分钟区间起始时刻），
                // 避免用本地 now 造成最多 5 分钟的错位；无数据时回退到本地当前时间。
                val firstTime = minutely?.times?.firstOrNull()?.let { fx ->
                    runCatching { OffsetDateTime.parse(fx).toLocalTime() }.getOrNull()
                } ?: LocalTime.now()
                val t0 = firstTime.format(minuteTimeFormat)
                val t1 = firstTime.plusHours(1).format(minuteTimeFormat)
                val t2 = firstTime.plusHours(2).format(minuteTimeFormat)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = t0,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = t1,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = t2,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Sample N data points down to targetBins by averaging groups.
 * Uses alternating bin sizes (2, 3, 2, 3, ...) to handle 120 -> 48.
 */
private fun sampleToBins(data: List<Double>, targetBins: Int): List<Double> {
    if (data.size <= targetBins) return data
    if (targetBins <= 0) return emptyList()

    val result = mutableListOf<Double>()
    val binSize = data.size.toDouble() / targetBins
    var pos = 0.0

    for (i in 0 until targetBins) {
        val start = pos.toInt()
        pos += binSize
        val end = pos.toInt().coerceAtMost(data.size)
        if (start < end) {
            val avg = (start until end).map { data[it] }.average()
            result.add(avg)
        } else {
            result.add(0.0)
        }
    }
    return result
}