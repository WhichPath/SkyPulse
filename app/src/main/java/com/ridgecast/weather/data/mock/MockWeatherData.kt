package com.ridgecast.weather.data.mock

import com.ridgecast.weather.model.AirQuality
import com.ridgecast.weather.model.Alert
import com.ridgecast.weather.model.AstronomySnapshot
import com.ridgecast.weather.model.AstroTime
import com.ridgecast.weather.model.DailyAstro
import com.ridgecast.weather.model.DailyForecast
import com.ridgecast.weather.model.DailyLifeIndex
import com.ridgecast.weather.model.DailyPrecipitation
import com.ridgecast.weather.model.DailySkycon
import com.ridgecast.weather.model.DailyTemperature
import com.ridgecast.weather.model.DailyValue
import com.ridgecast.weather.model.DailyWind
import com.ridgecast.weather.model.HourlyAirQuality
import com.ridgecast.weather.model.HourlyForecast
import com.ridgecast.weather.model.HourlyLifeIndex
import com.ridgecast.weather.model.HourlySkycon
import com.ridgecast.weather.model.HourlyUvItem
import com.ridgecast.weather.model.HourlyValue
import com.ridgecast.weather.model.HourlyWind
import com.ridgecast.weather.model.LifeIndex
import com.ridgecast.weather.model.LifeIndexDay
import com.ridgecast.weather.model.LifeIndexItem
import com.ridgecast.weather.model.MinutelyForecast
import com.ridgecast.weather.model.Precipitation
import com.ridgecast.weather.model.PrecipitationLocal
import com.ridgecast.weather.model.PrecipitationNearest
import com.ridgecast.weather.model.RealtimeWeather
import com.ridgecast.weather.model.WeatherResponse
import com.ridgecast.weather.model.WeatherResult
import com.ridgecast.weather.model.Wind
import com.ridgecast.weather.ui.glass.SkyPosition
import com.ridgecast.weather.util.WeatherDataValidator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 完全离线/缓存损坏时的最终回退**示例数据**。
 *
 * ## 位置：世界地图的中心点
 * 联合国大会 2018 年以 164 票通过决议，推荐用 **Equal Earth**（EPSG:8857，
 * 等积投影，Europe-Africa centred）替代墨卡托（保角）作为官方世界地图。
 * 该地图的中央经线为 0°（格林尼治），图幅的几何中心即 **赤道 × 本初子午线**：
 * **北纬 0°、东经 0°，几内亚湾海域**（非洲西岸外海，最近的陆地是加纳与加蓬）。
 *
 * ## 气候口径（几内亚湾，赤道海域）
 * 热带海洋性（Köppen Af）：全年高温、湿度高、日较差小、对流雨为主；
 * 10 月前后处于第二雨季，午后多雷阵雨；西南季风与东北哈马丹风交替。
 *
 * ## 数据口径
 * 字段与真实和风接口**完全同构**（逐时/逐日/实况/分钟级/预警/天文），
 * 并由 [com.ridgecast.weather.util.WeatherDataValidator] 校验；
 * 单元测试 `MockWeatherDataTest` 会断言本数据"零缺失"，
 * 真实接口字段升级时校验器同步升级，缺口会立刻暴露。
 *
 * ### 硬约束：示例数据只给「原始观测量」，派生量一律由 App 自算
 * 1. **空气质量只给分项浓度**（PM2.5/PM10/O3/SO2/NO2/CO + 单位），
 *    不预置 `aqi` / `*Iaqi` / `description`：
 *    AQI 由 [com.ridgecast.weather.util.AqiCalculator]、AQHI 由
 *    [com.ridgecast.weather.util.AqhiCalculator] 自算，
 *    与真实接口路径（`QWeatherMapper` 同样调 AqiCalculator）口径完全一致；
 * 2. **逐时/逐日不含空气质量**：和风虽有 /airquality/v1/hourly、/airquality/v1/daily
 *    端点，但本项目只接入实况 /airquality/v1/current，模型里没有逐时/逐日空气质量字段，
 *    示例数据不得凭空造数；
 * 3. **分钟级降水必须有真实值**：下雨时给出 5 分钟累计降水，
 *    不下雨时全 0（对应和风的「未来两小时无降水」）；
 *    `forecast_keypoint` 取自分钟级 summary，**不再塞气候说明文案**
 *    （那会让示例数据多出一块真实数据永远不会有的面板）。
 */
object MockWeatherData {

    /** 地图中心点：北纬 0°、东经 0°（几内亚湾）。 */
    const val MAP_CENTER_LON = 0.0
    const val MAP_CENTER_LAT = 0.0

    const val DEFAULT_CITY_NAME = "几内亚湾"
    const val DEFAULT_CITY_ID = "earth_map_center_equator_0e"

    private const val HOURLY_ROWS = 24
    private const val DAILY_ROWS = 10

    /** 逐日天气型（几内亚湾的典型对流性天气循环）。 */
    private val daySkycons = listOf(
        "PARTLY_CLOUDY_DAY", "CLOUDY", "LIGHT_RAIN", "PARTLY_CLOUDY_DAY",
        "THUNDER_SHOWER", "SHOWER_RAIN", "PARTLY_CLOUDY_DAY", "CLOUDY",
        "LIGHT_RAIN", "PARTLY_CLOUDY_DAY"
    )

    /**
     * 生成示例天气数据。
     *
     * @param nowMillis 当前时刻（默认系统时间）
     */
    fun createSampleWeather(nowMillis: Long = System.currentTimeMillis()): WeatherResponse {
        val localCalendar = Calendar.getInstance()
        val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utcCalendar.timeInMillis = nowMillis

        // 示例地点的"本地时"按设备时区呈现（与逐时序列的时钟保持一致）；
        // 太阳时刻用等效经线（时区标准经线）求解，日长/赤纬仍取赤道真实值。
        val offsetMinutes = localCalendar.get(Calendar.ZONE_OFFSET) +
            localCalendar.get(Calendar.DST_OFFSET)
        val effectiveLongitude = offsetMinutes / 4.0

        val isoFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val hourCalendar = localCalendar.clone() as Calendar
        val baseHour = hourCalendar.get(Calendar.HOUR_OF_DAY)

        // ---- 天文（真实计算：赤道当日的日出日落与月相） ----
        val solar = SkyPosition.solarEvents(nowMillis, MAP_CENTER_LAT, effectiveLongitude)
        val moon = SkyPosition.moonEvents(nowMillis, MAP_CENTER_LAT, effectiveLongitude)
        val moonNow = SkyPosition.moon(nowMillis, MAP_CENTER_LAT, effectiveLongitude)
        val moonPhaseName = phaseName(moonNow.phaseFraction, moonNow.waxing)

        // ---- 逐时（24h） ----
        val hourlyTemps = mutableListOf<HourlyValue>()
        val hourlyApparent = mutableListOf<HourlyValue>()
        val hourlyWinds = mutableListOf<HourlyWind>()
        val hourlyGusts = mutableListOf<HourlyValue>()
        val hourlyHumidity = mutableListOf<HourlyValue>()
        val hourlyCloudrate = mutableListOf<HourlyValue>()
        val hourlySkycons = mutableListOf<HourlySkycon>()
        val hourlyPressure = mutableListOf<HourlyValue>()
        val hourlyVisibility = mutableListOf<HourlyValue>()
        val hourlyPrecip = mutableListOf<HourlyValue>()
        // 逐时空气质量（/airquality/v1/hourly 同构）：只给**原始分项浓度**，
        // 逐时 AQI 由 App 用 AqiCalculator 自算（官方逐时端点只给 qaqi/gb-defra）。
        val hourlyPm25 = mutableListOf<HourlyValue>()
        val hourlyPm10 = mutableListOf<HourlyValue>()
        val hourlyO3 = mutableListOf<HourlyValue>()
        val hourlySo2 = mutableListOf<HourlyValue>()
        val hourlyNo2 = mutableListOf<HourlyValue>()
        val hourlyCo = mutableListOf<HourlyValue>()
        val hourlyUv = mutableListOf<HourlyUvItem>()

        for (i in 0 until HOURLY_ROWS) {
            val h = (baseHour + i) % 24
            val timeStr = isoFormat.format(hourCalendar.time)

            val diurnal = sin(2.0 * PI * (h - 15) / 24.0)
            val temperature = 26.8 + 2.2 * diurnal
            // 口径与真实 v1 接口一致：湿度/云量都是 0..1 的比例
            val humidity = (80.0 - 12.0 * diurnal) / 100.0
            val cloudrate = (58.0 + 22.0 * sin(2.0 * PI * (h - 12) / 24.0)) / 100.0
            val skycon = hourlySkycon(h)
            val rain = hourlyRain(h)
            val windSpeed = 13.0 + 5.0 * abs(diurnal) + if (rain > 0) 3.0 else 0.0

            hourlyTemps.add(HourlyValue(datetime = timeStr, value = round1(temperature)))
            hourlyApparent.add(
                HourlyValue(
                    datetime = timeStr,
                    value = round1(temperature + 4.2 + 1.5 * humidity / 100.0)
                )
            )
            hourlyWinds.add(
                HourlyWind(
                    datetime = timeStr,
                    speed = round1(windSpeed),
                    direction = 205.0 + 18.0 * sin(2.0 * PI * (h - 9) / 24.0),
                    scale = beaufort(windSpeed)
                )
            )
            hourlyGusts.add(HourlyValue(datetime = timeStr, value = round1(windSpeed * 1.7)))
            hourlyHumidity.add(HourlyValue(datetime = timeStr, value = round2(humidity)))
            hourlyCloudrate.add(HourlyValue(datetime = timeStr, value = round2(cloudrate)))
            hourlySkycons.add(HourlySkycon(datetime = timeStr, value = skycon))
            hourlyPressure.add(HourlyValue(datetime = timeStr, value = round1(1011.4 - 0.9 * diurnal)))
            hourlyVisibility.add(
                HourlyValue(datetime = timeStr, value = round1(19.0 - cloudrate / 12.0 - rain * 1.4))
            )
            hourlyPrecip.add(
                HourlyValue(
                    datetime = timeStr,
                    value = rain,
                    probability = if (rain > 0) 0.62 else 0.12
                )
            )
            // 逐时分项污染物浓度（赤道海域气溶胶口径，与实况同一套量级）
            val pm25 = round1(4.8 + cloudrate * 3.2)
            val pm10 = round1(12.0 + cloudrate * 7.5)
            val o3 = round1(46.0 + cloudrate * 18.0)
            val so2 = round1(1.2 + cloudrate * 0.8)
            val no2 = round1(2.4 + cloudrate * 2.0)
            val co = round1(320.0 + cloudrate * 90.0)
            hourlyPm25.add(HourlyValue(datetime = timeStr, value = pm25))
            hourlyPm10.add(HourlyValue(datetime = timeStr, value = pm10))
            hourlyO3.add(HourlyValue(datetime = timeStr, value = o3))
            hourlySo2.add(HourlyValue(datetime = timeStr, value = so2))
            hourlyNo2.add(HourlyValue(datetime = timeStr, value = no2))
            hourlyCo.add(HourlyValue(datetime = timeStr, value = co))
            hourlyUv.add(
                HourlyUvItem(
                    datetime = timeStr,
                    index = uvIndex(h).toString(),
                    desc = uvDesc(uvIndex(h))
                )
            )

            hourCalendar.add(Calendar.HOUR_OF_DAY, 1)
        }

        // ---- 逐日（10 天） ----
        val dailyAstro = mutableListOf<DailyAstro>()
        val dailyTemps = mutableListOf<DailyTemperature>()
        val dailySkycons = mutableListOf<DailySkycon>()
        val dailyWinds = mutableListOf<DailyWind>()
        val dailyPrecip = mutableListOf<DailyPrecipitation>()
        val dailyLife = mutableListOf<LifeIndexDay>()

        val dayCalendar = utcCalendar.clone() as Calendar
        dayCalendar.set(Calendar.HOUR_OF_DAY, 12)
        dayCalendar.set(Calendar.MINUTE, 0)
        dayCalendar.set(Calendar.SECOND, 0)

        for (i in 0 until DAILY_ROWS) {
            val dateStr = dateFormat.format(dayCalendar.time)
            val skycon = daySkycons[i % daySkycons.size]
            val rainy = skycon.contains("RAIN")
            val maxTemp = 29.4 + (i % 3) * 0.4 - (if (rainy) 1.2 else 0.0)
            val minTemp = 25.6 + (i % 2) * 0.3
            val avgTemp = (maxTemp + minTemp) / 2.0
            val maxWind = 19.6 + (i % 4) * 2.1
            val minWind = 6.8 + (i % 3) * 1.4
            val avgWind = (maxWind + minWind) / 2.0
            val rainAmount = when {
                skycon == "THUNDER_SHOWER" -> 18.6
                skycon == "SHOWER_RAIN" -> 9.4
                skycon == "LIGHT_RAIN" -> 3.2
                skycon == "CLOUDY" -> 0.6
                else -> 0.0
            }
            val rainProb = when {
                skycon == "THUNDER_SHOWER" -> 0.82
                skycon == "SHOWER_RAIN" -> 0.66
                skycon == "LIGHT_RAIN" -> 0.48
                skycon == "CLOUDY" -> 0.24
                else -> 0.12
            }
            val uv = 11 - (if (cloudrateOf(skycon) > 70) 2 else 0)

            // 逐日天文：当天真实的太阳/月亮时刻（赤道）
            val dayStart = dayCalendar.timeInMillis
            val daySolar = SkyPosition.solarEvents(dayStart, MAP_CENTER_LAT, effectiveLongitude)
            val dayMoon = SkyPosition.moonEvents(dayStart, MAP_CENTER_LAT, effectiveLongitude)
            val dayMoonNow = SkyPosition.moon(dayStart, MAP_CENTER_LAT, effectiveLongitude)

            dailyAstro.add(
                DailyAstro(
                    date = dateStr,
                    sunrise = AstroTime(formatMinutes(daySolar?.sunriseMinutes)),
                    sunset = AstroTime(formatMinutes(daySolar?.sunsetMinutes)),
                    moonrise = AstroTime(formatMinutes(dayMoon?.moonriseMinutes)),
                    moonset = AstroTime(formatMinutes(dayMoon?.moonsetMinutes)),
                    moonPhase = phaseName(dayMoonNow.phaseFraction, dayMoonNow.waxing),
                    solarNoon = AstroTime(formatMinutes(daySolar?.solarNoonMinutes)),
                    solarMidnight = AstroTime(formatMinutes(daySolar?.solarMidnightMinutes)),
                    civilDawn = AstroTime(formatMinutes(daySolar?.civilDawnMinutes)),
                    civilDusk = AstroTime(formatMinutes(daySolar?.civilDuskMinutes)),
                    astronomicalDawn = AstroTime(formatMinutes(daySolar?.astronomicalDawnMinutes)),
                    astronomicalDusk = AstroTime(formatMinutes(daySolar?.astronomicalDuskMinutes)),
                    nauticalDawn = AstroTime(formatMinutes(daySolar?.nauticalDawnMinutes)),
                    nauticalDusk = AstroTime(formatMinutes(daySolar?.nauticalDuskMinutes)),
                    moonTransit = AstroTime(formatMinutes(dayMoon?.transitMinutes)),
                    moonUnderfoot = AstroTime(formatMinutes(dayMoon?.underfootMinutes))
                )
            )
            dailyTemps.add(
                DailyTemperature(date = dateStr, max = round1(maxTemp), min = round1(minTemp), avg = round1(avgTemp))
            )
            dailySkycons.add(DailySkycon(date = dateStr, value = skycon))
            dailyWinds.add(
                DailyWind(
                    date = dateStr,
                    max = Wind(
                        speed = round1(maxWind),
                        direction = 208.0 + (i % 5) * 4.0,
                        gust = round1(maxWind * 1.8),
                        scale = beaufort(maxWind)
                    ),
                    min = Wind(
                        speed = round1(minWind),
                        direction = 186.0 + (i % 5) * 3.0,
                        gust = round1(minWind * 1.6),
                        scale = beaufort(minWind)
                    ),
                    avg = Wind(
                        speed = round1(avgWind),
                        direction = 198.0 + (i % 5) * 3.5,
                        gust = round1(avgWind * 1.7),
                        scale = beaufort(avgWind)
                    )
                )
            )
            dailyPrecip.add(
                DailyPrecipitation(
                    date = dateStr,
                    max = round1(rainAmount),
                    min = 0.0,
                    avg = round1(rainAmount / 2.0),
                    probability = rainProb
                )
            )
            dailyLife.add(
                LifeIndexDay(date = dateStr, index = uv.toString(), desc = uvDesc(uv))
            )

            dayCalendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // ---- 汇总实况 ----
        val currentTemp = hourlyTemps.first().value ?: 27.0
        val currentHumidity = hourlyHumidity.first().value ?: 0.78
        val currentCloud = hourlyCloudrate.first().value ?: 0.58
        val currentSkycon = hourlySkycons.first().value ?: "PARTLY_CLOUDY_DAY"
        val currentWind = hourlyWinds.first()
        val currentRain = hourlyPrecip.first().value ?: 0.0

        // ---- 分钟级降水（未来 2 小时，逐 5 分钟累计，mm） ----
        // 与和风 /v7/minutely/5m 同构：24 个 5 分钟累计 + 一句 summary。
        //
        // **必须与逐时降水量自洽**：同一小时内的 12 个 5 分钟累计之和
        // = 该小时的降水量（mm）。否则图上强度会和逐小时预报对不上
        // （此前峰值按「每 5 分钟 1.6mm」硬编，逐时只有 2.9mm/h，差了近 6 倍）。
        // 形状取对流单体过境：小时内中间强、两端弱，并按小时总量归一化。
        val minutelyTimes = mutableListOf<String>()
        val minutelyPrecip = mutableListOf<Double>()
        val minutelyProb = mutableListOf<Double>()
        val minutelyCalendar = localCalendar.clone() as Calendar
        minutelyCalendar.set(Calendar.SECOND, 0)
        minutelyCalendar.set(Calendar.MILLISECOND, 0)
        val minutelyIso = SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX", Locale.CHINA)
        // 小时内 12 个 5 分钟槽位的强度形状
        val slotShape = List(12) { slot -> sin(PI * (slot + 0.5) / 12.0) }
        val slotShapeSum = slotShape.sum()
        repeat(24) { i ->
            minutelyTimes.add(minutelyIso.format(minutelyCalendar.time))
            val hourOffset = i / 12
            val slot = i % 12
            val h = (baseHour + hourOffset) % 24
            val hourRainMm = hourlyRain(h)
            val mm = if (hourRainMm > 0.0) {
                round2(hourRainMm * slotShape[slot] / slotShapeSum)
            } else {
                0.0
            }
            minutelyPrecip.add(mm)
            minutelyProb.add(if (mm > 0.0) 0.62 else 0.08)
            minutelyCalendar.add(Calendar.MINUTE, 5)
        }
        // summary 由**数据本身**推导，保证与柱子、逐时预报三者自洽。
        // 口径贴真实接口：和风 /v7/minutely/5m 的 summary 是**自然语言描述**
        // （官方示例「95分钟后雨就停了」），不是「有降水/无降水」这种标签。
        val minutelyHasRain = minutelyPrecip.any { it > 0.0 }
        val minutelySummary = if (!minutelyHasRain) {
            "未来两小时无降水"
        } else {
            val firstIdx = minutelyPrecip.indexOfFirst { it > 0.0 }
            val lastIdx = minutelyPrecip.indexOfLast { it > 0.0 }
            val startMinutes = firstIdx * 5
            val endMinutes = (lastIdx + 1) * 5
            val kind = if (currentSkycon.contains("SNOW")) "雪" else "雨"
            val startText = if (startMinutes <= 0) {
                "现在开始有$kind"
            } else {
                "${startMinutes}分钟后开始有$kind"
            }
            "$startText，${endMinutes}分钟后${kind}就停了"
        }

        val response = WeatherResponse(
            status = "ok",
            lang = "zh-CN",
            unit = "metric",
            timezone = "UTC+00:00",
            server_time = nowMillis / 1000L,
            location = listOf(MAP_CENTER_LAT, MAP_CENTER_LON),
            result = WeatherResult(
                realtime = RealtimeWeather(
                    status = "ok",
                    temperature = currentTemp,
                    apparent_temperature = hourlyApparent.first().value,
                    humidity = currentHumidity,
                    cloudrate = currentCloud,
                    skycon = currentSkycon,
                    visibility = hourlyVisibility.first().value,
                    dswrf = 486.0,
                    pressure = hourlyPressure.first().value,
                    wind = Wind(
                        speed = currentWind.speed,
                        direction = currentWind.direction,
                        gust = hourlyGusts.first().value,
                        scale = currentWind.scale
                    ),
                    precipitation = Precipitation(
                        local = PrecipitationLocal(status = "ok", datasource = "radar", intensity = currentRain),
                        nearest = PrecipitationNearest(status = "ok", distance = 0.0, intensity = currentRain)
                    ),
                    air_quality = AirQuality(
                        // 只给**分项浓度**（赤道海域气溶胶口径），不预置 AQI / IAQI / 等级文案：
                        // 这些派生量一律由 App 用 AqiCalculator / AqhiCalculator 自算，
                        // 与真实接口路径（QWeatherMapper 同样调 AqiCalculator）口径一致，
                        // 不会出现「浓度和 AQI 对不上」。
                        pm25 = hourlyPm25.first().value,
                        pm10 = round1(12.0 + currentCloud * 7.5),
                        o3 = round1(46.0 + currentCloud * 18.0),
                        so2 = round1(1.2 + currentCloud * 0.8),
                        no2 = round1(2.4 + currentCloud * 2.0),
                        co = round1(320.0 + currentCloud * 90.0),
                        pm25Unit = "μg/m³",
                        pm10Unit = "μg/m³",
                        o3Unit = "μg/m³",
                        so2Unit = "μg/m³",
                        no2Unit = "μg/m³",
                        coUnit = "μg/m³"
                    ),
                    life_index = LifeIndex(
                        ultraviolet = LifeIndexItem(index = "11", desc = "极强"),
                        comfort = LifeIndexItem(index = "4", desc = "闷热")
                    )
                ),
                minutely = MinutelyForecast(
                    status = "ok",
                    datasource = "radar",
                    precipitation_2h = minutelyPrecip,
                    precipitation = minutelyPrecip,
                    probability = minutelyProb,
                    description = minutelySummary,
                    times = minutelyTimes
                ),
                hourly = HourlyForecast(
                    status = "ok",
                    description = "赤道海域典型对流性天气：午后多雷阵雨，昼夜温差小、湿度高。",
                    precipitation = hourlyPrecip,
                    temperature = hourlyTemps,
                    apparent_temperature = hourlyApparent,
                    wind = hourlyWinds,
                    gust = hourlyGusts,
                    humidity = hourlyHumidity,
                    cloudrate = hourlyCloudrate,
                    skycon = hourlySkycons,
                    pressure = hourlyPressure,
                    visibility = hourlyVisibility,
                    dswrf = null,
                    // 逐时空气质量：与 /airquality/v1/hourly 同构，
                    // 只给分项浓度（原始观测量），逐时 AQI 由 App 自算
                    air_quality = HourlyAirQuality(
                        pm25 = hourlyPm25,
                        pm10 = hourlyPm10,
                        o3 = hourlyO3,
                        so2 = hourlySo2,
                        no2 = hourlyNo2,
                        co = hourlyCo,
                        aqi = null,
                        pm25Unit = "μg/m³",
                        pm10Unit = "μg/m³",
                        o3Unit = "μg/m³",
                        so2Unit = "μg/m³",
                        no2Unit = "μg/m³",
                        coUnit = "μg/m³"
                    ),
                    life_index = HourlyLifeIndex(ultraviolet = hourlyUv)
                ),
                daily = DailyForecast(
                    status = "ok",
                    astro = dailyAstro,
                    precipitation = dailyPrecip,
                    temperature = dailyTemps,
                    wind = dailyWinds,
                    skycon = dailySkycons,
                    life_index = DailyLifeIndex(ultraviolet = dailyLife)
                ),
                // 与真实接口同口径：forecast_keypoint 取自分钟级降水的 summary，
                // 不再塞入与真实数据无关的「气候说明面板」文案。
                forecastKeypoint = minutelySummary,
                alert = Alert(status = "ok", content = emptyList()),
                astronomy = AstronomySnapshot(
                    latitude = MAP_CENTER_LAT,
                    longitude = MAP_CENTER_LON,
                    solarElevationDegrees = SkyPosition.sun(nowMillis, MAP_CENTER_LAT, MAP_CENTER_LON).elevationDegrees,
                    solarAzimuthDegrees = SkyPosition.sun(nowMillis, MAP_CENTER_LAT, MAP_CENTER_LON).azimuthDegrees,
                    moonIllumination = round1(moonNow.illuminatedFraction),
                    moonPhaseName = moonPhaseName,
                    observedAtMillis = nowMillis
                )
            )
        )

        // 示例数据与真实接口同口径：开关打开时会输出完整性校验结果
        WeatherDataValidator.logIfEnabled("mock", response)
        return response
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    /** 赤道海域的逐时天气型（午后为对流雷阵雨高峰）。 */
    private fun hourlySkycon(hour: Int): String = when (hour) {
        in 0..4 -> "CLEAR_NIGHT"
        5 -> "PARTLY_CLOUDY_NIGHT"
        in 6..10 -> "PARTLY_CLOUDY_DAY"
        11, 12 -> "CLOUDY"
        13, 19 -> "SHOWER_RAIN"
        14, 18 -> "LIGHT_RAIN"
        in 15..17 -> "THUNDER_SHOWER"
        in 20..22 -> "PARTLY_CLOUDY_NIGHT"
        else -> "CLEAR_NIGHT"
    }

    /** 逐时降水量（mm）。 */
    private fun hourlyRain(hour: Int): Double = when (hour) {
        13 -> 1.8
        14 -> 3.4
        15 -> 4.2
        16 -> 2.9
        17 -> 1.2
        18 -> 0.6
        19 -> 0.2
        else -> 0.0
    }

    /** 逐时紫外线指数（赤道上空臭氧层薄，紫外线全年极强）。 */
    private fun uvIndex(hour: Int): Int = when (hour) {
        in 6..8 -> 6
        9 -> 8
        10 -> 10
        11, 12 -> 11
        13 -> 10
        14, 15 -> 9
        16 -> 7
        17 -> 5
        18 -> 3
        19 -> 1
        else -> 0
    }

    private fun uvDesc(index: Int): String = when {
        index <= 2 -> "弱"
        index <= 5 -> "中等"
        index <= 7 -> "强"
        index <= 10 -> "很强"
        else -> "极强"
    }

    private fun cloudrateOf(skycon: String): Double = when {
        skycon.contains("THUNDER") -> 84.0
        skycon.contains("RAIN") -> 76.0
        skycon == "CLOUDY" -> 68.0
        skycon.contains("PARTLY") -> 52.0
        else -> 22.0
    }

    /** 蒲福风级（输入 km/h）。 */
    private fun beaufort(speedKmh: Double): Int = when {
        speedKmh >= 118 -> 12
        speedKmh >= 103 -> 11
        speedKmh >= 89 -> 10
        speedKmh >= 74 -> 9
        speedKmh >= 62 -> 8
        speedKmh >= 51 -> 7
        speedKmh >= 39 -> 6
        speedKmh >= 29 -> 5
        speedKmh >= 20 -> 4
        speedKmh >= 12 -> 3
        speedKmh >= 6 -> 2
        speedKmh >= 1 -> 1
        else -> 0
    }

    /** 月相名称（按八分月相，0 = 新月 → 1 = 满月）。 */
    private fun phaseName(phaseFraction: Double, waxing: Boolean): String = when {
        phaseFraction < 0.125 -> "新月"
        phaseFraction < 0.375 -> if (waxing) "峨眉月" else "残月"
        phaseFraction < 0.625 -> if (waxing) "上弦月" else "下弦月"
        phaseFraction < 0.875 -> if (waxing) "盈凸月" else "亏凸月"
        else -> "满月"
    }

    /** 分钟数 → "HH:mm"。 */
    private fun formatMinutes(minutes: Int?): String {
        if (minutes == null) return "--:--"
        val wrapped = ((minutes % 1440) + 1440) % 1440
        return String.format(Locale.US, "%02d:%02d", wrapped / 60, wrapped % 60)
    }

    private fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0

    private fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0
}
