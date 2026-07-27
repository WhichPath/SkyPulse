package com.skypulse.weather.data.remote.qweather

import com.skypulse.weather.model.Alert
import com.skypulse.weather.model.AlertContent
import com.skypulse.weather.model.AirQuality
import com.skypulse.weather.model.AirQualityDescription
import com.skypulse.weather.model.AirQualityIndex
import com.skypulse.weather.model.AstroTime
import com.skypulse.weather.model.DailyAstro
import com.skypulse.weather.model.DailyForecast
import com.skypulse.weather.model.DailyLifeIndex
import com.skypulse.weather.model.DailyPrecipitation
import com.skypulse.weather.model.DailySkycon
import com.skypulse.weather.model.DailyTemperature
import com.skypulse.weather.model.DailyValue
import com.skypulse.weather.model.DailyWind
import com.skypulse.weather.model.HourlyForecast
import com.skypulse.weather.model.HourlySkycon
import com.skypulse.weather.model.HourlyValue
import com.skypulse.weather.model.HourlyWind
import com.skypulse.weather.model.LifeIndexDay
import com.skypulse.weather.model.MinutelyForecast
import com.skypulse.weather.model.Precipitation
import com.skypulse.weather.model.PrecipitationLocal
import com.skypulse.weather.model.RealtimeWeather
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.model.WeatherResult
import com.skypulse.weather.model.Wind
import com.skypulse.weather.model.sortedByPublishTimeDescending
import java.util.TimeZone

/**
 * 和风天气响应 -> 现有 WeatherResponse (彩云格式) 映射器。
 *
 * 保留彩云的 skycon 字符串体系作为 UI 层的内部抽象，
 * 和风的 icon 天气码在此处统一转换为 skycon，UI 层无需任何改动。
 *
 * 单位转换规则（和风 -> 彩云）：
 * - 湿度：百分比(0-100) -> 小数(0-1)，除以 100
 * - 云量：百分比(0-100) -> 小数(0-1)，除以 100
 * - 气压：hPa -> Pa，乘以 100
 * - 能见度：km -> m，乘以 1000
 * - 风速：km/h -> km/h，无需转换
 * - 温度：°C -> °C，无需转换
 */
object QWeatherMapper {

    // ============ 主映射入口 ============

    fun mapToWeatherResponse(
        now: QWeatherNowResponse?,
        daily: QWeatherDailyResponse?,
        hourly: QWeatherHourlyResponse?,
        minutely: QWeatherMinutelyResponse?,
        warning: QWeatherWarningResponse?,
        air: QWeatherAirResponse?,
        longitude: Double,
        latitude: Double
    ): WeatherResponse {
        val tzOffsetSeconds = TimeZone.getDefault().rawOffset / 1000

        val realtime = mapRealtime(now, air)
        val minutelyForecast = mapMinutely(minutely)
        val hourlyForecast = mapHourly(hourly)
        val dailyForecast = mapDaily(daily)
        val alert = mapWarning(warning)

        return WeatherResponse(
            status = "ok",
            api_version = "qweather",
            lang = "zh_CN",
            unit = "metric",
            tzshift = tzOffsetSeconds,
            timezone = TimeZone.getDefault().id,
            server_time = System.currentTimeMillis() / 1000,
            location = listOf(longitude, latitude),
            result = WeatherResult(
                realtime = realtime,
                minutely = minutelyForecast,
                hourly = hourlyForecast,
                daily = dailyForecast,
                forecastKeypoint = minutelyForecast?.description,
                alert = alert
            )
        )
    }

    // ============ Realtime (实况) ============

    private fun mapRealtime(
        now: QWeatherNowResponse?,
        air: QWeatherAirResponse?
    ): RealtimeWeather? {
        val n = now?.now ?: return null
        return RealtimeWeather(
            status = "ok",
            temperature = n.temp?.toDoubleOrNull(),
            humidity = n.humidity?.toDoubleOrNull()?.div(100.0),
            cloudrate = n.cloud?.toDoubleOrNull()?.div(100.0),
            skycon = iconToSkycon(n.icon),
            visibility = n.vis?.toDoubleOrNull()?.times(1000.0),
            dswrf = null,
            wind = Wind(
                speed = n.windSpeed?.toDoubleOrNull(),
                direction = n.wind360?.toDoubleOrNull()
            ),
            pressure = n.pressure?.toDoubleOrNull()?.times(100.0),
            apparent_temperature = n.feelsLike?.toDoubleOrNull(),
            precipitation = Precipitation(
                local = PrecipitationLocal(
                    status = "ok",
                    datasource = "qweather",
                    intensity = n.precip?.toDoubleOrNull()
                )
            ),
            air_quality = mapAirQuality(air),
            life_index = null
        )
    }

    // ============ Air Quality (空气质量) ============

    private fun mapAirQuality(air: QWeatherAirResponse?): AirQuality? {
        if (air == null) return null
        val indexes = air.indexes ?: emptyList()
        val pollutants = air.pollutants ?: emptyList()

        val usEpa = indexes.firstOrNull { it.code == "us-epa" }
        val qaqi = indexes.firstOrNull { it.code == "qaqi" }

        fun pollutantValue(code: String): Double? =
            pollutants.firstOrNull { it.code == code }?.concentration?.value

        return AirQuality(
            pm25 = pollutantValue("pm2p5"),
            pm10 = pollutantValue("pm10"),
            o3 = pollutantValue("o3"),
            so2 = pollutantValue("so2"),
            no2 = pollutantValue("no2"),
            co = pollutantValue("co"),
            aqi = AirQualityIndex(
                chn = qaqi?.aqi, // 和风无中国国标 AQI，用 QAQI 替代
                usa = usEpa?.aqi
            ),
            description = AirQualityDescription(
                chn = qaqi?.category,
                usa = usEpa?.category
            )
        )
    }

    // ============ Minutely (分钟级降水) ============

    private fun mapMinutely(minutely: QWeatherMinutelyResponse?): MinutelyForecast? {
        val items = minutely?.minutely ?: return null
        val precipList = items.mapNotNull { it.precip?.toDoubleOrNull() }
        if (precipList.isEmpty()) return null

        return MinutelyForecast(
            status = "ok",
            datasource = "qweather",
            precipitation_2h = precipList,
            precipitation = precipList,
            probability = null,
            description = minutely.summary
        )
    }

    // ============ Hourly (逐小时) ============

    private fun mapHourly(hourly: QWeatherHourlyResponse?): HourlyForecast? {
        val items = hourly?.hourly ?: return null
        if (items.isEmpty()) return null

        return HourlyForecast(
            status = "ok",
            description = null,
            precipitation = items.map {
                HourlyValue(
                    datetime = it.fxTime,
                    value = it.precip?.toDoubleOrNull(),
                    probability = it.pop?.toDoubleOrNull()
                )
            },
            temperature = items.map {
                HourlyValue(datetime = it.fxTime, value = it.temp?.toDoubleOrNull())
            },
            apparent_temperature = null,
            wind = items.map {
                HourlyWind(
                    datetime = it.fxTime,
                    speed = it.windSpeed?.toDoubleOrNull(),
                    direction = it.wind360?.toDoubleOrNull()
                )
            },
            gust = null,
            humidity = items.map {
                HourlyValue(datetime = it.fxTime, value = it.humidity?.toDoubleOrNull()?.div(100.0))
            },
            cloudrate = items.map {
                HourlyValue(datetime = it.fxTime, value = it.cloud?.toDoubleOrNull()?.div(100.0))
            },
            skycon = items.map {
                HourlySkycon(datetime = it.fxTime, value = iconToSkycon(it.icon))
            },
            pressure = items.map {
                HourlyValue(datetime = it.fxTime, value = it.pressure?.toDoubleOrNull()?.times(100.0))
            },
            visibility = items.map {
                HourlyValue(datetime = it.fxTime, value = it.vis?.toDoubleOrNull()?.times(1000.0))
            },
            dswrf = null,
            air_quality = null,
            life_index = null
        )
    }

    // ============ Daily (逐日) ============

    private fun mapDaily(daily: QWeatherDailyResponse?): DailyForecast? {
        val items = daily?.daily ?: return null
        if (items.isEmpty()) return null

        return DailyForecast(
            status = "ok",
            astro = items.map {
                DailyAstro(
                    date = it.fxDate,
                    sunrise = AstroTime(time = it.sunrise),
                    sunset = AstroTime(time = it.sunset)
                )
            },
            precipitation = items.map {
                DailyPrecipitation(
                    date = it.fxDate,
                    max = it.precip?.toDoubleOrNull(),
                    min = it.precip?.toDoubleOrNull(),
                    avg = it.precip?.toDoubleOrNull(),
                    probability = null
                )
            },
            temperature = items.map {
                DailyTemperature(
                    date = it.fxDate,
                    max = it.tempMax?.toDoubleOrNull(),
                    min = it.tempMin?.toDoubleOrNull(),
                    avg = null
                )
            },
            temperature_08h_20h = null,
            temperature_20h_32h = null,
            wind = items.map {
                DailyWind(
                    date = it.fxDate,
                    max = Wind(speed = it.windSpeedDay?.toDoubleOrNull(), direction = it.wind360Day?.toDoubleOrNull()),
                    min = Wind(speed = it.windSpeedNight?.toDoubleOrNull(), direction = it.wind360Night?.toDoubleOrNull()),
                    avg = null
                )
            },
            wind_08h_20h = null,
            wind_20h_32h = null,
            humidity = items.map {
                DailyValue(
                    date = it.fxDate,
                    max = it.humidity?.toDoubleOrNull()?.div(100.0),
                    min = it.humidity?.toDoubleOrNull()?.div(100.0),
                    avg = it.humidity?.toDoubleOrNull()?.div(100.0)
                )
            },
            cloudrate = items.map {
                DailyValue(
                    date = it.fxDate,
                    max = it.cloud?.toDoubleOrNull()?.div(100.0),
                    min = it.cloud?.toDoubleOrNull()?.div(100.0),
                    avg = it.cloud?.toDoubleOrNull()?.div(100.0)
                )
            },
            pressure = items.map {
                DailyValue(
                    date = it.fxDate,
                    max = it.pressure?.toDoubleOrNull()?.times(100.0),
                    min = it.pressure?.toDoubleOrNull()?.times(100.0),
                    avg = it.pressure?.toDoubleOrNull()?.times(100.0)
                )
            },
            visibility = items.map {
                DailyValue(
                    date = it.fxDate,
                    max = it.vis?.toDoubleOrNull()?.times(1000.0),
                    min = it.vis?.toDoubleOrNull()?.times(1000.0),
                    avg = it.vis?.toDoubleOrNull()?.times(1000.0)
                )
            },
            dswrf = null,
            skycon = items.map {
                DailySkycon(date = it.fxDate, value = iconToSkycon(it.iconDay))
            },
            skycon_08h_20h = items.map {
                DailySkycon(date = it.fxDate, value = iconToSkycon(it.iconDay))
            },
            skycon_20h_32h = items.map {
                DailySkycon(date = it.fxDate, value = iconToSkycon(it.iconNight))
            },
            air_quality = null,
            life_index = DailyLifeIndex(
                ultraviolet = items.map {
                    LifeIndexDay(
                        date = it.fxDate,
                        index = it.uvIndex,
                        desc = null
                    )
                },
                carWashing = null,
                dressing = null,
                comfort = null,
                coldRisk = null
            )
        )
    }

    // ============ Warning (天气预警) ============

    private fun mapWarning(warning: QWeatherWarningResponse?): Alert? {
        val alerts = warning?.alerts ?: return Alert(status = "ok", content = emptyList())
        val contents = alerts.map { alert ->
            AlertContent(
                province = null,
                city = alert.senderName,
                county = null,
                title = alert.headline ?: alert.eventType?.name,
                description = alert.description,
                level = alert.color?.code ?: alert.severity ?: "",
                type = alert.eventType?.code ?: alert.icon,
                status = "active",
                id = alert.id,
                regionCode = null,
                areaCode = null,
                publishTime = parseIsoTimeToEpoch(alert.issuedTime)
            )
        }.sortedByPublishTimeDescending()

        return Alert(status = "ok", content = contents)
    }

    /**
     * 将 ISO 8601 时间字符串（如 "2025-10-24T11:19+08:00"）转换为 Unix 时间戳（秒）。
     */
    private fun parseIsoTimeToEpoch(isoTime: String?): Long? {
        if (isoTime.isNullOrBlank()) return null
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mmXXX", java.util.Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.parse(isoTime)?.time?.div(1000)
        } catch (e: Exception) {
            null
        }
    }

    // ============ 天气码 -> Skycon 映射 ============

    /**
     * 和风天气 icon 编码 -> 彩云 skycon 字符串。
     *
     * 和风的 icon 编码已内含昼夜信息（100=晴/白天, 150=晴/夜间），
     * 因此直接映射到对应的 CLEAR_DAY/CLEAR_NIGHT 等。
     */
    fun iconToSkycon(icon: String?): String? {
        if (icon.isNullOrBlank()) return null
        return when (icon) {
            // 晴 / 多云 / 阴
            "100" -> "CLEAR_DAY"
            "150" -> "CLEAR_NIGHT"
            "101", "102", "103" -> "PARTLY_CLOUDY_DAY"
            "151", "152", "153" -> "PARTLY_CLOUDY_NIGHT"
            "104" -> "CLOUDY"
            // 雨
            "300", "350", "305", "309", "313", "314" -> "LIGHT_RAIN"
            "301", "351", "306", "315" -> "MODERATE_RAIN"
            "307", "316" -> "HEAVY_RAIN"
            "308", "310", "311", "312", "317", "318" -> "STORM_RAIN"
            "399" -> "MODERATE_RAIN"
            // 雷阵雨
            "302", "303", "304" -> "THUNDER_SHOWER"
            // 雪
            "400", "407", "408", "457" -> "LIGHT_SNOW"
            "401", "409", "499" -> "MODERATE_SNOW"
            "402", "410" -> "HEAVY_SNOW"
            "403" -> "STORM_SNOW"
            // 雨夹雪
            "404", "405", "406", "456" -> "SLEET"
            // 雾
            "500", "501", "509", "510", "514", "515" -> "FOG"
            // 霾
            "502", "511" -> "MODERATE_HAZE"
            "512", "513" -> "HEAVY_HAZE"
            // 沙尘
            "503", "504" -> "DUST"
            "507", "508" -> "SAND"
            // 其他
            "900", "901" -> "CLEAR_DAY"
            else -> null
        }
    }
}
