package com.ridgecast.weather.util

import androidx.compose.ui.graphics.Color
import com.ridgecast.weather.model.DailyAstro
import com.ridgecast.weather.model.DailyForecast
import com.ridgecast.weather.model.DailyTemperature
import com.ridgecast.weather.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 天空渐变锚点，与 [WeatherUtils.getWeatherGradient] 返回的 5 色一一对应。
 *
 * 非等距：顶部天顶色（最深的 color[0]）占据上约 40%，压住「天顶」层次，
 * 之后向地平线（最亮的 color[4]）逐步加速提亮——更接近真实天空由深到浅的过渡，
 * 而非把 5 色机械均分在 0/0.25/0.5/0.75/1.0。
 */
val SkyGradientStops: List<Float> = listOf(0.0f, 0.40f, 0.65f, 0.85f, 1.0f)

/**
 * 将天空渐变色与 [SkyGradientStops] 锚点组合为 Brush.verticalGradient 所需的颜色停靠对。
 * 调用方直接传入：Brush.verticalGradient(colorStops = skyGradientColorStops(colors), ...)。
 *
 * 颜色数与锚点数不一致时退回**等距**分布：直接 zip 会静默截断，
 * 把后面的颜色挤在同一个锚点上（例如 4 色配 5 锚点 → 第 4、5 个锚点共用第 4 色），
 * 造成色阶错位，而不是「只是不够非等距」。
 */
fun skyGradientColorStops(colors: List<Color>): Array<Pair<Float, Color>> {
    if (colors.isEmpty()) return arrayOf(0f to Color.Transparent)
    if (colors.size == SkyGradientStops.size) {
        return colors.zip(SkyGradientStops) { color, stop -> stop to color }.toTypedArray()
    }
    return Array(colors.size) { index ->
        val stop = if (colors.size == 1) 0f else index.toFloat() / (colors.size - 1)
        stop to colors[index]
    }
}

object WeatherUtils {

    private val hourFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
    }
    private val dateFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    /**
     * 正则一律提升为文件级常量（不可变、线程安全、编译一次）。
     *
     * 历史问题：`parseTimeMinutes` / `extractHour` 把 `Regex(...)` 写在函数体内，
     * 逐小时预报的 Canvas 绘制循环里每帧、每个可见格都要调用 → 每帧约 570 次正则编译，
     * 成为滚动掉帧的主因之一。
     */
    private val timeOfDayRegex = Regex("(\\d{1,2}):(\\d{2})")
    private val hourAfterDateSeparatorRegex = Regex("[T ](\\d{1,2}):")
    private val leadingHourRegex = Regex("^(\\d{1,2}):")

    data class WeatherInfo(
        val description: String,
        val icon: String,
        val isDay: Boolean = true
    )

    fun getWeatherTheme(skycon: String?, isDay: Boolean): WeatherTheme {
        val background = getWeatherGradient(skycon, isDay)
        val precipitationIconColor = getPrecipitationIconColor(skycon, isDay)

        // --- 玻璃卡片配色：天气色相派生 ---
        // 取天气渐变中段色作为「天气色相」，先做饱和度拉伸去灰（阴天不再灰蒙蒙），
        // 白天非雨雪向白色提亮 55%（清透有蓝天感），雨雪与夜晚向黑色压暗 55%。
        // 卡片因此保留天气色彩个性，且无需逐天气手工调色；文字恒白可读。
        val hue = background[background.size / 2]
        // 降水判定统一走天气族：雷阵雨/雨夹雪/冰雹同样必须压暗，
        // 否则会落进「白天非降水 → 浅色玻璃」分支，浅色玻璃压在暗背景上不可读。
        val precipOrSnow = classifyWeather(skycon).isPrecipitating
        val glass = if (isDay && !precipOrSnow) {
            val light = lerpColor(saturateColor(hue, 1.35f), Color.White, 0.35f)
            GlassColors(
                tint = light.copy(alpha = 0.22f),
                tintFallback = light.copy(alpha = 0.35f),
                isLight = true
            )
        } else {
            val dark = lerpColor(saturateColor(hue, 1.2f), Color.Black, 0.50f)
            GlassColors(
                tint = dark.copy(alpha = 0.22f),
                tintFallback = dark.copy(alpha = 0.35f),
                isLight = false
            )
        }

        // --- Chart Colors ---
        val chartColors = if (isDay) {
            WeatherChartColors(
                clear = Color(0xFFFFF9C4).copy(alpha = 0.85f) to Color(0xFFFFF9C4).copy(alpha = 0.20f),
                partlyCloudy = Color(0xFFFFF4B8).copy(alpha = 0.72f) to Color(0xFFFFF4B8).copy(alpha = 0.14f),
                cloudy = Color(0xFFB0CCE0).copy(alpha = 0.70f) to Color(0xFFB0CCE0).copy(alpha = 0.10f),
                rain = Color(0xFF4FC3F7).copy(alpha = 0.70f) to Color(0xFF4FC3F7).copy(alpha = 0.15f),
                snow = Color(0xFFFFFFFF).copy(alpha = 0.85f) to Color(0xFFFFFFFF).copy(alpha = 0.20f),
                wind = Color(0xFF4DB6AC).copy(alpha = 0.65f) to Color(0xFF4DB6AC).copy(alpha = 0.10f),
                haze = Color(0xFFBCAAA4).copy(alpha = 0.65f) to Color(0xFFBCAAA4).copy(alpha = 0.10f),
                storm = Color(0xFF5C6BC0).copy(alpha = 0.75f) to Color(0xFF5C6BC0).copy(alpha = 0.15f)
            )
        } else {
            WeatherChartColors(
                clear = Color(0xFFFFFDE7).copy(alpha = 0.25f) to Color(0xFFFFF9C4).copy(alpha = 0.15f),
                partlyCloudy = Color(0xFFFFF9C4).copy(alpha = 0.22f) to Color(0xFFFFECB3).copy(alpha = 0.12f),
                cloudy = Color(0xFF8898B0).copy(alpha = 0.25f) to Color(0xFF607088).copy(alpha = 0.15f),
                rain = Color(0xFF70A0F0).copy(alpha = 0.30f) to Color(0xFF4070B8).copy(alpha = 0.18f),
                snow = Color(0xFF80B8FF).copy(alpha = 0.32f) to Color(0xFF5090D0).copy(alpha = 0.20f),
                wind = Color(0xFF60C0D0).copy(alpha = 0.28f) to Color(0xFF4090A0).copy(alpha = 0.16f),
                haze = Color(0xFF908878).copy(alpha = 0.25f) to Color(0xFF706858).copy(alpha = 0.15f),
                storm = Color(0xFFB080FF).copy(alpha = 0.35f) to Color(0xFF7040C0).copy(alpha = 0.22f)
            )
        }

        return WeatherTheme(
            isDay = isDay,
            backgroundGradient = background,
            glass = glass,
            chartColors = chartColors,
            precipitationIconColor = precipitationIconColor
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun getPrecipitationIconColor(skycon: String?, isDay: Boolean): Color = Color.White

    fun getWeatherInfo(skycon: String?, isDay: Boolean): WeatherInfo {
        return when (skycon) {
            "CLEAR_DAY", "CLEAR_NIGHT", "CLEAR" -> {
                WeatherInfo("晴", if (isDay) "clear-day" else "clear-night", isDay)
            }
            "PARTLY_CLOUDY_DAY", "PARTLY_CLOUDY_NIGHT", "PARTLY_CLOUDY" -> {
                WeatherInfo("多云", if (isDay) "partly-cloudy-day" else "partly-cloudy-night", isDay)
            }
            "CLOUDY" -> WeatherInfo("阴", if (isDay) "overcast-day" else "overcast-night", isDay)
            "SHOWER_RAIN" -> WeatherInfo("阵雨", if (isDay) "partly-cloudy-day-rain" else "partly-cloudy-night-rain", isDay)
            "SHOWER_SNOW" -> WeatherInfo("阵雪", if (isDay) "partly-cloudy-day-snow" else "partly-cloudy-night-snow", isDay)
            "LIGHT_HAZE" -> WeatherInfo("轻度霾", if (isDay) "haze-day" else "haze-night", isDay)
            "MODERATE_HAZE" -> WeatherInfo("中度霾", if (isDay) "haze-day" else "haze-night", isDay)
            "HEAVY_HAZE" -> WeatherInfo("重度霾", if (isDay) "haze-day" else "haze-night", isDay)
            "LIGHT_RAIN" -> WeatherInfo("小雨", "drizzle", isDay)
            "MODERATE_RAIN" -> WeatherInfo("中雨", "rain", isDay)
            "HEAVY_RAIN" -> WeatherInfo("大雨", "extreme-rain", isDay)
            "STORM_RAIN" -> WeatherInfo("暴雨", "thunderstorms-rain", isDay)
            "FOG" -> WeatherInfo("雾", if (isDay) "fog-day" else "fog-night", isDay)
            "LIGHT_SNOW" -> WeatherInfo("小雪", "snow", isDay)
            "MODERATE_SNOW" -> WeatherInfo("中雪", "snow", isDay)
            "HEAVY_SNOW" -> WeatherInfo("大雪", "extreme-snow", isDay)
            "STORM_SNOW" -> WeatherInfo("暴雪", "extreme-snow", isDay)
            "WIND" -> WeatherInfo("大风", "wind", isDay)
            "DUST" -> WeatherInfo("浮尘", if (isDay) "dust-day" else "dust-night", isDay)
            "SAND" -> WeatherInfo("沙尘", if (isDay) "dust-day" else "dust-night", isDay)
            "SLEET" -> WeatherInfo("雨夹雪", "sleet", isDay)
            "HAIL" -> WeatherInfo("冰雹", "hail", isDay)
            "THUNDER_SHOWER" -> WeatherInfo("雷阵雨", if (isDay) "thunderstorms-day-rain" else "thunderstorms-night-rain", isDay)
            else -> WeatherInfo("未知", if (isDay) "overcast-day" else "overcast-night", isDay)
        }
    }

    fun getWeatherInfo(skycon: String?, hour: Int = 12): WeatherInfo {
        val isDay = when (skycon) {
            "CLEAR_NIGHT", "PARTLY_CLOUDY_NIGHT" -> false
            "CLEAR_DAY", "PARTLY_CLOUDY_DAY" -> true
            else -> hour in 6 until 18
        }
        return getWeatherInfo(skycon, isDay)
    }

    /**
     * 天空渐变调色板。
     *
     * 归类统一走 [classifyWeather]，不再在本处另写关键词链——
     * 历史上 `THUNDER_SHOWER` / `SLEET` / `HAIL` 因不匹配任何关键词而落到
     * `else -> 晴天蓝`，出现「闪电配晴空」的错配。
     *
     * 族 → 调色板映射：
     * - Clear / Unknown → 晴
     * - PartlyCloudy → 多云
     * - Cloudy → 阴
     * - Thunder → 雷暴（独立配色：比普通雨更暗更沉）
     * - Rain / Hail → 雨
     * - Snow / Sleet → 雪（雨夹雪本质是低温降水）
     * - Haze → 霾雾尘
     * - Wind → 大风
     */
    fun getWeatherGradient(skycon: String?, isDay: Boolean = true): List<Color> {
        return when (classifyWeather(skycon)) {
            WeatherFamily.Clear, WeatherFamily.Unknown ->
                if (isDay) SunnyGradient else SunnyNightGradient
            WeatherFamily.PartlyCloudy ->
                if (isDay) PartialCloudGradient else PartialCloudNightGradient
            WeatherFamily.Cloudy ->
                if (isDay) CloudyGradient else CloudyNightGradient
            WeatherFamily.Thunder ->
                if (isDay) ThunderGradient else ThunderNightGradient
            WeatherFamily.Rain, WeatherFamily.Hail ->
                if (isDay) RainyGradient else RainyNightGradient
            WeatherFamily.Snow, WeatherFamily.Sleet ->
                if (isDay) SnowyGradient else SnowyNightGradient
            WeatherFamily.Haze ->
                if (isDay) HazeGradient else HazeNightGradient
            WeatherFamily.Wind ->
                if (isDay) WindyGradient else WindyNightGradient
        }
    }

    fun formatTemperature(temp: Double?): String {
        if (temp == null) return "--"
        return "${kotlin.math.round(temp).toInt()}°"
    }

    fun isBrightBackground(skycon: String?, isDay: Boolean = isCurrentlyDay()): Boolean {
        if (!isDay) return false
        return skycon == null ||
               skycon.contains("CLEAR") ||
               skycon.contains("PARTLY_CLOUDY") ||
               skycon.contains("CLOUDY")
    }

    fun getTemperatureColor(temp: Double?): Color {
        if (temp == null) return Color.White
        val t = temp.toFloat()
        return when {
            t < -10f -> Color(0xFF90CAF9) // Very cold
            t < 0f -> lerpColor(Color(0xFF90CAF9), Color(0xFF64B5F6), (t + 10f) / 10f)
            t < 10f -> lerpColor(Color(0xFF64B5F6), Color(0xFF4FC3F7), t / 10f)
            t < 20f -> lerpColor(Color(0xFF4FC3F7), Color(0xFFFFD54F), (t - 10f) / 10f)
            t < 30f -> lerpColor(Color(0xFFFFD54F), Color(0xFFFFB74D), (t - 20f) / 10f)
            t < 40f -> lerpColor(Color(0xFFFFB74D), Color(0xFFEF5350), (t - 30f) / 10f)
            else -> Color(0xFFEF5350) // Very hot
        }
    }

    private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
        val f = fraction.coerceIn(0f, 1f)
        return Color(
            red = start.red + (end.red - start.red) * f,
            green = start.green + (end.green - start.green) * f,
            blue = start.blue + (end.blue - start.blue) * f,
            alpha = start.alpha + (end.alpha - start.alpha) * f
        )
    }

    /** 饱和度拉伸：以明度中灰为支点向外扩展，factor>1 更鲜艳（去灰），<1 更灰 */
    private fun saturateColor(color: Color, factor: Float): Color {
        val gray = (color.red + color.green + color.blue) / 3f
        fun ch(c: Float) = (gray + (c - gray) * factor).coerceIn(0f, 1f)
        return Color(
            red = ch(color.red),
            green = ch(color.green),
            blue = ch(color.blue),
            alpha = color.alpha
        )
    }

    fun formatHumidity(humidity: Double?): String {
        if (humidity == null) return "--"
        return "${(humidity * 100).toInt()}%"
    }

    fun formatWindSpeed(speed: Double?): String {
        if (speed == null) return "--"
        if (speed < 1) return "0"
        val s = kotlin.math.ceil(speed).toInt()
        return when {
            s < 6 -> "1"
            s < 12 -> "2"
            s < 20 -> "3"
            s < 29 -> "4"
            s < 39 -> "5"
            s < 50 -> "6"
            s < 62 -> "7"
            s < 75 -> "8"
            s < 89 -> "9"
            s < 103 -> "10"
            s < 118 -> "11"
            s < 134 -> "12"
            s < 150 -> "13"
            s < 167 -> "14"
            s < 184 -> "15"
            s < 202 -> "16"
            else -> "17"
        }
    }

    /**
     * 风力等级统一取值：**优先使用和风官方蒲福风级**（v1 `wind.scale`），
     * 官方缺失时回退到本地按 km/h 换算，保证任何数据源（含 mock）都能正常显示。
     *
     * @return 数字等级字符串，取不到时为 "--"
     */
    fun windLevelLabel(scale: Int?, speedKmh: Double?): String {
        if (scale != null && scale >= 0) return scale.toString()
        return formatWindSpeed(speedKmh)
    }

    fun formatWindDirection(direction: Double?): String {
        if (direction == null) return ""
        return when {
            direction < 22.5 || direction >= 337.5 -> "北风"
            direction < 67.5 -> "东北风"
            direction < 112.5 -> "东风"
            direction < 157.5 -> "东南风"
            direction < 202.5 -> "南风"
            direction < 247.5 -> "西南风"
            direction < 292.5 -> "西风"
            else -> "西北风"
        }
    }

    fun formatPressure(pressure: Double?): String {
        if (pressure == null) return "--"
        return "${(pressure / 100).toInt()} 百帕"
    }

    fun formatVisibility(visibility: Double?): String {
        if (visibility == null) return "--"
        return if (visibility >= 1000) {
            "${"%.1f".format(visibility / 1000)} km"
        } else {
            "${visibility.toInt()} m"
        }
    }

    fun formatHourShort(datetime: String?): String {
        if (datetime == null) return ""
        return try {
            val date = hourFormat.get()!!.parse(datetime) ?: return ""
            val cal = Calendar.getInstance()
            cal.time = date
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            "${hour}:00"
        } catch (e: Exception) {
            ""
        }
    }

    fun extractHour(datetime: String?): Int {
        if (datetime == null) return -1
        val parsed = try {
            val date = hourFormat.get()!!.parse(datetime)
            if (date != null) {
                val cal = Calendar.getInstance()
                cal.time = date
                cal.get(Calendar.HOUR_OF_DAY)
            } else -1
        } catch (_: Exception) {
            -1
        }
        if (parsed in 0..23) return parsed

        // Fallback: regex matching "HH:mm" after 'T' or space, or at start
        val match = hourAfterDateSeparatorRegex.find(datetime) ?: leadingHourRegex.find(datetime)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..23 } ?: -1
    }

    /**
     * 判断逐小时预报中的特定时间点是否属于日间。
     *
     * 规则：
     * 1. 根据当天的日落/日出时间确定（优先匹配 astro 中同日期的记录，无则回退首条有效 astro）。
     * 2. 以该小时整点为判断依据（分钟归零）。
     * 3. 在日出至日落之间为日间版；在日落后为夜间版。
     * 4. 如果日出或日落正好是整点，以即将发生的为准：
     *    - 例如 18:00 日落，处于即将日落/入夜情况，判定为夜间版（[sunrise, sunset) 左闭右开）；
     *    - 6:00 整日出，处于即将日出/天亮情况，判定为日间版（包含 sunrise 点）。
     * 5. 若无有效天文数据，回退至 6:00..17:59 为日间（hour in 6 until 18）。
     */
    fun isHourlyDay(
        datetime: String?,
        astro: List<DailyAstro>?
    ): Boolean {
        val hour = extractHour(datetime)
        if (hour < 0) return true

        val dateText = datetime?.substringBefore("T")?.substringBefore(" ")?.take(10)
        val window = resolveAstroWindow(dateText, astro) ?: return hour in 6 until 18

        val (sunrise, sunset) = window
        // 无有效天文数据 / 日出不早于日落 → 回退固定窗口（语义与历史实现一致）
        if (sunrise >= sunset) return hour in 6 until 18

        val hourMinute = hour * 60
        return hourMinute in sunrise until sunset
    }

    /**
     * 解析某天 astro 的日出/日落（分钟）。
     *
     * 选择规则与历史实现逐字一致：
     * 1. 优先命中 `dateText` 同日期的 astro 记录（即使该记录天文时间无效也照用，交给调用方回退）；
     * 2. 否则回退首条日出与日落均可解析的 astro 记录；
     * 3. 两者都没有 → 返回 null。
     *
     * 性能：历史实现先用 `firstOrNull { 谓词内解析两次 }` 线性查找、命中后又把
     * sunrise/sunset 整体解析一次，且日期未命中时要把 10 天 astro 走满两遍。
     * 现在是**至多一遍**遍历，且每条记录的日出/日落各只解析一次——
     * 该函数在逐小时卡片 Canvas 绘制循环里每帧每格调用，是最热的解析路径。
     */
    private fun resolveAstroWindow(
        dateText: String?,
        astro: List<DailyAstro>?
    ): Pair<Int, Int>? {
        if (astro.isNullOrEmpty()) return null

        val wantedDate = dateText?.takeIf { it.isNotEmpty() }
        if (wantedDate != null) {
            for (item in astro) {
                if (item.date?.take(10) == wantedDate) {
                    val rise = parseTimeMinutes(item.sunrise?.time)
                    val set = parseTimeMinutes(item.sunset?.time)
                    if (rise != null && set != null) return rise to set
                    // 该记录天文时间无效：历史实现同样直接回退固定窗口，不再找其它天
                    return null
                }
            }
        }

        for (item in astro) {
            val rise = parseTimeMinutes(item.sunrise?.time)
            val set = parseTimeMinutes(item.sunset?.time)
            if (rise != null && set != null) return rise to set
        }
        return null
    }

    fun isHourlyDay(
        datetime: String?,
        daily: DailyForecast?
    ): Boolean = isHourlyDay(datetime, daily?.astro)

    fun formatWeekday(dateStr: String?): String {
        return try {
            val date = parseDateOnly(dateStr) ?: return ""
            val cal = Calendar.getInstance()
            val today = Calendar.getInstance()
            cal.time = date

            when {
                isSameDay(cal, today) -> "今天"

                isTomorrow(dateStr) -> "明天"

                else -> {
                    val weekdays = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
                    weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]
                }
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun todayTemperature(daily: DailyForecast?): DailyTemperature? {
        val temperatures = daily?.temperature ?: return null
        return temperatures.firstOrNull { isToday(it.date) } ?: temperatures.firstOrNull()
    }

    fun isToday(dateStr: String?): Boolean = isRelativeDay(dateStr, 0)

    fun isYesterday(dateStr: String?): Boolean = isRelativeDay(dateStr, -1)

    fun isTomorrow(dateStr: String?): Boolean {
        return isRelativeDay(dateStr, 1)
    }

    fun isCurrentlyDay(
        daily: DailyForecast? = null,
        now: Calendar = Calendar.getInstance()
    ): Boolean {
        isDayByAstro(daily?.astro, now)?.let { return it }
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return hour in 6..18
    }

    private fun isDayByAstro(
        astro: List<DailyAstro>?,
        now: Calendar
    ): Boolean? {
        if (astro.isNullOrEmpty()) return null
        val candidate = astro.firstOrNull { isSameDate(it.date, now) }
            ?: astro.firstOrNull { parseTimeMinutes(it.sunrise?.time) != null && parseTimeMinutes(it.sunset?.time) != null }
            ?: return null
        val sunrise = parseTimeMinutes(candidate.sunrise?.time) ?: return null
        val sunset = parseTimeMinutes(candidate.sunset?.time) ?: return null
        if (sunrise >= sunset) return null
        val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return minuteOfDay in sunrise until sunset
    }

    private fun parseTimeMinutes(time: String?): Int? {
        if (time.isNullOrBlank()) return null
        val match = timeOfDayRegex.find(time) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun isSameDate(dateStr: String?, calendar: Calendar): Boolean {
        return try {
            val date = parseDateOnly(dateStr) ?: return false
            val other = Calendar.getInstance()
            other.time = date
            isSameDay(other, calendar)
        } catch (_: Exception) {
            false
        }
    }

    private fun parseDateOnly(dateStr: String?): Date? {
        if (dateStr == null) return null
        val dateText = dateStr.take(10)
        return dateFormat.get()!!.parse(dateText)
    }

    private fun isRelativeDay(dateStr: String?, dayOffset: Int): Boolean {
        return try {
            val date = parseDateOnly(dateStr) ?: return false
            val cal = Calendar.getInstance()
            val target = Calendar.getInstance()
            cal.time = date
            target.add(Calendar.DAY_OF_YEAR, dayOffset)
            isSameDay(cal, target)
        } catch (e: Exception) {
            false
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}
