package com.skypulse.weather.data.mock

import com.skypulse.weather.model.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 离线/预览/默认初始天气模拟数据生成器（以中国北京为基准）。
 *
 * 避免模拟器默认定位到加利福尼亚 Mountain View 导致出现美国天气或网络/API 未就绪时的白屏与报错。
 * 提供完整、精美的全卡片数据（实况、24小时逐小时、7天逐日、空气质量、天文、风速与舒适度）。
 */
object MockWeatherData {

    const val DEFAULT_CITY_NAME = "北京市"
    const val DEFAULT_CITY_ID = "beijing_sample"
    const val BEIJING_LON = 116.4074
    const val BEIJING_LAT = 39.9042

    fun createBeijingWeather(): WeatherResponse {
        val now = Calendar.getInstance()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)

        // 构造 24 小时逐小时数据
        val hourlyTemps = mutableListOf<HourlyValue>()
        val hourlySkycons = mutableListOf<HourlySkycon>()
        val hourlyWinds = mutableListOf<HourlyWind>()
        val hourlyUvs = mutableListOf<HourlyUvItem>()
        val hourlyAqis = mutableListOf<HourlyAqiValue>()
        val hourlyPrecip = mutableListOf<HourlyValue>()

        val hourCal = now.clone() as Calendar
        val baseHour = hourCal.get(Calendar.HOUR_OF_DAY)
        for (i in 0 until 24) {
            val h = (baseHour + i) % 24
            val timeStr = isoFormat.format(hourCal.time)
            
            // 白天温度高、夜晚温度低
            val temp = when (h) {
                in 6..8 -> 17.0 + (h - 6) * 1.5
                in 9..14 -> 20.0 + (h - 9) * 0.8
                in 15..18 -> 24.0 - (h - 15) * 1.2
                in 19..23 -> 20.0 - (h - 19) * 1.0
                else -> 15.0 + h * 0.3
            }
            hourlyTemps.add(HourlyValue(datetime = timeStr, value = temp, probability = 0.0))

            val isDayHour = h in 6..18
            val skycon = when {
                h in 11..15 -> "CLEAR_DAY"
                isDayHour -> "PARTLY_CLOUDY_DAY"
                else -> "CLEAR_NIGHT"
            }
            hourlySkycons.add(HourlySkycon(datetime = timeStr, value = skycon))

            hourlyWinds.add(HourlyWind(datetime = timeStr, speed = 2.8 + (i % 3) * 0.4, direction = 210.0))
            
            val uvIdx = if (isDayHour) {
                when (h) {
                    in 10..14 -> 4
                    in 8..16 -> 2
                    else -> 1
                }
            } else 0
            hourlyUvs.add(HourlyUvItem(datetime = timeStr, index = uvIdx.toString(), desc = if (uvIdx >= 3) "中等" else "弱"))
            
            hourlyAqis.add(HourlyAqiValue(datetime = timeStr, value = AirQualityIndex(chn = 32.0, usa = 32.0)))
            hourlyPrecip.add(HourlyValue(datetime = timeStr, value = 0.0, probability = 0.0))

            hourCal.add(Calendar.HOUR_OF_DAY, 1)
        }

        // 构造 7 天逐日数据
        val dailyAstro = mutableListOf<DailyAstro>()
        val dailyTemps = mutableListOf<DailyTemperature>()
        val dailySkycons = mutableListOf<DailySkycon>()
        val dailyWinds = mutableListOf<DailyWind>()
        val dailyPrecip = mutableListOf<DailyPrecipitation>()
        val dailyAqis = mutableListOf<DailyAqiIndexValue>()
        val dailyLife = mutableListOf<LifeIndexDay>()

        val dayCal = now.clone() as Calendar
        val weekSkycons = listOf("CLEAR_DAY", "PARTLY_CLOUDY_DAY", "CLEAR_DAY", "CLOUDY", "PARTLY_CLOUDY_DAY", "CLEAR_DAY", "CLEAR_DAY")
        val weekHighs = listOf(24.0, 23.0, 25.0, 21.0, 22.0, 24.0, 23.0)
        val weekLows = listOf(13.0, 14.0, 12.0, 11.0, 12.0, 13.0, 12.0)

        for (i in 0 until 7) {
            val dateStr = dateFormat.format(dayCal.time)
            dailyAstro.add(
                DailyAstro(
                    date = dateStr,
                    sunrise = AstroTime("06:18"),
                    sunset = AstroTime("17:42"),
                    moonrise = AstroTime("19:20"),
                    moonset = AstroTime("07:15"),
                    moonPhase = "盈凸月"
                )
            )
            dailyTemps.add(
                DailyTemperature(
                    date = dateStr,
                    max = weekHighs[i],
                    min = weekLows[i],
                    avg = (weekHighs[i] + weekLows[i]) / 2.0
                )
            )
            dailySkycons.add(DailySkycon(date = dateStr, value = weekSkycons[i]))
            dailyWinds.add(
                DailyWind(
                    date = dateStr,
                    max = Wind(speed = 4.2, direction = 210.0),
                    min = Wind(speed = 1.8, direction = 190.0),
                    avg = Wind(speed = 3.0, direction = 200.0)
                )
            )
            dailyPrecip.add(DailyPrecipitation(date = dateStr, max = 0.0, min = 0.0, avg = 0.0, probability = 0.0))
            dailyAqis.add(
                DailyAqiIndexValue(
                    date = dateStr,
                    max = AirQualityIndex(chn = 38.0, usa = 38.0),
                    min = AirQualityIndex(chn = 26.0, usa = 26.0),
                    avg = AirQualityIndex(chn = 32.0, usa = 32.0)
                )
            )
            dailyLife.add(LifeIndexDay(date = dateStr, index = "4", desc = "中等"))
            dayCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        // 分钟级降水（未来2小时无雨）
        val minutelyList = List(24) { 0.0 }

        return WeatherResponse(
            status = "ok",
            lang = "zh_CN",
            unit = "metric",
            timezone = "Asia/Shanghai",
            server_time = System.currentTimeMillis() / 1000L,
            location = listOf(BEIJING_LAT, BEIJING_LON),
            result = WeatherResult(
                realtime = RealtimeWeather(
                    status = "ok",
                    temperature = 23.0,
                    apparent_temperature = 23.6,
                    humidity = 0.42,
                    cloudrate = 0.18,
                    skycon = "CLEAR_DAY",
                    visibility = 25.0,
                    dswrf = 520.0,
                    pressure = 1016.0,
                    wind = Wind(
                        speed = 3.2,
                        direction = 210.0,
                        gust = 5.0
                    ),
                    precipitation = Precipitation(
                        local = PrecipitationLocal(status = "ok", datasource = "radar", intensity = 0.0)
                    ),
                    air_quality = AirQuality(
                        pm25 = 12.0,
                        pm10 = 24.0,
                        o3 = 48.0,
                        so2 = 5.0,
                        no2 = 18.0,
                        co = 0.6,
                        aqi = AirQualityIndex(chn = 32.0, usa = 32.0),
                        description = AirQualityDescription(chn = "优", usa = "Good"),
                        primaryPollutant = "无",
                        healthEffect = "空气质量优良，空气非常清新",
                        healthAdviceGeneral = "各类人群可正常户外活动",
                        healthAdviceSensitive = "各类人群可正常户外活动",
                        pm25Unit = "μg/m3",
                        pm10Unit = "μg/m3",
                        o3Unit = "μg/m3",
                        so2Unit = "μg/m3",
                        no2Unit = "μg/m3",
                        coUnit = "mg/m3",
                        pm25Iaqi = 25,
                        pm10Iaqi = 24,
                        o3Iaqi = 32,
                        so2Iaqi = 8,
                        no2Iaqi = 15,
                        coIaqi = 8
                    ),
                    life_index = LifeIndex(
                        ultraviolet = LifeIndexItem(index = "4", desc = "中等"),
                        comfort = LifeIndexItem(index = "1", desc = "舒适")
                    )
                ),
                minutely = MinutelyForecast(
                    status = "ok",
                    datasource = "radar",
                    precipitation_2h = minutelyList,
                    precipitation = minutelyList,
                    probability = minutelyList,
                    description = "未来两小时无降水，晴空万里"
                ),
                hourly = HourlyForecast(
                    status = "ok",
                    description = "未来24小时天气晴好，气温适宜",
                    precipitation = hourlyPrecip,
                    temperature = hourlyTemps,
                    apparent_temperature = hourlyTemps,
                    wind = hourlyWinds,
                    humidity = hourlyTemps.map { HourlyValue(datetime = it.datetime, value = 0.42) },
                    cloudrate = hourlyTemps.map { HourlyValue(datetime = it.datetime, value = 0.18) },
                    skycon = hourlySkycons,
                    air_quality = HourlyAirQuality(aqi = hourlyAqis),
                    life_index = HourlyLifeIndex(ultraviolet = hourlyUvs)
                ),
                daily = DailyForecast(
                    status = "ok",
                    astro = dailyAstro,
                    precipitation = dailyPrecip,
                    temperature = dailyTemps,
                    wind = dailyWinds,
                    skycon = dailySkycons,
                    air_quality = DailyAirQuality(aqi = dailyAqis),
                    life_index = DailyLifeIndex(ultraviolet = dailyLife)
                ),
                forecastKeypoint = "今天北京天气晴好，温度舒适，适宜外出与通风",
                alert = Alert(status = "ok", content = emptyList())
            )
        )
    }
}
