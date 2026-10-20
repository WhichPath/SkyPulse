package com.ridgecast.weather.ui.glass

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 天体位置计算（纯本地、零网络），为玻璃高光提供"光源在哪、有多亮"的物理依据。
 *
 * - **太阳**：NOAA Solar Position 算法（Michalsky 1988 的改进版），
 *   精度约 ±0.01°，可直接给出高度角/方位角。
 * - **月亮**：Meeus《Astronomical Algorithms》第 47 章的简化月历位置，
 *   精度约 ±0.3°（视位置），并可由日月黄经差求出**被照亮比例**。
 *
 * 两者都返回 [SkyPosition]，再由 [GlassLighting] 投影到屏幕空间。
 */
object SkyPosition {

    /** 天体在观察者地平坐标系中的位置。 */
    data class Position(
        /** 高度角（度），地平线为 0，向天顶为正。 */
        val elevationDegrees: Double,
        /** 方位角（度），0 = 正北，90 = 正东，顺时针。 */
        val azimuthDegrees: Double,
        /** 被照亮比例 0..1（月亮有效，太阳恒为 1）。 */
        val illuminatedFraction: Double = 1.0,
        /** 月相进度 0..1：0 = 新月，0.5 = 上下弦，1 = 满月（太阳恒为 0.5）。 */
        val phaseFraction: Double = 0.5,
        /** 月亮是否在盈（waxing）。 */
        val waxing: Boolean = false
    )

    private const val J2000_UNIX_EPOCH = 2440587.5
    private const val J2000_JD = 2451545.0

    private fun julianDay(utcMillis: Long): Double =
        utcMillis / 86_400_000.0 + J2000_UNIX_EPOCH

    private fun julianCentury(jd: Double): Double = (jd - J2000_JD) / 36525.0

    private fun norm360(v: Double): Double {
        val r = v % 360.0
        return if (r < 0) r + 360.0 else r
    }

    /** 归一到 -180..180。 */
    private fun norm180(v: Double): Double {
        var r = v % 360.0
        if (r > 180.0) r -= 360.0
        if (r < -180.0) r += 360.0
        return r
    }

    // ------------------------------------------------------------------
    // 太阳
    // ------------------------------------------------------------------

    /**
     * 太阳位置（NOAA 算法）。
     *
     * @param utcMillis 当前 UTC 毫秒
     */
    fun sun(utcMillis: Long, latitude: Double, longitude: Double): Position {
        val t = solarTerms(utcMillis, latitude, longitude)
        return Position(t.elevation, t.azimuth, 1.0)
    }

    /** 太阳位置的中间量。 */
    private data class SolarTerms(
        val declination: Double,
        val equationOfTime: Double,
        val hourAngle: Double,
        val elevation: Double,
        val azimuth: Double
    )

    private fun solarTerms(utcMillis: Long, latitude: Double, longitude: Double): SolarTerms {
        val jd = julianDay(utcMillis)
        val t = julianCentury(jd)

        val meanLongitude = norm360(280.46646 + t * (36000.76983 + t * 0.0003032))
        val meanAnomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val mRad = Math.toRadians(meanAnomaly)
        val center = sin(mRad) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * mRad) * (0.019993 - 0.000101 * t) +
            sin(3 * mRad) * 0.000289
        val trueLongitude = meanLongitude + center
        val omega = 125.04 - 1934.136 * t
        val apparentLongitude = trueLongitude - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
        val meanObliquity = 23.0 +
            (26.0 + ((21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0)) / 60.0
        val obliquity = meanObliquity + 0.00256 * cos(Math.toRadians(omega))

        val declination = Math.toDegrees(
            asin(sin(Math.toRadians(obliquity)) * sin(Math.toRadians(apparentLongitude)))
        )
        val y = tan(Math.toRadians(obliquity / 2.0)).let { it * it }
        val lRad = Math.toRadians(meanLongitude)
        val equationOfTime = 4.0 * (
            y * sin(2 * lRad) -
                2 * eccentricity * sin(mRad) +
                4 * eccentricity * y * sin(mRad) * cos(2 * lRad) -
                0.5 * y * y * sin(4 * lRad) -
                1.25 * eccentricity * eccentricity * sin(2 * mRad)
            )

        val minutesUtc = (utcMillis / 60000.0 % 1440.0 + 1440.0) % 1440.0
        // 真太阳时以"当日分钟数"表示（0..1440），再折算成时角
        val trueSolarTime = (minutesUtc + equationOfTime + 4.0 * longitude) % 1440.0
        val hourAngle = (if (trueSolarTime < 0) trueSolarTime + 1440.0 else trueSolarTime) / 4.0 - 180.0

        val latRad = Math.toRadians(latitude)
        val decRad = Math.toRadians(declination)
        val haRad = Math.toRadians(hourAngle)
        val cosZ = sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(haRad)
        val zenith = Math.toDegrees(acos(cosZ.coerceIn(-1.0, 1.0)))
        val elevation = 90.0 - zenith

        val cosAz = (sin(decRad) - sin(latRad) * sin(Math.toRadians(elevation))) /
            (cos(latRad) * cos(Math.toRadians(elevation)))
        var azimuth = Math.toDegrees(acos(cosAz.coerceIn(-1.0, 1.0)))
        if (hourAngle > 0) azimuth = 360.0 - azimuth
        azimuth = norm360(azimuth)

        return SolarTerms(declination, equationOfTime, hourAngle, elevation, azimuth)
    }

    /**
     * 当日日出/日落的太阳方位角（太阳高度 -0.833°，含大气折射与日面半径修正）。
     *
     * 返回 null 表示极昼/极夜（该纬度当天看不到日出或日落）。
     */
    fun sunriseSunsetAzimuth(
        utcMillis: Long,
        latitude: Double,
        longitude: Double
    ): Pair<Double, Double>? {
        // 以当天 UTC 正午的赤纬近似全天赤纬（赤纬日变化 < 0.4°，足够）
        val noonMillis = utcMillis / 86_400_000L * 86_400_000L + 12L * 3600_000L
        val declination = solarTerms(noonMillis, latitude, longitude).declination
        val decRad = Math.toRadians(declination)
        val latRad = Math.toRadians(latitude)
        val cosH = (sin(Math.toRadians(-0.833)) - sin(latRad) * sin(decRad)) /
            (cos(latRad) * cos(decRad))
        if (abs(cosH) > 1.0) return null
        val hourAngle = Math.toDegrees(acos(cosH))
        val rise = azimuthAtHourAngle(declination, -hourAngle, latitude)
        val set = azimuthAtHourAngle(declination, hourAngle, latitude)
        return rise to set
    }

    /** 给定赤纬与时角，返回方位角。 */
    private fun azimuthAtHourAngle(declination: Double, hourAngle: Double, latitude: Double): Double {
        val latRad = Math.toRadians(latitude)
        val decRad = Math.toRadians(declination)
        val haRad = Math.toRadians(hourAngle)
        val cosZ = sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(haRad)
        val elevation = 90.0 - Math.toDegrees(acos(cosZ.coerceIn(-1.0, 1.0)))
        val cosAz = (sin(decRad) - sin(latRad) * sin(Math.toRadians(elevation))) /
            (cos(latRad) * cos(Math.toRadians(elevation)))
        var azimuth = Math.toDegrees(acos(cosAz.coerceIn(-1.0, 1.0)))
        if (hourAngle > 0) azimuth = 360.0 - azimuth
        return norm360(azimuth)
    }

    // ------------------------------------------------------------------
    // 太阳升落时刻
    // ------------------------------------------------------------------

    /**
     * 太阳的升落与各晨昏时刻（UTC 当日分钟数）。
     *
     * 高度角基准：日出日落 -0.833°（含蒙气差与日面半径）、
     * 民用晨昏 -6°、航海晨昏 -12°、天文晨昏 -18°。
     */
    data class SolarEvents(
        val sunriseMinutes: Int,
        val sunsetMinutes: Int,
        val civilDawnMinutes: Int,
        val civilDuskMinutes: Int,
        val nauticalDawnMinutes: Int,
        val nauticalDuskMinutes: Int,
        val astronomicalDawnMinutes: Int,
        val astronomicalDuskMinutes: Int,
        val solarNoonMinutes: Int,
        val solarMidnightMinutes: Int
    )

    /**
     * 计算当天（UTC）的太阳升落时刻。极昼/极夜导致无法求解时返回 null。
     */
    fun solarEvents(utcMillis: Long, latitude: Double, longitude: Double): SolarEvents? {
        val dayStart = utcMillis / 86_400_000L * 86_400_000L
        val terms = solarTerms(dayStart + 12L * 3600_000L, latitude, longitude)
        val declination = terms.declination
        val solarNoon = 720.0 - terms.equationOfTime - 4.0 * longitude

        fun riseSetFor(h0: Double): Pair<Int, Int>? {
            val latRad = Math.toRadians(latitude)
            val decRad = Math.toRadians(declination)
            val cosH = (sin(Math.toRadians(h0)) - sin(latRad) * sin(decRad)) /
                (cos(latRad) * cos(decRad))
            if (abs(cosH) > 1.0) return null
            val halfDay = Math.toDegrees(acos(cosH)) * 4.0
            return Pair(
                Math.round(solarNoon - halfDay).toInt(),
                Math.round(solarNoon + halfDay).toInt()
            )
        }

        val riseSet = riseSetFor(-0.833) ?: return null
        val civil = riseSetFor(-6.0) ?: riseSet
        val nautical = riseSetFor(-12.0) ?: riseSet
        val astronomical = riseSetFor(-18.0) ?: riseSet

        return SolarEvents(
            sunriseMinutes = riseSet.first,
            sunsetMinutes = riseSet.second,
            civilDawnMinutes = civil.first,
            civilDuskMinutes = civil.second,
            nauticalDawnMinutes = nautical.first,
            nauticalDuskMinutes = nautical.second,
            astronomicalDawnMinutes = astronomical.first,
            astronomicalDuskMinutes = astronomical.second,
            solarNoonMinutes = Math.round(solarNoon).toInt(),
            solarMidnightMinutes = Math.round(solarNoon + 720.0).toInt()
        )
    }

    // ------------------------------------------------------------------
    // 月亮升落时刻
    // ------------------------------------------------------------------

    /**
     * 月亮的月升月落与中天（UTC 当日分钟数）。
     */
    data class MoonEvents(
        val moonriseMinutes: Int,
        val moonsetMinutes: Int,
        val transitMinutes: Int,
        val underfootMinutes: Int
    )

    /**
     * 计算当天（UTC）的月亮升落时刻。
     *
     * 采用 Meeus 的简化月历：先解出月亮赤经/赤纬，再用时角求升落。
     */
    fun moonEvents(utcMillis: Long, latitude: Double, longitude: Double): MoonEvents? {
        val dayStart = utcMillis / 86_400_000L * 86_400_000L
        val noonMillis = dayStart + 12L * 3600_000L
        val jd = julianDay(noonMillis)
        val t = julianCentury(jd)
        val d = jd - J2000_JD

        val lp = norm360(218.316 + 13.176396 * d)
        val m = norm360(134.963 + 13.064993 * d)
        val mp = norm360(357.529 + 0.98560028 * d)
        val f = norm360(93.272 + 13.229350 * d)
        val dRad = norm360(297.8501921 + 445267.1114034 * t - 0.0018819 * t * t)
        val mRad = Math.toRadians(m)
        val fRad = Math.toRadians(f)
        val dRadR = Math.toRadians(dRad)
        val lRad = Math.toRadians(lp)

        val lambda = lRad + Math.toRadians(
            6.289 * sin(mRad) +
                1.274 * sin(2 * dRadR - mRad) +
                0.658 * sin(2 * dRadR) +
                0.214 * sin(2 * mRad) -
                0.186 * sin(Math.toRadians(mp)) -
                0.114 * sin(2 * fRad)
        )
        val beta = Math.toRadians(
            5.128 * sin(fRad) +
                0.281 * sin(mRad + fRad) -
                0.278 * sin(fRad - mRad) -
                0.173 * sin(2 * dRadR - fRad)
        )
        val meanObliquity = 23.0 +
            (26.0 + ((21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0)) / 60.0
        val obliquity = meanObliquity + 0.00256 * cos(Math.toRadians(125.04 - 1934.136 * t))
        val epsRad = Math.toRadians(obliquity)
        val declination = asin(sin(epsRad) * sin(beta) + cos(epsRad) * sin(beta) * cos(lambda))
        val rightAscension = Math.toDegrees(
            atan2(
                sin(lambda) * cos(epsRad) - tan(beta) * sin(epsRad),
                cos(lambda)
            )
        )

        // 格林尼治平恒星时（当日 0h 起算）
        val gmst0 = norm360(
            280.46061837 + 360.98564736629 * d + 0.000387933 * t * t - t * t * t / 38710000.0
        )
        // 中天时刻：GMST(t) + lon = α
        val transit = norm360(rightAscension - longitude - gmst0) / 0.98564736629

        val latRad = Math.toRadians(latitude)
        val cosH = (sin(Math.toRadians(0.125)) - sin(latRad) * sin(declination)) /
            (cos(latRad) * cos(declination))
        if (abs(cosH) > 1.0) return null
        val halfDay = Math.toDegrees(acos(cosH)) * 4.0

        return MoonEvents(
            moonriseMinutes = Math.round(transit - halfDay).toInt(),
            moonsetMinutes = Math.round(transit + halfDay).toInt(),
            transitMinutes = Math.round(transit).toInt(),
            underfootMinutes = Math.round(transit + 720.0).toInt()
        )
    }

    // ------------------------------------------------------------------
    // 月亮
    // ------------------------------------------------------------------

    /**
     * 月亮位置与被照亮比例（Meeus 简化月历）。
     *
     * 位置精度约 ±0.3°；被照亮比例由日月黄经差与赤纬差求出的月相角得到。
     */
    fun moon(utcMillis: Long, latitude: Double, longitude: Double): Position {
        val jd = julianDay(utcMillis)
        val t = julianCentury(jd)
        val d = jd - J2000_JD

        // 太阳视黄经（用于月相角）
        val sunMeanLongitude = norm360(280.46646 + t * (36000.76983 + t * 0.0003032))
        val sunMeanAnomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val sunM = Math.toRadians(sunMeanAnomaly)
        val sunCenter = sin(sunM) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * sunM) * (0.019993 - 0.000101 * t) +
            sin(3 * sunM) * 0.000289
        val sunTrueLongitude = sunMeanLongitude + sunCenter
        val omega = 125.04 - 1934.136 * t
        val sunApparentLongitude = sunTrueLongitude - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
        val meanObliquity = 23.0 +
            (26.0 + ((21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0)) / 60.0
        val obliquity = meanObliquity + 0.00256 * cos(Math.toRadians(omega))
        val epsRad = Math.toRadians(obliquity)
        val sunLambda = Math.toRadians(sunApparentLongitude)
        val sunDeclination = asin(sin(epsRad) * sin(sunLambda))

        // 月亮黄经/黄纬（主要周期项）
        val lp = norm360(218.316 + 13.176396 * d)
        val m = norm360(134.963 + 13.064993 * d)
        val mp = norm360(357.529 + 0.98560028 * d)
        val f = norm360(93.272 + 13.229350 * d)
        val dRad = norm360(297.8501921 + 445267.1114034 * t - 0.0018819 * t * t) // 平距角
        val mRad = Math.toRadians(m)
        val mpRad = Math.toRadians(mp)
        val fRad = Math.toRadians(f)
        val dRadR = Math.toRadians(dRad)
        val lRad = Math.toRadians(lp)

        val lambda = lRad + Math.toRadians(
            6.289 * sin(mRad) +
                1.274 * sin(2 * dRadR - mRad) +
                0.658 * sin(2 * dRadR) +
                0.214 * sin(2 * mRad) -
                0.186 * sin(mpRad) -
                0.114 * sin(2 * fRad)
        )
        val beta = Math.toRadians(
            5.128 * sin(fRad) +
                0.281 * sin(mRad + fRad) -
                0.278 * sin(fRad - mRad) -
                0.173 * sin(2 * dRadR - fRad)
        )

        val declination = asin(sin(epsRad) * sin(beta) + cos(epsRad) * sin(beta) * cos(lambda))
        val rightAscension = atan2(
            sin(lambda) * cos(epsRad) - tan(beta) * sin(epsRad),
            cos(lambda)
        )

        // 格林尼治平恒星时 → 地方时角
        val gmst = norm360(
            280.46061837 + 360.98564736629 * d + 0.000387933 * t * t - t * t * t / 38710000.0
        )
        val localSidereal = norm360(gmst + longitude)
        val hourAngle = norm180(localSidereal - Math.toDegrees(rightAscension))

        val latRad = Math.toRadians(latitude)
        val haRad = Math.toRadians(hourAngle)
        val sinElevation = sin(latRad) * sin(declination) +
            cos(latRad) * cos(declination) * cos(haRad)
        val elevation = Math.toDegrees(asin(sinElevation.coerceIn(-1.0, 1.0)))
        val azimuth = norm360(
            Math.toDegrees(
                atan2(
                    sin(haRad),
                    cos(haRad) * sin(latRad) - tan(declination) * cos(latRad)
                )
            ) + 180.0
        )

        // 月相角 → 被照亮比例（Meeus 48.3/48.4）
        val psi = acos((cos(beta) * cos(lambda - sunLambda)).coerceIn(-1.0, 1.0))
        val sunDistanceEarthRadii = 1.000001018
        val phaseAngle = atan2(
            sunDistanceEarthRadii * sin(psi),
            declination - sunDeclination
        )
        val illuminated = ((1.0 + cos(phaseAngle)) / 2.0).coerceIn(0.0, 1.0)
        // 月相进度：0 = 新月，0.5 = 上下弦，1 = 满月
        val phaseFraction = (psi / Math.PI).coerceIn(0.0, 1.0)
        val elongation = norm360(Math.toDegrees(lambda - sunLambda))
        val waxing = elongation < 180.0

        return Position(
            elevationDegrees = elevation,
            azimuthDegrees = azimuth,
            illuminatedFraction = illuminated,
            phaseFraction = phaseFraction,
            waxing = waxing
        )
    }
}
