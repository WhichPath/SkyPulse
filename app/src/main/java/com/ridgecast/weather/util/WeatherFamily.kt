package com.ridgecast.weather.util

/**
 * 天气族（Weather Family）——全项目唯一的天气归类口径。
 *
 * 背景渐变、玻璃卡片配色、粒子特效、城市卡片色、逐时图表色都要回答同一个问题：
 * 「当前是哪一类天气」。过去每一处各写一条 `contains` 关键词链，词表不重叠，
 * 于是同一场天气在不同层的表现会打架。
 *
 * 历史事故（背景色错配）：`THUNDER_SHOWER`（雷阵雨）、`SLEET`（雨夹雪）、
 * `HAIL`（冰雹）这三个 skycon 不含任何被旧关键词链命中的词
 * （不含 RAIN / STORM / SNOW / CLOUDY / CLEAR / HAZE / WIND），
 * 于是在渐变层全部落到 `else -> 晴天蓝`，
 * 表现为「打着闪电、下着雨夹雪，背景却是万里晴空」；
 * 同时 `THUNDER_SHOWER` 在玻璃配色层被判为「非降水」而套用了浅色玻璃。
 *
 * 现在统一由 [classifyWeather] 归类，新增 skycon 只需改这一处。
 */
enum class WeatherFamily {
    /** 晴：CLEAR_DAY / CLEAR_NIGHT / CLEAR */
    Clear,

    /** 多云：PARTLY_CLOUDY_DAY / PARTLY_CLOUDY_NIGHT */
    PartlyCloudy,

    /** 阴：CLOUDY */
    Cloudy,

    /** 雷暴：THUNDER_SHOWER */
    Thunder,

    /** 雨：LIGHT_RAIN / MODERATE_RAIN / HEAVY_RAIN / STORM_RAIN / SHOWER_RAIN */
    Rain,

    /** 雪：LIGHT_SNOW / MODERATE_SNOW / HEAVY_SNOW / STORM_SNOW / SHOWER_SNOW */
    Snow,

    /** 雨夹雪：SLEET */
    Sleet,

    /** 冰雹：HAIL */
    Hail,

    /** 霾 / 雾 / 浮尘 / 沙尘：*_HAZE / FOG / DUST / SAND */
    Haze,

    /** 大风：WIND */
    Wind,

    /** 未知或尚未加载：skycon 为 null、空串，或和风词表之外的值 */
    Unknown;

    /**
     * 是否为降水类天气。
     *
     * 决定玻璃卡片走「深色玻璃」（雨雪/雷暴向黑色压暗）还是「浅色玻璃」
     * （白天非降水向白色提亮），见 [WeatherUtils.getWeatherTheme]。
     */
    val isPrecipitating: Boolean
        get() = this == Thunder || this == Rain || this == Snow ||
                this == Sleet || this == Hail

    /**
     * 是否为强对流天气（雷暴）。
     *
     * 强对流天气的天空比普通降水更暗更沉，背景渐变需要单独一套配色。
     */
    val isConvective: Boolean
        get() = this == Thunder
}

/**
 * 把和风 skycon 字符串归类到 [WeatherFamily]。
 *
 * 判定顺序即优先级，**先特指后泛指**，顺序本身是正确性的一部分：
 * 1. `THUNDER` 必须最先判定——「THUNDER_SHOWER」既含雷暴也含阵雨语义，
 *    但它首先是雷暴；
 * 2. `SLEET` / `HAIL` 是独立词，既不含 RAIN 也不含 SNOW；
 * 3. `PARTLY_CLOUDY` 必须先于 `CLOUDY`——「PARTLY_CLOUDY」含子串「CLOUDY」；
 * 4. `SNOW` / `RAIN` 先于裸 `STORM`，这样 `STORM_SNOW` 归雪、`STORM_RAIN` 归雨，
 *    剩下的裸「STORM」才归雨。
 */
fun classifyWeather(skycon: String?): WeatherFamily {
    if (skycon.isNullOrBlank()) return WeatherFamily.Unknown
    return when {
        skycon.contains("THUNDER") -> WeatherFamily.Thunder
        skycon.contains("SLEET") -> WeatherFamily.Sleet
        skycon.contains("HAIL") -> WeatherFamily.Hail
        skycon.contains("PARTLY_CLOUDY") -> WeatherFamily.PartlyCloudy
        skycon.contains("CLOUDY") -> WeatherFamily.Cloudy
        skycon.contains("CLEAR") -> WeatherFamily.Clear
        skycon.contains("SNOW") -> WeatherFamily.Snow
        skycon.contains("RAIN") -> WeatherFamily.Rain
        skycon.contains("STORM") -> WeatherFamily.Rain
        skycon.contains("HAZE") -> WeatherFamily.Haze
        skycon == "FOG" || skycon == "DUST" || skycon == "SAND" -> WeatherFamily.Haze
        skycon == "WIND" -> WeatherFamily.Wind
        else -> WeatherFamily.Unknown
    }
}
