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
import com.skypulse.weather.model.HourlyLifeIndex
import com.skypulse.weather.model.HourlySkycon
import com.skypulse.weather.model.HourlyUvItem
import com.skypulse.weather.model.HourlyValue
import com.skypulse.weather.model.HourlyWind
import com.skypulse.weather.model.LifeIndex
import com.skypulse.weather.model.LifeIndexDay
import com.skypulse.weather.model.LifeIndexItem
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
 * 单位转换规则（和风 v1 -> 彩云）：
 * - 湿度：小数(0-1) -> 小数(0-1)，无需转换
 * - 云量：小数(0-1) -> 小数(0-1)，无需转换
 * - 气压：hPa -> Pa，乘以 100
 * - 能见度：m -> m，无需转换
 * - 风速：m/s -> km/h，乘以 3.6
 * - 温度：°C -> °C，无需转换
 * - 降水概率：小数(0-1) -> 百分比(0-100)，乘以 100
 *
 * 注意：实况(now)和分钟降水(minutely)仍使用 v7 API，单位转换规则见各方法。
 */
object QWeatherMapper {

    // ============ 主映射入口 ============

    fun mapToWeatherResponse(
        now: QWeatherNowResponse?,
        daily: QWeatherV1DailyResponse?,
        hourly: QWeatherV1HourlyResponse?,
        minutely: QWeatherMinutelyResponse?,
        warning: QWeatherWarningResponse?,
        air: QWeatherAirResponse?,
        longitude: Double,
        latitude: Double
    ): WeatherResponse {
        val tzOffsetSeconds = TimeZone.getDefault().rawOffset / 1000

        // 提取今日 UV 索引，用于 realtime（和风 now 端点不含 UV）
        val todayUvIndex = daily?.days?.firstOrNull()?.uvIndexMax?.toInt()?.toString()

        val realtime = mapRealtime(now, air, todayUvIndex)
        val minutelyForecast = mapMinutely(minutely)
        val hourlyForecast = mapHourlyV1(hourly)
        val dailyForecast = mapDailyV1(daily)
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
        air: QWeatherAirResponse?,
        todayUvIndex: String?
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
            life_index = if (todayUvIndex != null) {
                LifeIndex(
                    ultraviolet = LifeIndexItem(index = todayUvIndex, desc = uvDescription(todayUvIndex)),
                    comfort = null
                )
            } else null
        )
    }

    // ============ Air Quality (空气质量) ============

    private fun mapAirQuality(air: QWeatherAirResponse?): AirQuality? {
        if (air == null) return null
        val indexes = air.indexes
        if (indexes.isNullOrEmpty()) return null
        val pollutants = air.pollutants ?: emptyList()

        val usEpa = indexes.firstOrNull { it.code == "us-epa" }
        // 和风无中国国标 AQI，优先用 qaqi，其次取任意非 us-epa 索引兜底
        val chnIndex = indexes.firstOrNull { it.code == "qaqi" }
            ?: indexes.firstOrNull { it.code != "us-epa" }

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
                chn = chnIndex?.aqi ?: usEpa?.aqi,
                usa = usEpa?.aqi
            ),
            description = AirQualityDescription(
                chn = chnIndex?.category ?: usEpa?.category,
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

    // ============ Hourly (逐小时, v1 API) ============

    private fun mapHourlyV1(hourly: QWeatherV1HourlyResponse?): HourlyForecast? {
        val items = hourly?.hours ?: return null
        if (items.isEmpty()) return null

        // v1 hourly 自带真实逐小时 UV 指数
        val hourlyUv = items.map { hour ->
            val uvStr = hour.uvIndex?.toInt()?.toString()
            HourlyUvItem(
                datetime = hour.forecastTime,
                index = uvStr,
                desc = uvDescription(uvStr)
            )
        }

        return HourlyForecast(
            status = "ok",
            description = null,
            precipitation = items.map {
                HourlyValue(
                    datetime = it.forecastTime,
                    value = it.precipitation?.amount?.value,
                    probability = it.precipitation?.probability?.times(100.0)
                )
            },
            temperature = items.map {
                HourlyValue(datetime = it.forecastTime, value = it.temperature?.value)
            },
            apparent_temperature = items.map {
                HourlyValue(datetime = it.forecastTime, value = it.feelsLike?.value)
            },
            wind = items.map {
                HourlyWind(
                    datetime = it.forecastTime,
                    speed = it.wind?.speed?.value?.times(3.6), // m/s -> km/h
                    direction = it.wind?.direction?.degree
                )
            },
            gust = items.map {
                HourlyValue(datetime = it.forecastTime, value = it.windGust?.value?.times(3.6))
            },
            humidity = items.map {
                HourlyValue(datetime = it.forecastTime, value = it.humidity)
            },
            cloudrate = items.map {
                HourlyValue(datetime = it.forecastTime, value = it.cloudCover)
            },
            skycon = items.map {
                HourlySkycon(datetime = it.forecastTime, value = iconToSkycon(it.condition?.code))
            },
            pressure = items.map {
                HourlyValue(datetime = it.forecastTime, value = it.pressure?.value?.times(100.0))
            },
            visibility = items.map {
                HourlyValue(datetime = it.forecastTime, value = it.visibility?.value)
            },
            dswrf = null,
            air_quality = null,
            life_index = HourlyLifeIndex(ultraviolet = hourlyUv)
        )
    }

    // ============ Daily (逐日, v1 API) ============

    private fun mapDailyV1(daily: QWeatherV1DailyResponse?): DailyForecast? {
        val items = daily?.days ?: return null
        if (items.isEmpty()) return null

        return DailyForecast(
            status = "ok",
            astro = items.map {
                val date = it.forecastStartTime?.substringBefore("T")
                DailyAstro(
                    date = date,
                    sunrise = AstroTime(time = extractTime(it.astro?.sunrise)),
                    sunset = AstroTime(time = extractTime(it.astro?.sunset))
                )
            },
            precipitation = items.map {
                val date = it.forecastStartTime?.substringBefore("T")
                val dayPrecip = it.daytime?.precipitation
                DailyPrecipitation(
                    date = date,
                    max = dayPrecip?.amount?.value,
                    min = dayPrecip?.amount?.value,
                    avg = dayPrecip?.amount?.value,
                    probability = dayPrecip?.probability?.times(100.0)
                )
            },
            temperature = items.map {
                val date = it.forecastStartTime?.substringBefore("T")
                DailyTemperature(
                    date = date,
                    max = it.temperatureMax?.value,
                    min = it.temperatureMin?.value,
                    avg = it.temperatureAvg?.value
                )
            },
            temperature_08h_20h = null,
            temperature_20h_32h = null,
            wind = items.map {
                val date = it.forecastStartTime?.substringBefore("T")
                DailyWind(
                    date = date,
                    max = Wind(
                        speed = it.daytime?.wind?.speed?.value?.times(3.6),
                        direction = it.daytime?.wind?.direction?.degree
                    ),
                    min = Wind(
                        speed = it.nighttime?.wind?.speed?.value?.times(3.6),
                        direction = it.nighttime?.wind?.direction?.degree
                    ),
                    avg = null
                )
            },
            wind_08h_20h = null,
            wind_20h_32h = null,
            humidity = items.map {
                val date = it.forecastStartTime?.substringBefore("T")
                val dayH = it.daytime?.humidity
                val nightH = it.nighttime?.humidity
                DailyValue(
                    date = date,
                    max = dayH,
                    min = nightH,
                    avg = if (dayH != null && nightH != null) (dayH + nightH) / 2.0 else null
                )
            },
            cloudrate = items.map {
                val date = it.forecastStartTime?.substringBefore("T")
                val dayC = it.daytime?.cloudCover
                val nightC = it.nighttime?.cloudCover
                DailyValue(
                    date = date,
                    max = dayC,
                    min = nightC,
                    avg = if (dayC != null && nightC != null) (dayC + nightC) / 2.0 else null
                )
            },
            pressure = null,
            visibility = null,
            dswrf = null,
            skycon = items.map {
                val date = it.forecastStartTime?.substringBefore("T")
                DailySkycon(date = date, value = iconToSkycon(it.daytime?.condition?.code))
            },
            skycon_08h_20h = items.map {
                val date = it.forecastStartTime?.substringBefore("T")
                DailySkycon(date = date, value = iconToSkycon(it.daytime?.condition?.code))
            },
            skycon_20h_32h = items.map {
                val date = it.forecastStartTime?.substringBefore("T")
                DailySkycon(date = date, value = iconToSkycon(it.nighttime?.condition?.code))
            },
            air_quality = null,
            life_index = DailyLifeIndex(
                ultraviolet = items.map {
                    val date = it.forecastStartTime?.substringBefore("T")
                    LifeIndexDay(
                        date = date,
                        index = it.uvIndexMax?.toInt()?.toString(),
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

    /** 从 v1 ISO 时间（如 "2024-08-11T04:22Z" 或 "2024-08-11T12:22+08:00"）中提取 "HH:mm"。 */
    private fun extractTime(isoTime: String?): String? {
        if (isoTime.isNullOrBlank()) return null
        val tIndex = isoTime.indexOf('T')
        if (tIndex < 0) return null
        val timePart = isoTime.substring(tIndex + 1)
        return if (timePart.length >= 5) timePart.substring(0, 5) else null
    }

    /**
     * UV 索引数值 -> 中文描述。
     */
    private fun uvDescription(uvIndex: String?): String? {
        val v = uvIndex?.toIntOrNull() ?: return null
        return when {
            v <= 2 -> "弱"
            v <= 5 -> "中等"
            v <= 7 -> "强"
            v <= 10 -> "很强"
            else -> "极强"
        }
    }

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
