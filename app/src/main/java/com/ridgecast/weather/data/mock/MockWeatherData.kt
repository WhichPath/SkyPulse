package com.ridgecast.weather.data.mock

import com.ridgecast.weather.model.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 完全离线/缓存损坏时的最终回退模拟数据（以**火星·奥林帕斯山**为基准）。
 *
 * 设计目的：Fall back 时必须给用户一个"明确不是正常地球天气"的样态，
 * 且所有字段值尽量符合该地真实物理条件（极低压、极低温、尘暴、无液态水），
 * 使用户一眼就能理解这是演示/兜底数据而非真实地球气象。涵盖：
 *  实况、24h逐小时、7天逐日、天文、风速、舒适度等全卡片数据。
 *
 * 坐标：奥林帕斯山 Olympus Mons（火星，18.65°N, 226.2°E）。
 */
object MockWeatherData {

    const val DEFAULT_CITY_NAME = "火星·奥林帕斯山"
    const val DEFAULT_CITY_ID = "mars_sample_olympus_mons"
    const val MARS_OLYMPUS_MONS_LON = 226.2
    const val MARS_OLYMPUS_MONS_LAT = 18.65

    private val fictionalDailyHigh = listOf(-48.0, -50.0, -46.0, -50.0, -52.0, -47.0, -49.0)
    private val fictionalDailyLow = listOf(-78.0, -80.0, -76.0, -80.0, -82.0, -75.0, -79.0)

    fun createMarsWeather(): WeatherResponse {
        val now = Calendar.getInstance()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)

        // ---- 24h 逐小时 ----
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

            // 火星表面气温：白天~ evening 寒冷，夜间极寒；昼夜温差大
            val temp = when (h) {
                in 11..15 -> -48.0 + (h - 11) * 0.8
                in 6..10 -> -52.0 + (h - 6) * 1.0
                in 16..19 -> -46.0 - (h - 15) * 1.5
                in 20..23 -> -55.0 - (h - 20) * 1.2
                else -> -70.0 + h * 0.3
            }
            hourlyTemps.add(HourlyValue(datetime = timeStr, value = temp, probability = 0.0))

            val isDayHour = h in 6..18
            val skycon = if (isDayHour) {
                "DUST" // 火星表面漫天粉红尘埃
            } else {
                "DUST_NIGHT"
            }
            hourlySkycons.add(HourlySkycon(datetime = timeStr, value = skycon))

            val windSpeed = 42.0 + (i % 5) * 3.2
            val windLevel = when {
                windSpeed >= 50 -> 6
                windSpeed >= 39 -> 5
                windSpeed >= 29 -> 4
                windSpeed >= 20 -> 3
                else -> 2
            }
            hourlyWinds.add(HourlyWind(datetime = timeStr, speed = windSpeed, direction = 220.0 + i % 40, scale = windLevel))

            val uvIdx = 1 // 火星表面 UV 低（大气 CO2 + 尘暴屏蔽）
            hourlyUvs.add(HourlyUvItem(datetime = timeStr, index = uvIdx.toString(), desc = "弱"))
            hourlyAqis.add(HourlyAqiValue(datetime = timeStr, value = AirQualityIndex(chn = 0.0, usa = 0.0)))
            hourlyPrecip.add(HourlyValue(datetime = timeStr, value = 0.0, probability = 0.0))

            hourCal.add(Calendar.HOUR_OF_DAY, 1)
        }

        // ---- 7 天逐日 ----
        val dailyAstro = mutableListOf<DailyAstro>()
        val dailyTemps = mutableListOf<DailyTemperature>()
        val dailySkycons = mutableListOf<DailySkycon>()
        val dailyWinds = mutableListOf<DailyWind>()
        val dailyPrecip = mutableListOf<DailyPrecipitation>()
        val dailyAqis = mutableListOf<DailyAqiIndexValue>()
        val dailyLife = mutableListOf<LifeIndexDay>()

        val dayCal = now.clone() as Calendar
        val weekSkycons = listOf("DUST", "DUST", "DUST", "DUST", "SAND", "DUST", "DUST")

        for (i in 0 until 7) {
            val dateStr = dateFormat.format(dayCal.time)
            dailyAstro.add(
                DailyAstro(
                    date = dateStr,
                    sunrise = AstroTime("06:12"),
                    sunset = AstroTime("18:48"),
                    moonrise = AstroTime("05:31"),
                    moonset = AstroTime("19:02"),
                    moonPhase = null,
                    solarNoon = AstroTime("12:30"),
                    solarMidnight = AstroTime("00:30"),
                    civilDawn = AstroTime("05:48"),
                    civilDusk = AstroTime("19:12"),
                    astronomicalDawn = AstroTime("05:10"),
                    astronomicalDusk = AstroTime("19:48"),
                    nauticalDawn = AstroTime("05:30"),
                    nauticalDusk = AstroTime("19:30"),
                    moonTransit = AstroTime("00:12"),
                    moonUnderfoot = AstroTime("12:12")
                )
            )
            dailyTemps.add(
                DailyTemperature(
                    date = dateStr,
                    max = fictionalDailyHigh[i],
                    min = fictionalDailyLow[i],
                    avg = (fictionalDailyHigh[i] + fictionalDailyLow[i]) / 2.0
                )
            )
            dailySkycons.add(DailySkycon(date = dateStr, value = weekSkycons[i]))
            dailyWinds.add(
                DailyWind(
                    date = dateStr,
                    max = Wind(speed = 62.4, direction = 230.0, gust = 88.0, scale = 6),
                    min = Wind(speed = 28.0, direction = 190.0, gust = 42.0, scale = 3),
                    avg = Wind(speed = 44.0, direction = 210.0, gust = 58.0, scale = 4)
                )
            )
            dailyPrecip.add(DailyPrecipitation(date = dateStr, max = 0.0, min = 0.0, avg = 0.0, probability = 0.0))
            dailyAqis.add(
                DailyAqiIndexValue(
                    date = dateStr,
                    max = AirQualityIndex(chn = 0.0, usa = 0.0),
                    min = AirQualityIndex(chn = 0.0, usa = 0.0),
                    avg = AirQualityIndex(chn = 0.0, usa = 0.0)
                )
            )
            dailyLife.add(LifeIndexDay(date = dateStr, index = "1", desc = "强防护"))
            dayCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return WeatherResponse(
            status = "ok",
            lang = "zh-CN",
            unit = "metric",
            // 火星无统一时区概念，沿用 UTC 标识避免 Earth 时区误用
            timezone = "UTC+00:00",
            server_time = System.currentTimeMillis() / 1000L,
            location = listOf(MARS_OLYMPUS_MONS_LAT, MARS_OLYMPUS_MONS_LON),
            result = WeatherResult(
                realtime = RealtimeWeather(
                    status = "ok",
                    temperature = -58.0,
                    apparent_temperature = -62.4,
                    humidity = 0.02,
                    cloudrate = 0.05,
                    skycon = "DUST",
                    visibility = 3.2,
                    dswrf = null,
                    pressure = 610.0,
                    wind = Wind(
                        speed = 54.0,
                        direction = 230.0,
                        gust = 78.0,
                        scale = 5
                    ),
                    precipitation = Precipitation(
                        local = PrecipitationLocal(status = "ok", datasource = "radar", intensity = 0.0)
                    ),
                    air_quality = null,
                    life_index = LifeIndex(
                        ultraviolet = LifeIndexItem(index = "1", desc = "弱"),
                        comfort = LifeIndexItem(index = "5", desc = "极寒")
                    )
                ),
                minutely = null,
                hourly = HourlyForecast(
                    status = "ok",
                    description = "未来24小时持续尘暴风向多变，气压稳定极低，无降水",
                    precipitation = hourlyPrecip,
                    temperature = hourlyTemps,
                    apparent_temperature = hourlyTemps,
                    wind = hourlyWinds,
                    humidity = hourlyTemps.map { HourlyValue(datetime = it.datetime, value = 0.02) },
                    cloudrate = hourlyTemps.map { HourlyValue(datetime = it.datetime, value = 0.05) },
                    skycon = hourlySkycons,
                    air_quality = null,
                    life_index = HourlyLifeIndex(ultraviolet = hourlyUvs)
                ),
                daily = DailyForecast(
                    status = "ok",
                    astro = dailyAstro,
                    precipitation = dailyPrecip,
                    temperature = dailyTemps,
                    wind = dailyWinds,
                    skycon = dailySkycons,
                    air_quality = null,
                    life_index = DailyLifeIndex(ultraviolet = dailyLife)
                ),
                forecastKeypoint = "奥林帕斯山表面持续砂尘漫天，长时间暴露充电/通信设备过热与冻伤风险",
                alert = Alert(status = "ok", content = emptyList())
            )
        )
    }
}