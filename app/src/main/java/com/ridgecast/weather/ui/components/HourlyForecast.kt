package com.ridgecast.weather.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import com.ridgecast.weather.ui.theme.SkyPulseDesignSystem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridgecast.weather.model.DailyAstro
import com.ridgecast.weather.model.HourlyForecast
import com.ridgecast.weather.model.HourlyWind
import com.ridgecast.weather.ui.theme.*
import com.ridgecast.weather.ui.screen.LocalSkipCardAnimation
import com.ridgecast.weather.util.WeatherFamily
import com.ridgecast.weather.util.WeatherUtils
import com.ridgecast.weather.util.resolvedHourlyAqi
import com.ridgecast.weather.util.classifyWeather
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HOUR_WIDTH = 64
private val SIDE_PADDING = 12

/** 滑出该小时数后浮出「回到现在」按钮 */
private const val BACK_TO_NOW_THRESHOLD_HOURS = 72

/** 超过该滚动速度（px/s）即视为快速滑动，渲染降级为占位 */
private const val FAST_SCROLL_PX_PER_SEC = 2200f

/** 停止滚动多久后恢复完整渲染（ms） */
private const val FAST_SCROLL_SETTLE_MS = 140L

// ============ Hourly Parameter Colors (低饱和度，兼容毛玻璃主题) ============
private object HourlyParamColors {
    // AQI — 两种绿色：亮色背景用深绿，暗色背景用浅绿
    val AqiExcellentBright = Color(0xFF43A047)  // 优 亮色背景（晴/多云白天）
    val AqiExcellentDark = Color(0xFF81C784)    // 优 暗色背景（阴/雨/夜）
    val AqiGood = Color(0xFFFFD54F)        // 良 51-100 暖金
    val AqiLight = Color(0xFFFFB74D)       // 轻度 101-150 柔橙
    val AqiModerate = Color(0xFFE57373)    // 中度 151-200 柔红
    val AqiHeavy = Color(0xFFCE93D8)       // 重度 201-300 柔紫
    val AqiSevere = Color(0xFFA1887F)      // 严重 300+ 棕灰

    // UV
    val UvWeakBright = Color(0xFF43A047)   // 无/很弱 0-2 亮色背景（与空气优一致）
    val UvWeakDark = Color(0xFF81C784)     // 无/很弱 0-2 暗色背景（与空气优一致）
    val UvLow = Color(0xFFFFD54F)          // 弱 3-4 暖金
    val UvMedium = Color(0xFFFFB74D)       // 中等 5-6 柔橙
    val UvStrong = Color(0xFFEF9A9A)       // 强 7-9 柔粉红
    val UvVeryStrong = Color(0xFFCE93D8)   // 极强 10+ 柔紫

}

private fun aqiLabel(aqi: Double?): String {
    if (aqi == null) return ""
    val v = aqi.toInt()
    return when {
        v <= 50 -> "$v"
        v <= 100 -> "$v 中等"
        v <= 150 -> "$v 敏感"
        v <= 200 -> "$v 不健康"
        v <= 300 -> "$v 重度"
        else -> "$v 危险"
    }
}

private fun aqiColor(aqi: Double?, isBrightBg: Boolean = true): Color {
    if (aqi == null) return TextDisabled
    val v = aqi.toInt()
    return when {
        v <= 50 -> if (isBrightBg) HourlyParamColors.AqiExcellentBright else HourlyParamColors.AqiExcellentDark
        v <= 100 -> HourlyParamColors.AqiGood
        v <= 150 -> HourlyParamColors.AqiLight
        v <= 200 -> HourlyParamColors.AqiModerate
        v <= 300 -> HourlyParamColors.AqiHeavy
        else -> HourlyParamColors.AqiSevere
    }
}

private fun uvLabel(uvIndex: String?): String {
    if (uvIndex.isNullOrBlank()) return ""
    val v = uvIndex.toIntOrNull() ?: return ""
    return when {
        v <= 0 -> "$v 无"
        v <= 2 -> "$v 很弱"
        v <= 4 -> "$v 弱"
        v <= 6 -> "$v 中等"
        v <= 9 -> "$v 强"
        else -> "$v 极强"
    }
}

private fun uvColor(uvIndex: String?, isBrightBg: Boolean = true): Color {
    if (uvIndex.isNullOrBlank()) return TextDisabled
    val v = uvIndex.toIntOrNull() ?: return TextDisabled
    return when {
        v <= 2 -> if (isBrightBg) HourlyParamColors.UvWeakBright else HourlyParamColors.UvWeakDark
        v <= 4 -> HourlyParamColors.UvLow
        v <= 6 -> HourlyParamColors.UvMedium
        v <= 9 -> HourlyParamColors.UvStrong
        else -> HourlyParamColors.UvVeryStrong
    }
}

/**
 * 气象风向流向箭头 (尖端指向风吹向的方向，气象学标准流向)
 * direction: 气象来向角度 (0°为北风，90°为东风，180°为南风，270°为西风)
 */
@Composable
private fun WindDirectionArrow(
    direction: Double?,
    modifier: Modifier = Modifier,
    color: Color = TextSecondary
) {
    if (direction == null) return
    val rotation = ((direction + 180.0) % 360.0).toFloat()

    Canvas(modifier = modifier.size(9.dp)) {
        val w = size.width
        val h = size.height
        rotate(degrees = rotation, pivot = Offset(w / 2f, h / 2f)) {
            val path = Path().apply {
                moveTo(w / 2f, 0f)          // 箭头尖端向上 (0°)
                lineTo(w * 0.88f, h)        // 右下翼
                lineTo(w / 2f, h * 0.72f)   // 中心内凹
                lineTo(w * 0.12f, h)        // 左下翼
                close()
            }
            drawPath(path = path, color = color)
        }
    }
}

private fun gustLabel(speed: Double?): String {
    if (speed == null) return ""
    val level = WeatherUtils.formatWindSpeed(speed)
    if (level == "--") return ""
    return "阵风${level}级"
}


private val ParamTagShape = SkyPulseDesignSystem.Shapes.extraSmall

@Composable
fun HourlyForecastCard(
    hourly: HourlyForecast?,
    astro: List<DailyAstro>? = null,
    showAqi: Boolean = false,
    showUv: Boolean = true,
    showWind: Boolean = true,
    showWindGust: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (hourly?.temperature.isNullOrEmpty()) return
    val data = hourly ?: return
    data.temperature ?: return

    val skipAnimation = LocalSkipCardAnimation.current
    var visible by remember { mutableStateOf(false) }
    val cardAlpha by animateFloatAsState(
        targetValue = if (skipAnimation || visible) 1f else 0f,
        animationSpec = if (skipAnimation) tween(0) else tween(
            SkyPulseDesignSystem.Motion.cardEnterMillis,
            delayMillis = SkyPulseDesignSystem.Motion.cardEnterDelayMillis
        ),
        label = "card_fade"
    )
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(skipAnimation) { if (skipAnimation) visible = true }

    // v4.1.3：横滑状态提升至卡片层，供「回到现在」按钮与窗口化渲染共享
    val scrollState = rememberScrollState()

    GlassCard(
        modifier = modifier.alpha(cardAlpha)
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Text(
                text = "逐小时预报",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            HourlyBackToNowBar(scrollState = scrollState, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(16.dp))

            HourlyTemperatureChart(
                scrollState = scrollState,
                hourlyData = data,
                astro = astro,
                showAqi = showAqi,
                showUv = showUv,
                showWind = showWind,
                showWindGust = showWindGust
            )
        }
    }
}

/**
 * 「回到现在」快捷按钮。
 *
 * 逐小时数据已扩展至 10 天（240h），横滑距离很长；当用户滑出 [BACK_TO_NOW_THRESHOLD_HOURS]
 * 之后，于卡片标题下方浮出该按钮，点击可平滑回到最左侧（当前时刻）。
 */
@Composable
private fun HourlyBackToNowBar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = remember(density) {
        with(density) { (BACK_TO_NOW_THRESHOLD_HOURS * HOUR_WIDTH).dp.toPx() }
    }
    val show by remember(thresholdPx) {
        derivedStateOf { scrollState.value > thresholdPx }
    }

    AnimatedVisibility(
        visible = show,
        enter = fadeIn(tween(160)) + expandVertically(tween(180)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, end = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable {
                        scope.launch { scrollState.animateScrollTo(0) }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LucideIcon(
                    name = "chevron-left",
                    contentDescription = null,
                    size = 13.dp,
                    tint = TextSecondary
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "回到现在",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * 快速滑动（惯性滚动）检测。
 *
 * 速度超过 [FAST_SCROLL_PX_PER_SEC] 时返回 true，调用方据此降级为低成本占位渲染；
 * 停止滚动约 [FAST_SCROLL_SETTLE_MS] 后恢复 false。
 */
@Composable
private fun rememberFastScrollFlag(scrollState: ScrollState): Boolean {
    var fast by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val holder = remember { ScrollSettleHolder() }

    LaunchedEffect(scrollState) {
        var lastValue = scrollState.value
        var lastNanos = System.nanoTime()
        snapshotFlow { scrollState.value }.collect { value ->
            val now = System.nanoTime()
            val dtMs = (now - lastNanos) / 1_000_000f
            val velocity = if (dtMs > 0.5f) kotlin.math.abs(value - lastValue) / dtMs * 1000f else 0f
            lastValue = value
            lastNanos = now
            if (velocity > FAST_SCROLL_PX_PER_SEC) {
                if (!fast) fast = true
                // 已有恢复协程在跑时不重复启动：避免每个滚动帧都取消/重建协程
                if (holder.job?.isActive != true) {
                    holder.job = scope.launch {
                        delay(FAST_SCROLL_SETTLE_MS)
                        fast = false
                    }
                }
            }
        }
    }
    return fast
}

private class ScrollSettleHolder {
    var job: kotlinx.coroutines.Job? = null
}

/** 逐小时横向窗口（数据类，结构相等性用于阻断无效重组）。 */
private data class HourlyWindow(val start: Int, val end: Int)

/** 依据滚动偏移计算需要构建的条目区间（视口 ±2 格缓冲）。 */
private fun computeHourlyWindow(
    scrollOffsetPx: Float,
    viewportPx: Float,
    itemWidthPx: Float,
    sidePadPx: Float,
    itemCount: Int
): HourlyWindow {
    val firstVisible = ((scrollOffsetPx - sidePadPx) / itemWidthPx).toInt().coerceAtLeast(0)
    val visibleCount = kotlin.math.ceil((viewportPx + itemWidthPx * 4f) / itemWidthPx).toInt()
    return HourlyWindow(
        start = (firstVisible - 2).coerceAtLeast(0),
        end = (firstVisible + visibleCount + 2).coerceAtMost(itemCount)
    )
}

/** 窗口化渲染时的单元格占位（与 LoadingShimmer 同一视觉语言）。 */
@Composable
private fun HourlyCellPlaceholder(width: Dp, height: Dp = 16.dp) {
    Box(
        modifier = Modifier.width(width).height(height),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(height * 0.7f)
                .clip(ParamTagShape)
                .background(Color.White.copy(alpha = 0.12f))
        )
    }
}

@Composable
private fun HourlyTemperatureChart(
    scrollState: ScrollState,
    hourlyData: HourlyForecast,
    astro: List<DailyAstro>? = null,
    showAqi: Boolean = false,
    showUv: Boolean = true,
    showWind: Boolean = true,
    showWindGust: Boolean = true
) {
    val showAnyParam = showUv || showWind || showWindGust
    val temperatures = hourlyData.temperature?.filter { it.value != null } ?: return
    if (temperatures.size < 2) return
    val itemCount = temperatures.size
    val skycons = hourlyData.skycon
    val precipitation = hourlyData.precipitation
    val winds = hourlyData.wind
    val gustValues = hourlyData.gust
    val uvItems = hourlyData.life_index?.ultraviolet
    // 逐时 AQI：数据源给了就用，没给则按逐时分项浓度本地自算（US EPA 口径）
    val aqiValues = remember(hourlyData.air_quality) {
        hourlyData.air_quality?.resolvedHourlyAqi().orEmpty()
    }

    val theme = LocalWeatherTheme.current
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val tempValues = temperatures.map { kotlin.math.round(it.value!!) }

    val minTemp = tempValues.min()
    val maxTemp = tempValues.max()
    val rawRange = maxTemp - minTemp
    val padding = (rawRange * 0.15).coerceAtLeast(1.0)
    val paddedMin = minTemp - padding
    val paddedMax = maxTemp + padding
    val tempRange = (paddedMax - paddedMin).coerceAtLeast(1.0)

    val probValues = precipitation?.map { it.probability ?: 0.0 } ?: List(itemCount) { 0.0 }
    val skyconValues = skycons?.map { it.value }?.take(itemCount) ?: List(itemCount) { null }

    // 预计算每小时的昼夜标记与天气描述：仅在数据变化时计算一次，
    // 避免 Canvas 绘制循环与图标行逐格重复解析日期字符串、线性扫描 astro 列表
    val isDayFlags = remember(skycons, skyconValues, astro) {
        IntArray(itemCount) { i ->
            if (WeatherUtils.isHourlyDay(skycons?.getOrNull(i)?.datetime, astro)) 1 else 0
        }
    }
    val weatherInfos = remember(skycons, skyconValues, astro) {
        List(itemCount) { i ->
            WeatherUtils.getWeatherInfo(skyconValues.getOrNull(i), isDayFlags[i] == 1)
        }
    }

    // 标签位于玻璃卡片之上，对比度随卡片玻璃明暗自适应（亮玻璃用深色文字色，深玻璃用浅色文字色）
    val isBrightBg = theme.glass.isLight

    val itemWidthDp = HOUR_WIDTH.dp
    val sidePad = SIDE_PADDING.dp
    val totalWidth = (itemCount * HOUR_WIDTH).dp + sidePad * 2
    val chartHeight = 120.dp

    // ---------- 几何预计算（组合期 remember，不随滚动窗口重建）----------
    // 曲线几何与颜色表只依赖「数据 + 密度 + 固定尺寸」，与滚动窗口无关。
    // 提升到组合期后，每滚动一步不再重建 240 个 Offset + 2 个 FloatArray + 240 次字符串扫描。
    val chartStepPx = with(density) { itemWidthDp.toPx() }
    val curveTopPx = with(density) { 20.dp.toPx() }
    val curveBottomPx = with(density) { chartHeight.toPx() } * 0.70f
    val halfStepPx = chartStepPx / 2f

    val splinePoints = remember(
        tempValues, paddedMin, tempRange, chartStepPx, curveTopPx, curveBottomPx, itemCount
    ) {
        (0 until itemCount).map { i ->
            val x = halfStepPx + i * chartStepPx
            val normalizedY = ((tempValues[i] - paddedMin) / tempRange).toFloat()
            val y = curveBottomPx - normalizedY * (curveBottomPx - curveTopPx)
            androidx.compose.ui.geometry.Offset(x, y.coerceIn(curveTopPx, curveBottomPx))
        }
    }

    val splineTangents = remember(splinePoints, itemCount) {
        if (itemCount < 2) {
            FloatArray(itemCount)
        } else {
            val seg = FloatArray(itemCount - 1)
            for (i in 0 until itemCount - 1) {
                seg[i] = (splinePoints[i + 1].y - splinePoints[i].y) / (splinePoints[i + 1].x - splinePoints[i].x)
            }
            val tan = FloatArray(itemCount)
            for (i in 0 until itemCount) {
                tan[i] = when {
                    i == 0 -> seg[0]
                    i == itemCount - 1 -> seg[itemCount - 2]
                    else -> {
                        val s0 = seg[i - 1]
                        val s1 = seg[i]
                        if (s0 * s1 <= 0f) 0f else (s0 + s1) / 2f
                    }
                }
            }
            tan
        }
    }

    val barColorPairs = remember(skyconValues, theme) {
        val chartColors = theme.chartColors
        skyconValues.map { skycon ->
            when {
                // 缺失天气现象时不要伪造晴天色，否则连续未知时段会变成整块黄色
                skycon == null -> Color.Transparent to Color.Transparent
                // 暴雨/暴雪（STORM_*）沿用雷暴靛蓝色，保持既有观感
                skycon.contains("STORM") -> chartColors.storm
                else -> when (classifyWeather(skycon)) {
                    // 雷阵雨原来会落到 else 变成全透明，柱体整个不可见
                    WeatherFamily.Thunder -> chartColors.storm
                    WeatherFamily.Rain,
                    WeatherFamily.Snow,
                    WeatherFamily.Sleet,
                    WeatherFamily.Hail -> chartColors.rain
                    WeatherFamily.PartlyCloudy -> chartColors.partlyCloudy
                    WeatherFamily.Cloudy -> chartColors.cloudy
                    WeatherFamily.Haze -> chartColors.haze
                    WeatherFamily.Wind -> chartColors.wind
                    WeatherFamily.Clear -> chartColors.clear
                    // 未识别的天气代码同样不要落成晴天色块
                    WeatherFamily.Unknown -> Color.Transparent to Color.Transparent
                }
            }
        }
    }

    // 降水概率只在降水时段显示（雷阵雨/雨夹雪/冰雹同样属于降水）。
    // 预计算一次，避免在 Canvas 逐帧绘制循环里反复做字符串匹配。
    val isPrecipFlags = remember(skyconValues) {
        skyconValues.map { skycon -> skycon != null && classifyWeather(skycon).isPrecipitating }
    }
    val horizontalEdgeGuard = rememberHorizontalScrollEdgeGuard()

    // 快速滑动时降级为占位渲染，避免一次性构建大量文本与雨丝
    val fastScrolling = false

    val tagWidth = 62.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val itemWidthPx = with(density) { itemWidthDp.toPx() }
        val sidePadPx = with(density) { sidePad.toPx() }
        val viewportPx = with(density) { maxWidth.toPx() }

        // 直接全量渲染全部小时 cell，与逐日面板一致；避免窗口化/快速滑动占位导致的闪烁。
        val window = remember(itemCount) { HourlyWindow(0, itemCount) }
        val windowStart = window.start
        val windowEnd = window.end
        val windowCount = (windowEnd - windowStart).coerceAtLeast(1)

        val windowStartPx = (windowStart * itemWidthPx).toInt()
        val leadingPadPx = if (windowStart == 0) sidePadPx else 0f
        val trailingPadPx = if (windowEnd == itemCount) sidePadPx else 0f
        val windowWidthDp = with(density) {
            (windowCount * itemWidthPx + leadingPadPx + trailingPadPx).toDp()
        }
        val leadingPadDp = with(density) { leadingPadPx.toDp() }
        val trailingPadDp = with(density) { trailingPadPx.toDp() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(horizontalEdgeGuard)
                .disableCityPagerWhilePressed()
                .horizontalScroll(scrollState)
        ) {
            // 仅用于声明横向内容总宽度，使 ScrollState 的 maxValue 正确
            Box(modifier = Modifier.width(totalWidth)) {
                Column(modifier = Modifier.offset { IntOffset(windowStartPx, 0) }) {
                    Canvas(
                        modifier = Modifier
                            .width(windowWidthDp)
                            .height(chartHeight)
                    ) {
                        val canvasH = size.height
                        if (size.width <= 0f || canvasH <= 0f) return@Canvas

                        // 内容坐标系 -> 窗口画布坐标系
                        val translateX = -windowStartPx + leadingPadPx
                        val step = chartStepPx // 全局步长，不随窗口缩放
                        val contentLeftLocal = translateX
                        val contentRightLocal = itemCount * step + sidePadPx + translateX

                        // 几何与颜色表已在组合期预计算，这里直接引用
                        val points = splinePoints
                        val tangents = splineTangents

                        fun findSegmentIndex(x: Float): Int {
                            var lo = 0; var hi = points.size - 2
                            while (lo <= hi) {
                                val mid = (lo + hi) ushr 1
                                if (x < points[mid].x) hi = mid - 1
                                else if (x > points[mid + 1].x) lo = mid + 1
                                else return mid
                            }
                            return lo.coerceIn(0, points.size - 2)
                        }

                        fun sampleSplineY(x: Float): Float {
                            if (points.isEmpty()) return curveBottomPx
                            if (x <= points.first().x) return points.first().y
                            if (x >= points.last().x) return points.last().y
                            val i = findSegmentIndex(x)
                            val p0 = points[i]; val p1 = points[i + 1]
                            val m0 = tangents[i]; val m1 = tangents[i + 1]
                            val t = (x - p0.x) / (p1.x - p0.x)
                            val t2 = t * t; val t3 = t2 * t
                            val h00 = 2 * t3 - 3 * t2 + 1; val h10 = t3 - 2 * t2 + t
                            val h01 = -2 * t3 + 3 * t2; val h11 = t3 - t2
                            val dx = p1.x - p0.x
                            return h00 * p0.y + h10 * m0 * dx + h01 * p1.y + h11 * m1 * dx
                        }

                        translate(translateX, 0f) {
                            // 快速滑动时只画曲线骨架，跳过柱体/雨丝/文字标注
                            if (!fastScrolling) {
                                for (i in windowStart until windowEnd) {
                                    val leftX = if (i == 0) contentLeftLocal else (points[i - 1].x + points[i].x) / 2f
                                    val rightX = if (i == itemCount - 1) contentRightLocal else (points[i].x + points[i + 1].x) / 2f
                                    val (topColor, _) = barColorPairs[i]

                                    val barSteps = ((rightX - leftX) / (2f * this@Canvas.density)).toInt().coerceIn(10, 50)
                                    val sampledYs = FloatArray(barSteps + 1)
                                    for (s in 0..barSteps) {
                                        val x = leftX + (rightX - leftX) * (s.toFloat() / barSteps)
                                        sampledYs[s] = sampleSplineY(x)
                                    }
                                    val barTopY = sampledYs.min()

                                    val barPath = Path().apply {
                                        moveTo(leftX, canvasH)
                                        for (s in 0..barSteps) {
                                            val x = leftX + (rightX - leftX) * (s.toFloat() / barSteps)
                                            lineTo(x, sampledYs[s])
                                        }
                                        lineTo(rightX, canvasH); close()
                                    }
                                    drawPath(path = barPath, brush = Brush.verticalGradient(
                                        colors = listOf(
                                            topColor,
                                            topColor.copy(alpha = topColor.alpha * 0.6f),
                                            topColor.copy(alpha = topColor.alpha * 0.25f),
                                            topColor.copy(alpha = 0f)
                                        ),
                                        startY = barTopY,
                                        endY = canvasH
                                    ))

                                    // 绘制下雨效果 - 三层分布，统一角度
                                    val skycon = skyconValues[i]
                                    val isRain = skycon?.contains("RAIN") == true || skycon?.contains("STORM") == true
                                    if (isRain && skycon != null) {
                                        val rainIntensity = when {
                                            skycon.contains("STORM") -> 4      // 暴雨
                                            skycon.contains("HEAVY_RAIN") -> 3  // 大雨
                                            skycon.contains("MODERATE_RAIN") -> 2 // 中雨
                                            else -> 1                           // 小雨
                                        }
                                        val rainColor = Color.White
                                        val barWidth = rightX - leftX
                                        val rainHeight = (canvasH - barTopY) * 0.5f

                                        if (barWidth > 0f && rainHeight > 0f) {
                                            clipRect(
                                                left = leftX,
                                                top = barTopY,
                                                right = rightX,
                                                bottom = canvasH
                                            ) {
                                                val baseSkewRatio = 0.14f
                                                val layerConfigs = listOf(
                                                    Triple(0.0f, 0.35f, 0.6f),
                                                    Triple(0.15f, 0.5f, 0.45f),
                                                    Triple(0.3f, 0.65f, 0.3f)
                                                )
                                                val totalLines = when (rainIntensity) {
                                                    1 -> (barWidth / 20f).toInt().coerceAtLeast(2)
                                                    2 -> (barWidth / 16f).toInt().coerceAtLeast(3)
                                                    3 -> (barWidth / 12f).toInt().coerceAtLeast(4)
                                                    else -> (barWidth / 9f).toInt().coerceAtLeast(5)
                                                }

                                                for (j in 0 until totalLines) {
                                                    val t = (j + 0.5f) / totalLines
                                                    val layerRandom = kotlin.math.sin(t * 47.3f + j * 13.7f)
                                                    val layerIndex = when {
                                                        layerRandom < -0.3f -> 0
                                                        layerRandom < 0.3f -> 1
                                                        else -> 2
                                                    }
                                                    val (layerOffset, lengthRatio, alphaRatio) = layerConfigs[layerIndex]
                                                    val xOffset = kotlin.math.sin(t * 23.7f + j * 7.3f) * barWidth * 0.08f
                                                    val x = (leftX + barWidth * t + xOffset).coerceIn(leftX, rightX)
                                                    val curveY = sampleSplineY(x)
                                                    val startY = curveY + (rainHeight * layerOffset) + 2.dp.toPx()
                                                    val lengthVariation = 0.8f + 0.4f * kotlin.math.sin(t * 31.3f + j * 11.7f)
                                                    val lineLength = rainHeight * lengthRatio * lengthVariation
                                                    val endY = startY + lineLength
                                                    val angleVariation = kotlin.math.sin(t * 19.7f + j * 5.3f) * 0.03f
                                                    val skew = lineLength * (baseSkewRatio + angleVariation)
                                                    val endX = x + skew
                                                    val bottomAlpha = alphaRatio * (0.85f + 0.15f * kotlin.math.sin(t * 11.3f + j * 3.7f))
                                                    val topAlpha = bottomAlpha * 0.3f
                                                    val strokeWidth = (1.1f + 0.3f * kotlin.math.sin(t * 17.3f + j * 2.3f)).dp.toPx()

                                                    drawLine(
                                                        brush = Brush.verticalGradient(
                                                            colors = listOf(
                                                                rainColor.copy(alpha = topAlpha),
                                                                rainColor.copy(alpha = bottomAlpha)
                                                            ),
                                                            startY = startY,
                                                            endY = endY
                                                        ),
                                                        start = Offset(x, startY),
                                                        end = Offset(endX, endY),
                                                        strokeWidth = strokeWidth,
                                                        cap = StrokeCap.Round
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (points.size >= 2) {
                                val linePath = Path().apply {
                                    moveTo(contentLeftLocal, points.first().y)
                                    lineTo(points.first().x, points.first().y)
                                    for (i in 0 until points.size - 1) {
                                        val p0 = points[i]; val p1 = points[i + 1]
                                        val m0 = tangents[i]; val m1 = tangents[i + 1]; val dx = p1.x - p0.x
                                        cubicTo(p0.x + dx / 3f, p0.y + m0 * dx / 3f, p1.x - dx / 3f, p1.y - m1 * dx / 3f, p1.x, p1.y)
                                    }
                                    lineTo(contentRightLocal, points.last().y)
                                }
                                drawPath(path = linePath, color = Color.White, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Round))
                            }

                            if (!fastScrolling) {
                                for (index in windowStart until windowEnd) {
                                    val point = points[index]
                                    if (index == 0) {
                                        drawCircle(Color.White.copy(alpha = 0.25f), 8.dp.toPx(), point)
                                        drawCircle(Color.White, 4.5.dp.toPx(), point)
                                    } else {
                                        drawCircle(Color.White, 3.5.dp.toPx(), point)
                                    }
                                    val tempText = "${tempValues[index].toInt()}°"
                                    val result = textMeasurer.measure(AnnotatedString(tempText), style = TextStyle(fontSize = 13.sp, color = Color.White))
                                    drawText(result, topLeft = Offset(point.x - result.size.width / 2, point.y - result.size.height - 6.dp.toPx()))
                                }

                                val labelStyle = TextStyle(fontSize = 12.sp, color = TextSecondary)
                                val labelCenterY = curveBottomPx + (canvasH - curveBottomPx) * 0.45f
                                for (i in windowStart until windowEnd) {
                                    // 缺少天气现象的时段不绘制标签（与历史行为一致）
                                    if (skyconValues[i] == null) continue
                                    val leftX = if (i == 0) contentLeftLocal else (points[i - 1].x + points[i].x) / 2f
                                    val rightX = if (i == itemCount - 1) contentRightLocal else (points[i].x + points[i + 1].x) / 2f
                                    val centerX = (leftX + rightX) / 2f
                                    // 昼夜标记与天气描述已在组合期预计算，这里直接索引，不再逐格解析日期
                                    val weatherInfo = weatherInfos[i]
                                    val weatherResult = textMeasurer.measure(AnnotatedString(weatherInfo.description), style = labelStyle)
                                    val isPrecip = isPrecipFlags[i]
                                    val prob = probValues[i]
                                    val probText = if (isPrecip && prob >= 1.0) "${prob.toInt()}%" else ""
                                    val lineSpacing = 2.dp.toPx()
                                    val totalH = if (probText.isNotEmpty()) weatherResult.size.height + lineSpacing + textMeasurer.measure(AnnotatedString(probText), style = labelStyle).size.height else weatherResult.size.height.toFloat()
                                    if (rightX - leftX < weatherResult.size.width * 0.7f) continue
                                    val startY = labelCenterY - totalH / 2f
                                    drawText(weatherResult, topLeft = Offset(centerX - weatherResult.size.width / 2f, startY))
                                    if (probText.isNotEmpty()) {
                                        val probResult = textMeasurer.measure(AnnotatedString(probText), style = labelStyle)
                                        drawText(probResult, topLeft = Offset(centerX - probResult.size.width / 2f, startY + weatherResult.size.height + lineSpacing))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Icons Row
                    Row(
                        modifier = Modifier
                            .width(windowWidthDp)
                            .padding(start = leadingPadDp, end = trailingPadDp)
                    ) {
                        for (i in windowStart until windowEnd) {
                            val skycon = skycons?.getOrNull(i)
                            if (fastScrolling || skycon == null) {
                                HourlyCellPlaceholder(width = itemWidthDp, height = 36.dp)
                            } else {
                                val info = weatherInfos[i]
                                Box(modifier = Modifier.width(itemWidthDp), contentAlignment = Alignment.Center) {
                                    WeatherIcon(iconType = info.icon, size = 36.dp, animated = false)
                                }
                            }
                        }
                    }

                    val showParams = showAqi || showUv || showWind || showWindGust
                    if (showParams) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(7.5.dp)) {
                            // AQI Row
                            if (showAqi) {
                                Row(
                                    modifier = Modifier
                                        .width(windowWidthDp)
                                        .padding(start = leadingPadDp, end = trailingPadDp)
                                ) {
                                    for (index in windowStart until windowEnd) {
                                        if (fastScrolling) {
                                            HourlyCellPlaceholder(width = itemWidthDp)
                                            continue
                                        }
                                        val aqi = aqiValues.getOrNull(index)?.toDouble()
                                        val label = aqiLabel(aqi)
                                        val color = aqiColor(aqi, isBrightBg)
                                        Box(
                                            modifier = Modifier.width(itemWidthDp).height(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (label.isNotEmpty()) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center,
                                                    modifier = Modifier
                                                        .width(tagWidth)
                                                        .clip(ParamTagShape)
                                                        .background(Color.White.copy(alpha = 0.25f))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                                            .background(color)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                                        color = TextSecondary,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // UV Row
                            if (showUv) {
                                Row(
                                    modifier = Modifier
                                        .width(windowWidthDp)
                                        .padding(start = leadingPadDp, end = trailingPadDp)
                                ) {
                                    for (index in windowStart until windowEnd) {
                                        if (fastScrolling) {
                                            HourlyCellPlaceholder(width = itemWidthDp)
                                            continue
                                        }
                                        val uvIndex = uvItems?.getOrNull(index)?.index
                                        val label = uvLabel(uvIndex)
                                        val color = uvColor(uvIndex, isBrightBg)
                                        Box(
                                            modifier = Modifier.width(itemWidthDp).height(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (label.isNotEmpty()) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center,
                                                    modifier = Modifier
                                                        .width(tagWidth)
                                                        .clip(ParamTagShape)
                                                        .background(Color.White.copy(alpha = 0.25f))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                                            .background(color)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                                        color = TextSecondary,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Wind Row
                            if (showWind) {
                                Row(
                                    modifier = Modifier
                                        .width(windowWidthDp)
                                        .padding(start = leadingPadDp, end = trailingPadDp)
                                ) {
                                    for (index in windowStart until windowEnd) {
                                        if (fastScrolling) {
                                            HourlyCellPlaceholder(width = itemWidthDp)
                                            continue
                                        }
                                        val wind = winds?.getOrNull(index)
                                        val level = WeatherUtils.windLevelLabel(wind?.scale, wind?.speed)
                                        Box(
                                            modifier = Modifier.width(itemWidthDp).height(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (level != "--") {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center,
                                                    modifier = Modifier
                                                        .width(tagWidth)
                                                        .clip(ParamTagShape)
                                                        .background(Color.White.copy(alpha = 0.25f))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    if (wind?.direction != null && level != "0") {
                                                        WindDirectionArrow(
                                                            direction = wind.direction,
                                                            modifier = Modifier.size(9.dp),
                                                            color = TextSecondary
                                                        )
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                    }
                                                    Text(
                                                        text = "${level}级",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                                        color = TextSecondary,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Gust Row
                            if (showWindGust) {
                                Row(
                                    modifier = Modifier
                                        .width(windowWidthDp)
                                        .padding(start = leadingPadDp, end = trailingPadDp)
                                ) {
                                    for (index in windowStart until windowEnd) {
                                        if (fastScrolling) {
                                            HourlyCellPlaceholder(width = itemWidthDp)
                                            continue
                                        }
                                        val gustSpeed = gustValues?.getOrNull(index)?.value
                                        val label = gustLabel(gustSpeed)
                                        Box(
                                            modifier = Modifier.width(itemWidthDp).height(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (label.isNotEmpty()) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                    color = TextSecondary,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    modifier = Modifier
                                                        .width(tagWidth)
                                                        .clip(ParamTagShape)
                                                        .background(Color.White.copy(alpha = 0.25f))
                                                        .padding(vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Time Row
                    Row(
                        modifier = Modifier
                            .width(windowWidthDp)
                            .padding(start = leadingPadDp, end = trailingPadDp)
                    ) {
                        for (index in windowStart until windowEnd) {
                            Box(modifier = Modifier.width(itemWidthDp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = hourlyTimeLabel(temperatures, index),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 逐小时时间轴标签：
 * 首格为「现在」；之后每小时显示时刻；每天 0 点切换为日期标记
 * （明天 / 后天 / M月d日），便于在长达 10 天的横滑过程中定位。
 */
private fun hourlyTimeLabel(temps: List<com.ridgecast.weather.model.HourlyValue>, index: Int): String {
    if (index == 0) return "现在"
    val datetime = temps[index].datetime
    val hour = WeatherUtils.extractHour(datetime)
    if (hour != 0) return WeatherUtils.formatHourShort(datetime)

    val firstDate = temps.firstOrNull { !it.datetime.isNullOrBlank() }?.datetime?.substringBefore("T")
    val date = datetime?.substringBefore("T")
    if (firstDate.isNullOrBlank() || date.isNullOrBlank()) return "明天"

    val dayOffset = runCatching {
        java.time.LocalDate.parse(date).toEpochDay() - java.time.LocalDate.parse(firstDate).toEpochDay()
    }.getOrNull() ?: return "明天"

    return when (dayOffset) {
        1L -> "明天"
        2L -> "后天"
        else -> runCatching {
            val d = java.time.LocalDate.parse(date)
            "${d.monthValue}月${d.dayOfMonth}日"
        }.getOrDefault("明天")
    }
}
