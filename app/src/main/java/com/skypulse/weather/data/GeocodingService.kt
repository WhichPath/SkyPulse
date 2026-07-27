package com.skypulse.weather.data

import com.skypulse.weather.data.remote.qweather.QWeatherApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CityEntry(
    val name: String,
    val province: String,
    val lat: Double,
    val lon: Double
)

/**
 * 城市搜索服务。
 *
 * 使用和风天气 GeoAPI (/geo/v2/city/lookup) 进行城市模糊搜索。
 * 替换了原有的小米天气城市搜索接口。
 */
class GeocodingService @Inject constructor(
    private val api: QWeatherApi
) {

    suspend fun search(query: String): List<CityEntry> {
        if (query.isBlank()) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val response = api.searchCity(query)
                response.location?.mapNotNull { item ->
                    try {
                        val name = item.name ?: return@mapNotNull null
                        val lat = item.lat?.toDoubleOrNull() ?: return@mapNotNull null
                        val lon = item.lon?.toDoubleOrNull() ?: return@mapNotNull null
                        val province = item.adm1 ?: item.adm2 ?: ""

                        CityEntry(name = name, province = province, lat = lat, lon = lon)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
