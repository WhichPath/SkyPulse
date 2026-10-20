package com.ridgecast.weather.util

import com.ridgecast.weather.model.RealtimeWeather
import com.ridgecast.weather.model.WeatherResponse

/**
 * 天气数据**完整性校验层**（v1.0.7）。
 *
 * ## 目的
 * 1. **接口自检**：每次刷新后按统一口径检查响应是否字段齐全，
 *    缺什么一目了然（例："缺失 daily.astro[3].sunrise"）。
 * 2. **模拟数据自检**：离线兜底/示例数据必须与真实接口**同口径**，
 *    用同一套校验器跑一遍即可确认"没有多余的、也没有少的"。
 *    真实接口字段一旦升级，校验器同步升级，模拟数据的缺口会立刻暴露。
 *
 * ## 日志开关
 * 默认**关闭**（不产生任何 IO）。需要排查时打开：
 * ```kotlin
 * WeatherDataValidator.logEnabled = true
 * ```
 * 也可用系统属性启动：`adb shell am start -n ... --es ...` 不适用，
 * 请在调试版本中直接置位，或用 `System.setProperty("skypulse.dataValidation", "1")`。
 */
object WeatherDataValidator {

    /** 校验报告。 */
    data class Report(
        /** 已检查的字段总数。 */
        val checked: Int,
        /** 缺失的必填项路径。 */
        val missing: List<String>,
        /** 缺失的可选项（不影响功能，仅作提示）。 */
        val notes: List<String> = emptyList()
    ) {
        val ok: Boolean get() = missing.isEmpty()

        val summary: String
            get() = if (ok) {
                "数据完整（已校验 $checked 项）" +
                    (if (notes.isEmpty()) "" else "；可选缺失：${notes.joinToString(", ")}")
            } else {
                "缺失 ${missing.size}/$checked 项：${missing.joinToString(", ")}"
            }
    }

    /**
     * 数据校验日志开关（默认关闭）。
     */
    @Volatile
    var logEnabled: Boolean =
        System.getProperty("skypulse.dataValidation") == "1"

    private const val TAG = "DataValidator"

    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------

    fun validate(response: WeatherResponse?): Report {
        val collector = Collector()
        if (response == null) {
            collector.missing("response")
            return collector.report()
        }
        val result = response.result
        if (result == null) {
            collector.missing("result")
            return collector.report()
        }

        validateRealtime(result.realtime, collector)

        // ---- 逐小时 ----
        val hourly = result.hourly
        if (hourly == null) {
            collector.missing("hourly")
        } else {
            collector.need("hourly.status", hourly.status)
            collector.need("hourly.description", hourly.description)
            val series = listOf(
                "hourly.temperature" to hourly.temperature?.size,
                "hourly.skycon" to hourly.skycon?.size,
                "hourly.wind" to hourly.wind?.size,
                "hourly.humidity" to hourly.humidity?.size,
                "hourly.cloudrate" to hourly.cloudrate?.size,
                "hourly.precipitation" to hourly.precipitation?.size,
                "hourly.air_quality.aqi" to hourly.air_quality?.aqi?.size,
                "hourly.life_index.ultraviolet" to hourly.life_index?.ultraviolet?.size
            )
            val sizes = series.map { it.second }
            if (sizes.all { it == null }) {
                collector.missing("hourly.*")
            } else {
                series.forEach { (path, size) -> collector.needSize(path, size) }
                val expected = sizes.filterNotNull().maxOrNull() ?: 0
                if (expected < MIN_HOURLY_ROWS) {
                    collector.missing("hourly.rows(>=${MIN_HOURLY_ROWS})")
                }
                series.forEach { (path, size) ->
                    if (size != null && size != expected) {
                        collector.missing("$path.size(=$size≠$expected)")
                    }
                }
                hourly.temperature?.forEachIndexed { i, v ->
                    collector.need("hourly.temperature[$i].value", v.value)
                    collector.need("hourly.temperature[$i].datetime", v.datetime)
                }
                hourly.skycon?.forEachIndexed { i, v ->
                    collector.need("hourly.skycon[$i].value", v.value)
                }
                hourly.wind?.forEachIndexed { i, w ->
                    collector.need("hourly.wind[$i].speed", w.speed)
                    collector.need("hourly.wind[$i].direction", w.direction)
                    collector.need("hourly.wind[$i].scale", w.scale)
                }
                hourly.air_quality?.aqi?.forEachIndexed { i, a ->
                    collector.need("hourly.air_quality.aqi[$i].value.chn", a.value?.chn)
                    collector.need("hourly.air_quality.aqi[$i].value.usa", a.value?.usa)
                }
                hourly.life_index?.ultraviolet?.forEachIndexed { i, u ->
                    collector.need("hourly.life_index.ultraviolet[$i].index", u.index)
                    collector.need("hourly.life_index.ultraviolet[$i].desc", u.desc)
                }
            }
        }

        // ---- 逐日 ----
        val daily = result.daily
        if (daily == null) {
            collector.missing("daily")
        } else {
            collector.need("daily.status", daily.status)
            val series = listOf(
                "daily.astro" to daily.astro?.size,
                "daily.temperature" to daily.temperature?.size,
                "daily.skycon" to daily.skycon?.size,
                "daily.wind" to daily.wind?.size,
                "daily.precipitation" to daily.precipitation?.size,
                "daily.air_quality.aqi" to daily.air_quality?.aqi?.size,
                "daily.life_index.ultraviolet" to daily.life_index?.ultraviolet?.size
            )
            val sizes = series.map { it.second }
            series.forEach { (path, size) -> collector.needSize(path, size) }
            val expected = sizes.filterNotNull().maxOrNull() ?: 0
            if (expected < MIN_DAILY_ROWS) {
                collector.missing("daily.rows(>=${MIN_DAILY_ROWS})")
            }
            series.forEach { (path, size) ->
                if (size != null && size != expected) {
                    collector.missing("$path.size(=$size≠$expected)")
                }
            }
            daily.astro?.forEachIndexed { i, a ->
                collector.need("daily.astro[$i].date", a.date)
                collector.need("daily.astro[$i].sunrise", a.sunrise?.time)
                collector.need("daily.astro[$i].sunset", a.sunset?.time)
                collector.need("daily.astro[$i].moonrise", a.moonrise?.time)
                collector.need("daily.astro[$i].moonset", a.moonset?.time)
                collector.need("daily.astro[$i].solarNoon", a.solarNoon?.time)
                collector.need("daily.astro[$i].solarMidnight", a.solarMidnight?.time)
                collector.need("daily.astro[$i].civilDawn", a.civilDawn?.time)
                collector.need("daily.astro[$i].civilDusk", a.civilDusk?.time)
                collector.need("daily.astro[$i].astronomicalDawn", a.astronomicalDawn?.time)
                collector.need("daily.astro[$i].astronomicalDusk", a.astronomicalDusk?.time)
                collector.need("daily.astro[$i].nauticalDawn", a.nauticalDawn?.time)
                collector.need("daily.astro[$i].nauticalDusk", a.nauticalDusk?.time)
                collector.need("daily.astro[$i].moonTransit", a.moonTransit?.time)
                collector.need("daily.astro[$i].moonUnderfoot", a.moonUnderfoot?.time)
                collector.need("daily.astro[$i].moonPhase", a.moonPhase)
            }
            daily.temperature?.forEachIndexed { i, t ->
                collector.need("daily.temperature[$i].max", t.max)
                collector.need("daily.temperature[$i].min", t.min)
                collector.need("daily.temperature[$i].avg", t.avg)
            }
            daily.skycon?.forEachIndexed { i, s ->
                collector.need("daily.skycon[$i].value", s.value)
            }
            daily.wind?.forEachIndexed { i, w ->
                collector.need("daily.wind[$i].max.speed", w.max?.speed)
                collector.need("daily.wind[$i].max.direction", w.max?.direction)
                collector.need("daily.wind[$i].max.scale", w.max?.scale)
                collector.need("daily.wind[$i].min.speed", w.min?.speed)
                collector.need("daily.wind[$i].avg.speed", w.avg?.speed)
            }
            daily.precipitation?.forEachIndexed { i, p ->
                collector.need("daily.precipitation[$i].max", p.max)
                collector.need("daily.precipitation[$i].min", p.min)
                collector.need("daily.precipitation[$i].avg", p.avg)
            }
            daily.air_quality?.aqi?.forEachIndexed { i, a ->
                collector.need("daily.air_quality.aqi[$i].max.chn", a.max?.chn)
                collector.need("daily.air_quality.aqi[$i].avg.usa", a.avg?.usa)
            }
        }

        // ---- 分钟级降水 ----
        val minutely = result.minutely
        if (minutely == null) {
            collector.note("minutely")
        } else {
            collector.need("minutely.status", minutely.status)
            collector.need("minutely.description", minutely.description)
            collector.needSize("minutely.precipitation", minutely.precipitation?.size)
        }

        // ---- 预警 ----
        val alert = result.alert
        if (alert == null) {
            collector.note("alert")
        } else {
            collector.need("alert.status", alert.status)
        }

        // ---- 天文快照（可选：接口失败时由本地算法兜底） ----
        if (result.astronomy == null) {
            collector.note("astronomy")
        } else {
            val astronomy = result.astronomy
            collector.need("astronomy.solarElevationDegrees", astronomy.solarElevationDegrees)
            collector.need("astronomy.solarAzimuthDegrees", astronomy.solarAzimuthDegrees)
            collector.need("astronomy.moonIllumination", astronomy.moonIllumination)
        }

        return collector.report()
    }

    private fun validateRealtime(
        realtime: RealtimeWeather?,
        collector: Collector
    ) {
        if (realtime == null) {
            collector.missing("realtime")
            return
        }
        collector.need("realtime.status", realtime.status)
        collector.need("realtime.temperature", realtime.temperature)
        collector.need("realtime.apparent_temperature", realtime.apparent_temperature)
        collector.need("realtime.humidity", realtime.humidity)
        collector.need("realtime.cloudrate", realtime.cloudrate)
        collector.need("realtime.skycon", realtime.skycon)
        collector.need("realtime.visibility", realtime.visibility)
        collector.need("realtime.pressure", realtime.pressure)
        collector.need("realtime.wind.speed", realtime.wind?.speed)
        collector.need("realtime.wind.direction", realtime.wind?.direction)
        collector.need("realtime.wind.gust", realtime.wind?.gust)
        collector.need("realtime.wind.scale", realtime.wind?.scale)
        collector.need("realtime.precipitation.local.intensity", realtime.precipitation?.local?.intensity)
        collector.need("realtime.air_quality.aqi.chn", realtime.air_quality?.aqi?.chn)
        collector.need("realtime.air_quality.aqi.usa", realtime.air_quality?.aqi?.usa)
        collector.need("realtime.air_quality.description.chn", realtime.air_quality?.description?.chn)
        collector.need("realtime.air_quality.pm25", realtime.air_quality?.pm25)
        collector.need("realtime.air_quality.pm10", realtime.air_quality?.pm10)
        collector.need("realtime.life_index.ultraviolet.index", realtime.life_index?.ultraviolet?.index)
        collector.need("realtime.life_index.comfort.index", realtime.life_index?.comfort?.index)
    }

    // ------------------------------------------------------------------
    // 日志
    // ------------------------------------------------------------------

    /**
     * 开关打开时把校验结果写进文件日志；关闭时**完全无开销**。
     */
    fun logIfEnabled(source: String, response: WeatherResponse?) {
        if (!logEnabled) return
        val report = validate(response)
        if (report.ok) {
            FileLogger.i(TAG, "[$source] ${report.summary}")
        } else {
            FileLogger.w(TAG, "[$source] ${report.summary}")
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private const val MIN_HOURLY_ROWS = 24
    private const val MIN_DAILY_ROWS = 7

    private class Collector {
        private val missingItems = mutableListOf<String>()
        private val noteItems = mutableListOf<String>()
        private var checkedCount = 0

        fun missing(path: String) {
            checkedCount++
            missingItems += path
        }

        fun note(path: String) {
            noteItems += path
        }

        fun need(path: String, value: Any?) {
            checkedCount++
            if (value == null) missingItems += path
        }

        fun needSize(path: String, size: Int?) {
            checkedCount++
            if (size == null || size <= 0) missingItems += path
        }

        fun report() = Report(checkedCount, missingItems.toList(), noteItems.toList())
    }
}
